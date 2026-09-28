package com.anji.captcha.service.impl;

import com.anji.captcha.model.common.CaptchaTypeEnum;
import com.anji.captcha.model.common.ResponseModel;
import com.anji.captcha.model.vo.CaptchaVO;
import com.anji.captcha.model.vo.PointVO;
import com.anji.captcha.service.CaptchaService;
import com.anji.captcha.util.AESUtil;
import com.anji.captcha.util.JsonUtil;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Properties;

/**
 * 滑块曲线验证码 V3 全链路单元测试
 * get -> check(成功/失败) -> verification
 *
 * V3交互: 后端返回底图(含目标曲线) + 曲线参数JSON,
 * 前端Canvas绘制活动曲线, check时传活动曲线控制点x坐标.
 */
public class CurveSliderCaptchaServiceImplTest {

    private static CaptchaService service;

    @BeforeClass
    public static void init() {
        Properties config = new Properties();
        config.setProperty("captcha.cacheType", "local");
        config.setProperty("captcha.water.mark", "我的水印");
        config.setProperty("captcha.aes.status", "true");
        service = CaptchaServiceFactory.getInstance(config);
    }

    @Test
    public void testGet() {
        CaptchaVO req = new CaptchaVO();
        req.setCaptchaType(CaptchaTypeEnum.CURVESLIDER.getCodeValue());
        ResponseModel r = service.get(req);
        Assert.assertNotNull(r);
        Assert.assertTrue(r.isSuccess());
        CaptchaVO data = (CaptchaVO) r.getRepData();
        Assert.assertNotNull(data);
        Assert.assertNotNull(data.getToken());
        Assert.assertNotNull(data.getOriginalImageBase64());
        Assert.assertNotNull(data.getJigsawImageBase64());
        Assert.assertNotNull(data.getSecretKey());
        // jigsawImageBase64 应为曲线参数JSON（含startX/endX等字段）
        Assert.assertTrue("jigsawImageBase64应为JSON参数",
                data.getJigsawImageBase64().contains("startX"));
        System.out.println("get成功, token=" + data.getToken()
                + ", originalImageBase64.length=" + data.getOriginalImageBase64().length()
                + ", curveParams=" + data.getJigsawImageBase64());
    }

    @Test
    public void testCheckSuccess() {
        CaptchaVO req = new CaptchaVO();
        req.setCaptchaType(CaptchaTypeEnum.CURVESLIDER.getCodeValue());
        ResponseModel r = service.get(req);
        CaptchaVO data = (CaptchaVO) r.getRepData();
        String token = data.getToken();
        String secretKey = data.getSecretKey();

        // 从缓存取目标控制点x(模拟前端拿不到,这里测试用)
        String codeKey = String.format("RUNNING:CAPTCHA:%s", token);
        String s = CaptchaServiceFactory.getCache("local").get(codeKey);
        PointVO target = JsonUtil.parseObject(s, PointVO.class);

        // 模拟前端: 把活动曲线控制点x坐标加密回传, y传0(只校验x)
        CaptchaVO checkReq = new CaptchaVO();
        checkReq.setCaptchaType(CaptchaTypeEnum.CURVESLIDER.getCodeValue());
        checkReq.setToken(token);
        String pointJson = JsonUtil.toJSONString(new PointVO(target.getX(), 0, null));
        try {
            checkReq.setPointJson(AESUtil.aesEncrypt(pointJson, secretKey));
        } catch (Exception e) {
            Assert.fail("加密失败:" + e.getMessage());
        }
        ResponseModel checkRes = service.check(checkReq);
        Assert.assertTrue("check应成功, 实际:" + checkRes.getRepMsg(), checkRes.isSuccess());
        System.out.println("check成功: " + checkRes.getRepMsg());
    }

    @Test
    public void testCheckWrong() {
        CaptchaVO req = new CaptchaVO();
        req.setCaptchaType(CaptchaTypeEnum.CURVESLIDER.getCodeValue());
        ResponseModel r = service.get(req);
        CaptchaVO data = (CaptchaVO) r.getRepData();
        String token = data.getToken();
        String secretKey = data.getSecretKey();

        // 模拟前端: 传一个明显错误的控制点x坐标(离目标很远)
        CaptchaVO checkReq = new CaptchaVO();
        checkReq.setCaptchaType(CaptchaTypeEnum.CURVESLIDER.getCodeValue());
        checkReq.setToken(token);
        String pointJson = JsonUtil.toJSONString(new PointVO(3, 0, null));
        try {
            checkReq.setPointJson(AESUtil.aesEncrypt(pointJson, secretKey));
        } catch (Exception e) {
            Assert.fail("加密失败:" + e.getMessage());
        }
        ResponseModel checkRes = service.check(checkReq);
        Assert.assertFalse(checkRes.isSuccess());
        System.out.println("错误坐标check失败(符合预期): " + checkRes.getRepMsg());
    }

    @Test
    public void testVerification() {
        // 先通过 get + check 拿到 captchaVerification
        CaptchaVO req = new CaptchaVO();
        req.setCaptchaType(CaptchaTypeEnum.CURVESLIDER.getCodeValue());
        ResponseModel r = service.get(req);
        CaptchaVO data = (CaptchaVO) r.getRepData();
        String token = data.getToken();
        String secretKey = data.getSecretKey();

        String s = CaptchaServiceFactory.getCache("local").get(String.format("RUNNING:CAPTCHA:%s", token));
        PointVO point = JsonUtil.parseObject(s, PointVO.class);

        CaptchaVO checkReq = new CaptchaVO();
        checkReq.setCaptchaType(CaptchaTypeEnum.CURVESLIDER.getCodeValue());
        checkReq.setToken(token);
        String pointJson = JsonUtil.toJSONString(new PointVO(point.getX(), 0, null));
        try {
            checkReq.setPointJson(AESUtil.aesEncrypt(pointJson, secretKey));
        } catch (Exception e) {
            Assert.fail("加密失败:" + e.getMessage());
        }
        ResponseModel checkRes = service.check(checkReq);
        Assert.assertTrue("check应成功", checkRes.isSuccess());

        // 构造二次校验参数
        String captchaVerification = null;
        try {
            captchaVerification = AESUtil.aesEncrypt(token + "---" + pointJson, secretKey);
        } catch (Exception e) {
            Assert.fail("加密失败:" + e.getMessage());
        }
        CaptchaVO verReq = new CaptchaVO();
        verReq.setCaptchaType(CaptchaTypeEnum.CURVESLIDER.getCodeValue());
        verReq.setCaptchaVerification(captchaVerification);
        ResponseModel verRes = service.verification(verReq);
        Assert.assertTrue("二次校验应成功, 实际:" + verRes.getRepMsg(), verRes.isSuccess());
        System.out.println("二次校验verification成功: " + verRes.getRepMsg());
    }
}
