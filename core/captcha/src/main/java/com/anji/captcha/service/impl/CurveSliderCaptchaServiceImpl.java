/*
 *Copyright © 2018 anji-plus
 *安吉加加信息技术有限公司
 *http://www.anji-plus.com
 *All rights reserved.
 */
package com.anji.captcha.service.impl;

import com.anji.captcha.model.common.CaptchaTypeEnum;
import com.anji.captcha.model.common.Const;
import com.anji.captcha.model.common.RepCodeEnum;
import com.anji.captcha.model.common.ResponseModel;
import com.anji.captcha.model.vo.CaptchaVO;
import com.anji.captcha.model.vo.PointVO;
import com.anji.captcha.util.AESUtil;
import com.anji.captcha.util.ImageUtils;
import com.anji.captcha.util.JsonUtil;
import com.anji.captcha.util.RandomUtils;
import com.anji.captcha.util.StringUtils;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.QuadCurve2D;
import java.awt.image.BufferedImage;
import java.util.Properties;

/**
 * 滑块曲线验证码（V3：端点固定，拖动改变弧度）
 * <p>
 * 在底图上绘制一条目标U型二次贝塞尔曲线（灰色半透明+白色端点），
 * 两端点固定在图片左右两侧。用户水平拖动底部滑块改变活动曲线的控制点x坐标，
 * 使活动曲线弧度变化，当活动曲线与目标曲线重合时验证通过。
 * <p>
 * 后端返回底图（含目标曲线）+ 曲线参数JSON（startX/endY/topY/bottomY/ctrlXMin/ctrlXMax/imgWidth/imgHeight）。
 * 前端使用Canvas根据滑块位置实时绘制活动曲线（蓝色），check时传活动曲线控制点x坐标。
 */
public class CurveSliderCaptchaServiceImpl extends AbstractCaptchaService {

    /**
     * 校验控制点x坐标允许误差(像素)
     */
    protected static String curveOffset = "8";

    @Override
    public String captchaType() {
        return CaptchaTypeEnum.CURVESLIDER.getCodeValue();
    }

    @Override
    public void init(Properties config) {
        super.init(config);
        curveOffset = config.getProperty(Const.CAPTCHA_CURVE_OFFSET, "8");
    }

    @Override
    public void destroy(Properties config) {
        logger.info("start-clear-history-data:{}", captchaType());
    }

    @Override
    public ResponseModel get(CaptchaVO captchaVO) {
        ResponseModel r = super.get(captchaVO);
        if (!validatedReq(r)) {
            return r;
        }
        BufferedImage originalImage = ImageUtils.getCurveOriginal();
        if (null == originalImage) {
            logger.error("滑块曲线底图未初始化成功，请检查路径");
            return ResponseModel.errorMsg(RepCodeEnum.API_CAPTCHA_BASEMAP_NULL);
        }
        CaptchaVO captcha = pictureTemplatesCut(originalImage);
        if (captcha == null
                || StringUtils.isBlank(captcha.getOriginalImageBase64())
                || StringUtils.isBlank(captcha.getJigsawImageBase64())) {
            return ResponseModel.errorMsg(RepCodeEnum.API_CAPTCHA_ERROR);
        }
        return ResponseModel.successData(captcha);
    }

    @Override
    public ResponseModel check(CaptchaVO captchaVO) {
        ResponseModel r = super.check(captchaVO);
        if (!validatedReq(r)) {
            return r;
        }
        String codeKey = String.format(REDIS_CAPTCHA_KEY, captchaVO.getToken());
        if (!CaptchaServiceFactory.getCache(cacheType).exists(codeKey)) {
            return ResponseModel.errorMsg(RepCodeEnum.API_CAPTCHA_INVALID);
        }
        String s = CaptchaServiceFactory.getCache(cacheType).get(codeKey);
        CaptchaServiceFactory.getCache(cacheType).delete(codeKey);
        PointVO point = null;
        PointVO point1 = null;
        String pointJson = null;
        try {
            point = JsonUtil.parseObject(s, PointVO.class);
            pointJson = decrypt(captchaVO.getPointJson(), point.getSecretKey());
            point1 = JsonUtil.parseObject(pointJson, PointVO.class);
        } catch (Exception e) {
            logger.error("验证码坐标解析失败", e);
            afterValidateFail(captchaVO);
            return ResponseModel.errorMsg(e.getMessage());
        }
        int offset = Integer.parseInt(curveOffset);
        // 只校验x坐标（活动曲线控制点x vs 目标曲线控制点x）
        if (point.x - offset > point1.x || point1.x > point.x + offset) {
            afterValidateFail(captchaVO);
            return ResponseModel.errorMsg(RepCodeEnum.API_CAPTCHA_COORDINATE_ERROR);
        }
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
        if (!validatedReq(r)) {
            return r;
        }
        try {
            String codeKey = String.format(REDIS_SECOND_CAPTCHA_KEY, captchaVO.getCaptchaVerification());
            if (!CaptchaServiceFactory.getCache(cacheType).exists(codeKey)) {
                return ResponseModel.errorMsg(RepCodeEnum.API_CAPTCHA_INVALID);
            }
            CaptchaServiceFactory.getCache(cacheType).delete(codeKey);
        } catch (Exception e) {
            logger.error("验证码坐标解析失败", e);
            return ResponseModel.errorMsg(e.getMessage());
        }
        return ResponseModel.success();
    }

