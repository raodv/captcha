/*
 *Copyright © 2018 anji-plus
 *安吉加加信息技术有限公司
 *http://www.anji-plus.com
 *All rights reserved.
 */
package com.anji.captcha.service.impl;

import com.anji.captcha.model.common.CaptchaTypeEnum;
import com.anji.captcha.model.common.RepCodeEnum;
import com.anji.captcha.model.common.ResponseModel;
import com.anji.captcha.model.vo.CaptchaVO;
import com.anji.captcha.model.vo.PointVO;
import com.anji.captcha.util.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Objects;
import java.util.Properties;
import java.util.Random;

/**
 * 滑动验证码
 * <p>
 * Created by raodeming on 2019/12/25.
 */
public class BlockPuzzleCaptchaServiceImpl extends AbstractCaptchaService {

    @Override
    public void init(Properties config) {
        super.init(config);
    }

	@Override
	public void destroy(Properties config) {
        logger.info("start-clear-history-data:{}",captchaType());
	}

    @Override
    public String captchaType() {
        return CaptchaTypeEnum.BLOCKPUZZLE.getCodeValue();
    }

    @Override
    public ResponseModel get(CaptchaVO captchaVO) {
		ResponseModel r = super.get(captchaVO);
		if(!validatedReq(r)){
			return r;
		}
        //原生图片
        BufferedImage originalImage = ImageUtils.getOriginal();
        if (null == originalImage) {
            logger.error("滑动底图未初始化成功，请检查路径");
            return ResponseModel.errorMsg(RepCodeEnum.API_CAPTCHA_BASEMAP_NULL);
        }
        //设置水印
        Graphics backgroundGraphics = originalImage.getGraphics();
        int width = originalImage.getWidth();
        int height = originalImage.getHeight();
        backgroundGraphics.setFont(waterMarkFont);
        backgroundGraphics.setColor(Color.white);
        backgroundGraphics.drawString(waterMark, width - getEnOrChLength(waterMark), height - (HAN_ZI_SIZE / 2) + 7);

        //抠图图片
        String jigsawImageBase64 = ImageUtils.getslidingBlock();
        BufferedImage jigsawImage = ImageUtils.getBase64StrToImage(jigsawImageBase64);
        if (null == jigsawImage) {
            logger.error("滑动底图未初始化成功，请检查路径");
            return ResponseModel.errorMsg(RepCodeEnum.API_CAPTCHA_BASEMAP_NULL);
        }
        CaptchaVO captcha = pictureTemplatesCut(originalImage, jigsawImage, jigsawImageBase64);
        if (captcha == null
                || StringUtils.isBlank(captcha.getJigsawImageBase64())
                || StringUtils.isBlank(captcha.getOriginalImageBase64())) {
            return ResponseModel.errorMsg(RepCodeEnum.API_CAPTCHA_ERROR);
        }
        return ResponseModel.successData(captcha);
    }

    @Override
    public ResponseModel check(CaptchaVO captchaVO) {
		ResponseModel r = super.check(captchaVO);
		if(!validatedReq(r)){
			return r;
		}
        //取坐标信息
        String codeKey = String.format(REDIS_CAPTCHA_KEY, captchaVO.getToken());
        if (!CaptchaServiceFactory.getCache(cacheType).exists(codeKey)) {
            return ResponseModel.errorMsg(RepCodeEnum.API_CAPTCHA_INVALID);
        }
        String s = CaptchaServiceFactory.getCache(cacheType).get(codeKey);
        //验证码只用一次，即刻失效
        CaptchaServiceFactory.getCache(cacheType).delete(codeKey);
        PointVO point = null;
        PointVO point1 = null;
        String pointJson = null;
        try {
            point = JsonUtil.parseObject(s, PointVO.class);
            //aes解密
            pointJson = decrypt(captchaVO.getPointJson(), point.getSecretKey());
            point1 = JsonUtil.parseObject(pointJson, PointVO.class);
        } catch (Exception e) {
            logger.error("验证码坐标解析失败", e);
            afterValidateFail(captchaVO);
            return ResponseModel.errorMsg(e.getMessage());
        }
        if (point.x - Integer.parseInt(slipOffset) > point1.x
                || point1.x > point.x + Integer.parseInt(slipOffset)
                || point.y != point1.y) {
            afterValidateFail(captchaVO);
            return ResponseModel.errorMsg(RepCodeEnum.API_CAPTCHA_COORDINATE_ERROR);
        }
        //校验成功，将信息存入缓存
        String secretKey = point.getSecretKey();
        String value = null;
        try {
            value = AESUtil.aesEncrypt(captchaVO.getToken().concat("---").concat(pointJson), secretKey);
        } catch (Exception e) {
            logger.error("AES加密失败", e);
            afterValidateFail(captchaVO);
            return ResponseModel.errorMsg(e.getMessage());
        }
        String secondKey = String.format(REDIS_SECOND_CAPTCHA_KEY, value);
        CaptchaServiceFactory.getCache(cacheType).set(secondKey, captchaVO.getToken(), EXPIRESIN_THREE);
        captchaVO.setResult(true);
        captchaVO.resetClientFlag();
        return ResponseModel.successData(captchaVO);
    }

    @Override
    public ResponseModel verification(CaptchaVO captchaVO) {
		ResponseModel r = super.verification(captchaVO);
		if(!validatedReq(r)){
			return r;
		}
        try {
            String codeKey = String.format(REDIS_SECOND_CAPTCHA_KEY, captchaVO.getCaptchaVerification());
            if (!CaptchaServiceFactory.getCache(cacheType).exists(codeKey)) {
                return ResponseModel.errorMsg(RepCodeEnum.API_CAPTCHA_INVALID);
            }
            //二次校验取值后，即刻失效
            CaptchaServiceFactory.getCache(cacheType).delete(codeKey);
        } catch (Exception e) {
            logger.error("验证码坐标解析失败", e);
            return ResponseModel.errorMsg(e.getMessage());
        }
        return ResponseModel.success();
    }

    /**
     * 根据模板切图
     *
     * @throws Exception
     */
    public CaptchaVO pictureTemplatesCut(BufferedImage originalImage, BufferedImage jigsawImage, String jigsawImageBase64) {
        try {
            CaptchaVO dataVO = new CaptchaVO();

            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();
            int jigsawWidth = jigsawImage.getWidth();
            int jigsawHeight = jigsawImage.getHeight();

            //随机生成拼图坐标
            PointVO point = generateJigsawPoint(originalWidth, originalHeight, jigsawWidth, jigsawHeight);
            int x = point.getX();
            int y = point.getY();

            //生成新的拼图图像
            BufferedImage newJigsawImage = new BufferedImage(jigsawWidth, jigsawHeight, jigsawImage.getType());
            Graphics2D graphics = newJigsawImage.createGraphics();

            int bold = 5;
            //如果需要生成RGB格式，需要做如下配置,Transparency 设置透明
            newJigsawImage = graphics.getDeviceConfiguration().createCompatibleImage(jigsawWidth, jigsawHeight, Transparency.TRANSLUCENT);
            // 新建的图像根据模板颜色赋值,源图生成遮罩
            cutByTemplate(originalImage, jigsawImage, newJigsawImage, x, 0);
            if (captchaInterferenceOptions > 0) {
                int position = 0;
                if (originalWidth - x - 10 > jigsawWidth * 2) {
                    //在原扣图右边插入干扰图
                    position = RandomUtils.getRandomInt(x + jigsawWidth + 5, originalWidth - jigsawWidth);
                } else {
                    //在原扣图左边插入干扰图
                    position = RandomUtils.getRandomInt(200, x - jigsawWidth - 10);
                }
                while (true) {
                    String s = ImageUtils.getslidingBlock();
                    if (!jigsawImageBase64.equals(s)) {
                        interferenceByTemplate(originalImage, Objects.requireNonNull(ImageUtils.getBase64StrToImage(s)), position, 0);
                        break;
                    }
                }
            }
            if (captchaInterferenceOptions > 1) {
                while (true) {
                    String s = ImageUtils.getslidingBlock();
                    if (!jigsawImageBase64.equals(s)) {
                        Integer randomInt = RandomUtils.getRandomInt(jigsawWidth, 200 - jigsawWidth);
                        interferenceByTemplate(originalImage, Objects.requireNonNull(ImageUtils.getBase64StrToImage(s)),
                                randomInt, 0);
                        break;
                    }
                }
            }


            // 设置“抗锯齿”的属性
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setStroke(new BasicStroke(bold, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));
            graphics.drawImage(newJigsawImage, 0, 0, null);
            graphics.dispose();

            ByteArrayOutputStream os = new ByteArrayOutputStream();//新建流。
            ImageIO.write(newJigsawImage, IMAGE_TYPE_PNG, os);//利用ImageIO类提供的write方法，将bi以png图片的数据模式写入流。
            byte[] jigsawImages = os.toByteArray();

            ByteArrayOutputStream oriImagesOs = new ByteArrayOutputStream();//新建流。
            ImageIO.write(originalImage, IMAGE_TYPE_JPG, oriImagesOs);//底图输出为JPG格式（体积更小）
            byte[] oriCopyImages = oriImagesOs.toByteArray();
            Base64.Encoder encoder = Base64.getEncoder();
            dataVO.setOriginalImageBase64(encoder.encodeToString(oriCopyImages).replaceAll("\r|\n", ""));
            //point信息不传到前端，只做后端check校验
//            dataVO.setPoint(point);
            dataVO.setJigsawImageBase64(encoder.encodeToString(jigsawImages).replaceAll("\r|\n", ""));
            dataVO.setToken(RandomUtils.getUUID());
            dataVO.setSecretKey(point.getSecretKey());
//            base64StrToImage(encoder.encodeToString(oriCopyImages), "D:\\原图.png");
//            base64StrToImage(encoder.encodeToString(jigsawImages), "D:\\滑动.png");

            //将坐标信息存入redis中
            String codeKey = String.format(REDIS_CAPTCHA_KEY, dataVO.getToken());
            CaptchaServiceFactory.getCache(cacheType).set(codeKey, JsonUtil.toJSONString(point), EXPIRESIN_SECONDS);
            logger.debug("token：{},point:{}", dataVO.getToken(), JsonUtil.toJSONString(point));
            return dataVO;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    /**
     * 随机生成拼图坐标
     *
     * @param originalWidth
     * @param originalHeight
     * @param jigsawWidth
     * @param jigsawHeight
     * @return
     */
    private static PointVO generateJigsawPoint(int originalWidth, int originalHeight, int jigsawWidth, int jigsawHeight) {
        Random random = new Random();
        int widthDifference = originalWidth - jigsawWidth;
        int heightDifference = originalHeight - jigsawHeight;
        int x, y = 0;
        if (widthDifference <= 0) {
            x = 5;
        } else {
            x = random.nextInt(originalWidth - jigsawWidth - 200) + 200;
        }
        if (heightDifference <= 0) {
            y = 5;
        } else {
            y = random.nextInt(originalHeight - jigsawHeight) + 5;
        }
        String key = null;
        if (captchaAesStatus) {
            key = AESUtil.getKey();
        }
        return new PointVO(x, y, key);
    }

    /**
     * @param oriImage      原图
     * @param templateImage 模板图
     * @param newImage      新抠出的小图
     * @param x             随机扣取坐标X
     * @param y             随机扣取坐标y
     * @throws Exception
     */
    private static void cutByTemplate(BufferedImage oriImage, BufferedImage templateImage, BufferedImage newImage, int x, int y) {
        //临时数组遍历用于高斯模糊存周边像素值
        int[][] martrix = new int[3][3];
        int[] values = new int[9];

        int xLength = templateImage.getWidth();
        int yLength = templateImage.getHeight();
        for (int i = 0; i < xLength; i++) {
            for (int j = 0; j < yLength; j++) {
                int templateRgb = templateImage.getRGB(i, j);
                int alpha = (templateRgb >> 24) & 0xff;
                if (alpha > 0) {
                    if (alpha > 200) {
                        // 完全内部像素：滑块抠背景色，底图凹槽高斯模糊
                        int oriRgb = oriImage.getRGB(x + i, y + j);
                        newImage.setRGB(i, j, (alpha << 24) | (oriRgb & 0x00FFFFFF));
                        readPixel(oriImage, x + i, y + j, values);
                        fillMatrix(martrix, values);
                        int blurredRgb = avgMatrix(martrix);
                        oriImage.setRGB(x + i, y + j, blurredRgb);
                    } else {
                        // 边缘过渡像素：滑块与凹槽同一套白描边逻辑——按 alpha 渐变混合白色，粗细浓度完全对称
                        oriImage.setRGB(x + i, y + j, blendWithWhite(oriImage.getRGB(x + i, y + j), alpha));
                        newImage.setRGB(i, j, (alpha << 24) | 0x00FFFFFF);
                    }
                }
            }
        }
    }


    /**
     * 干扰抠图处理
     *
     * @param oriImage      原图
     * @param templateImage 模板图
     * @param x             随机扣取坐标X
     * @param y             随机扣取坐标y
     * @throws Exception
     */
    private static void interferenceByTemplate(BufferedImage oriImage, BufferedImage templateImage, int x, int y) {
        int[][] martrix = new int[3][3];
        int[] values = new int[9];

        int xLength = templateImage.getWidth();
        int yLength = templateImage.getHeight();
        for (int i = 0; i < xLength; i++) {
            for (int j = 0; j < yLength; j++) {
                int rgb = templateImage.getRGB(i, j);
                int alpha = (rgb >> 24) & 0xff;
                if (alpha > 0) {
                    if (alpha > 200) {
                        // 完全内部像素：高斯模糊凹槽效果
                        readPixel(oriImage, x + i, y + j, values);
                        fillMatrix(martrix, values);
                        int blurredRgb = avgMatrix(martrix);
                        oriImage.setRGB(x + i, y + j, blurredRgb);
                    } else {
                        // 边缘过渡像素：与主拼图凹槽统一的白描边逻辑（alpha 渐变混合白）
                        oriImage.setRGB(x + i, y + j, blendWithWhite(oriImage.getRGB(x + i, y + j), alpha));
                    }
                }
            }
        }
        // 干扰图不画描边
    }

    /**
     * 按模板 alpha 比例将原像素与白色混合，得到柔和淡雅的白描边
     * （alpha 越大白色越浓，随模板边缘自然渐变，滑块与凹槽共用同一逻辑保证视觉统一）
     */
    private static int blendWithWhite(int rgb, int alpha) {
        float ratio = alpha / 255f;
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        int nr = (int) (r * (1 - ratio) + 255 * ratio);
        int ng = (int) (g * (1 - ratio) + 255 * ratio);
        int nb = (int) (b * (1 - ratio) + 255 * ratio);
        return 0xFF000000 | (nr << 16) | (ng << 8) | nb;
    }

    private static void readPixel(BufferedImage img, int x, int y, int[] pixels) {
        int xStart = x - 1;
        int yStart = y - 1;
        int current = 0;
        for (int i = xStart; i < 3 + xStart; i++) {
            for (int j = yStart; j < 3 + yStart; j++) {
                int tx = i;
                if (tx < 0) {
                    tx = -tx;

                } else if (tx >= img.getWidth()) {
                    tx = x;
                }
                int ty = j;
                if (ty < 0) {
                    ty = -ty;
                } else if (ty >= img.getHeight()) {
                    ty = y;
                }
                pixels[current++] = img.getRGB(tx, ty);

            }
        }
    }

    private static void fillMatrix(int[][] matrix, int[] values) {
        int filled = 0;
        for (int i = 0; i < matrix.length; i++) {
            int[] x = matrix[i];
            for (int j = 0; j < x.length; j++) {
                x[j] = values[filled++];
            }
        }
    }

    private static int avgMatrix(int[][] matrix) {
        int r = 0;
        int g = 0;
        int b = 0;
        for (int i = 0; i < matrix.length; i++) {
            int[] x = matrix[i];
            for (int j = 0; j < x.length; j++) {
                if (j == 1) {
                    continue;
                }
                Color c = new Color(x[j]);
                r += c.getRed();
                g += c.getGreen();
                b += c.getBlue();
            }
        }
        return new Color(r / 8, g / 8, b / 8).getRGB();
    }


}