    /**
     * 在底图上绘制目标U型曲线，返回底图base64和曲线参数JSON
     *
     * 交互模型：
     * - 两端点固定：startX在左侧，endX在右侧，y均为topY
     * - 控制点在下方（bottomY），x坐标可变，决定曲线弧度
     * - 目标曲线控制点x = targetCtrlX（随机生成，存入缓存作为答案）
     * - 活动曲线控制点x = 滑块位置映射值（前端Canvas实时绘制）
     * - 当活动曲线控制点x ≈ 目标曲线控制点x时，两曲线重合 → 验证通过
     *
     * @param originalImage 底图
     * @return CaptchaVO originalImageBase64=底图(含目标曲线), jigsawImageBase64=曲线参数JSON
     */
    private CaptchaVO pictureTemplatesCut(BufferedImage originalImage) {
        try {
            int w = originalImage.getWidth();
            int h = originalImage.getHeight();

            // === 1. 定义曲线参数（相对于图片尺寸） ===
            int startX = (int) (w * 0.13);    // 左端点x  ~40 (310宽时)
            int endX = (int) (w * 0.87);      // 右端点x  ~270
            // 弧线高度：二次贝塞尔曲线实际最深点 = (topY + bottomY) / 2
            // 端点贴顶部(topY≈8%)，控制点推到图片下方(bottomY≈152%)，
            // 最深点≈80%高度，弧线饱满明显、便于对齐验证
            int topY = (int) (h * 0.08);      // 端点y(上部) ~12 (155高时)
            int bottomY = (int) (h * 1.52);   // 控制点y(下部，可超出图片范围) ~236
            int ctrlXMin = (int) (w * 0.20);  // 控制点x最小值 ~62
            int ctrlXMax = (int) (w * 0.80);  // 控制点x最大值 ~248

            // 随机生成目标控制点x（答案）
            // 前端初始活动曲线在 ctrlXMin 位置，若目标接近 ctrlXMin 则初始几乎重合、无需滑动。
            // 因此目标限制在滑块行程的 30%~100% 区间，保证用户有足够的滑动动作。
            int targetCtrlXMin = ctrlXMin + (int) ((ctrlXMax - ctrlXMin) * 0.3);
            int targetCtrlX = RandomUtils.getRandomInt(targetCtrlXMin, ctrlXMax);

            // === 2. 在底图上绘制水印 + 目标曲线 ===
            Graphics2D bgG = originalImage.createGraphics();
            bgG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 水印
            bgG.setFont(waterMarkFont);
            bgG.setColor(Color.white);
            bgG.drawString(waterMark, w - getEnOrChLength(waterMark), h - (HAN_ZI_SIZE / 2) + 7);

            // 目标曲线：灰色半透明
            QuadCurve2D targetCurve = new QuadCurve2D.Double(
                    startX, topY, targetCtrlX, bottomY, endX, topY);
            bgG.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            bgG.setColor(new Color(180, 180, 180, 120));
            bgG.draw(targetCurve);
            // 白色端点圆点
            bgG.setPaint(Color.WHITE);
            bgG.fillOval(startX - 5, topY - 5, 10, 10);
            bgG.fillOval(endX - 5, topY - 5, 10, 10);
            bgG.dispose();

            // === 3. 组装返回数据 ===
            CaptchaVO dataVO = new CaptchaVO();
            dataVO.setOriginalImageBase64(
                    ImageUtils.getImageToBase64Str(originalImage).replaceAll("\r|\n", ""));

            // jigsawImageBase64 返回曲线参数JSON（非图片base64）
            // 前端据此用Canvas绘制活动曲线
            String curveParams = String.format(
                    "{\"startX\":%d,\"endX\":%d,\"topY\":%d,\"bottomY\":%d,"
                            + "\"ctrlXMin\":%d,\"ctrlXMax\":%d,"
                            + "\"imgWidth\":%d,\"imgHeight\":%d}",
                    startX, endX, topY, bottomY, ctrlXMin, ctrlXMax, w, h);
            dataVO.setJigsawImageBase64(curveParams);

            dataVO.setToken(RandomUtils.getUUID());
            String secretKey = null;
            if (captchaAesStatus) {
                secretKey = AESUtil.getKey();
            }
            // 缓存目标控制点x坐标（答案）
            PointVO target = new PointVO(targetCtrlX, 0, secretKey);
            dataVO.setSecretKey(secretKey);

            String codeKey = String.format(REDIS_CAPTCHA_KEY, dataVO.getToken());
            CaptchaServiceFactory.getCache(cacheType).set(codeKey,
                    JsonUtil.toJSONString(target), EXPIRESIN_SECONDS);
            logger.debug("token：{},targetCtrlX:{}", dataVO.getToken(), targetCtrlX);
            return dataVO;
        } catch (Exception e) {
            logger.error("生成滑块曲线验证码失败", e);
            return null;
        }
    }
}
