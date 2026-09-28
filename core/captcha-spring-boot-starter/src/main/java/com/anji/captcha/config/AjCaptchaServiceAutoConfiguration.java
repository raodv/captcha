package com.anji.captcha.config;


import com.anji.captcha.model.common.Const;
import com.anji.captcha.properties.AjCaptchaProperties;
import com.anji.captcha.service.CaptchaService;
import com.anji.captcha.service.impl.CaptchaServiceFactory;
import com.anji.captcha.util.Base64Utils;
import com.anji.captcha.util.ImageUtils;
import com.anji.captcha.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.FileCopyUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Configuration
public class AjCaptchaServiceAutoConfiguration {

    private static Logger logger = LoggerFactory.getLogger(AjCaptchaServiceAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public CaptchaService captchaService(AjCaptchaProperties prop) {
        logger.info("自定义配置项：{}", prop.toString());
        Properties config = new Properties();
        config.put(Const.CAPTCHA_CACHETYPE, prop.getCacheType().name());
        config.put(Const.CAPTCHA_WATER_MARK, prop.getWaterMark());
        config.put(Const.CAPTCHA_FONT_TYPE, prop.getFontType());
        config.put(Const.CAPTCHA_TYPE, prop.getType().getCodeValue());
        config.put(Const.CAPTCHA_INTERFERENCE_OPTIONS, prop.getInterferenceOptions());
        config.put(Const.ORIGINAL_PATH_JIGSAW, prop.getJigsaw());
        config.put(Const.ORIGINAL_PATH_PIC_CLICK, prop.getPicClick());
        config.put(Const.ORIGINAL_PATH_CURVE_SLIDER, prop.getCurveSlider());
        config.put(Const.CAPTCHA_SLIP_OFFSET, prop.getSlipOffset());
        config.put(Const.CAPTCHA_CURVE_OFFSET, prop.getCurveOffset());
        config.put(Const.CAPTCHA_AES_STATUS, String.valueOf(prop.getAesStatus()));
        config.put(Const.CAPTCHA_WATER_FONT, prop.getWaterFont());
        config.put(Const.CAPTCHA_CACAHE_MAX_NUMBER, prop.getCacheNumber());
        config.put(Const.CAPTCHA_TIMING_CLEAR_SECOND, prop.getTimingClear());

        config.put(Const.HISTORY_DATA_CLEAR_ENABLE, prop.isHistoryDataClearEnable() ? "1" : "0");

        config.put(Const.REQ_FREQUENCY_LIMIT_ENABLE, prop.getReqFrequencyLimitEnable() ? "1" : "0");
        config.put(Const.REQ_GET_LOCK_LIMIT, prop.getReqGetLockLimit() + "");
        config.put(Const.REQ_GET_LOCK_SECONDS, prop.getReqGetLockSeconds() + "");
        config.put(Const.REQ_GET_MINUTE_LIMIT, prop.getReqGetMinuteLimit() + "");
        config.put(Const.REQ_CHECK_MINUTE_LIMIT, prop.getReqCheckMinuteLimit() + "");
        config.put(Const.REQ_VALIDATE_MINUTE_LIMIT, prop.getReqVerifyMinuteLimit() + "");

        config.put(Const.CAPTCHA_FONT_SIZE, prop.getFontSize() + "");
        config.put(Const.CAPTCHA_FONT_STYLE, prop.getFontStyle() + "");
        config.put(Const.CAPTCHA_WORD_COUNT, prop.getClickWordCount() + "");

        if ((StringUtils.isNotBlank(prop.getJigsaw()) && prop.getJigsaw().startsWith("classpath"))
                || (StringUtils.isNotBlank(prop.getPicClick()) && prop.getPicClick().startsWith("classpath"))
                || (StringUtils.isNotBlank(prop.getCurveSlider()) && prop.getCurveSlider().startsWith("classpath"))) {
            //自定义resources目录下初始化底图
            config.put(Const.CAPTCHA_INIT_ORIGINAL, "true");
            initializeBaseMap(prop.getJigsaw(), prop.getPicClick(), prop.getCurveSlider());
        }
        CaptchaService s = CaptchaServiceFactory.getInstance(config);
        return s;
    }

    private static void initializeBaseMap(String jigsaw, String picClick, String curveSlider) {
        // 未配置 classpath 路径的类型，回退到 core 默认底图目录
        Map<String, String> originalMap, slidingBlockMap, picClickMap, curveSliderMap;

        if (StringUtils.isNotBlank(jigsaw)) {
            originalMap = getResourcesImagesFile(jigsaw + "/original/*.png");
            slidingBlockMap = getResourcesImagesFile(jigsaw + "/slidingBlock/*.png");
        } else {
            originalMap = getDefaultResourcesImagesFile("defaultImages/jigsaw/original");
            slidingBlockMap = getDefaultResourcesImagesFile("defaultImages/jigsaw/slidingBlock");
        }

        if (StringUtils.isNotBlank(picClick)) {
            picClickMap = getResourcesImagesFile(picClick + "/*.png");
        } else {
            picClickMap = getDefaultResourcesImagesFile("defaultImages/pic-click");
        }

        if (StringUtils.isNotBlank(curveSlider)) {
            curveSliderMap = getResourcesImagesFile(curveSlider + "/original/*.png");
        } else {
            curveSliderMap = getDefaultResourcesImagesFile("defaultImages/curveSlider/original");
        }

        ImageUtils.cacheBootImage(originalMap, slidingBlockMap, picClickMap, curveSliderMap);
    }

    private static Map<String, String> getDefaultResourcesImagesFile(String path) {
        Map<String, String> imgMap = new HashMap<>();
        ClassLoader classLoader = AjCaptchaServiceAutoConfiguration.class.getClassLoader();
        for (int i = 1; i <= 6; i++) {
            String filePath = path + "/" + i + ".png";
            org.springframework.core.io.ClassPathResource resource =
                    new org.springframework.core.io.ClassPathResource(filePath);
            try {
                byte[] bytes = FileCopyUtils.copyToByteArray(resource.getInputStream());
                String base64 = Base64Utils.encodeToString(bytes);
                imgMap.put(i + ".png", base64);
            } catch (Exception e) {
                // 默认图片不存在时跳过
            }
        }
        return imgMap;
    }

    public static Map<String, String> getResourcesImagesFile(String path) {
        Map<String, String> imgMap = new HashMap<>();
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources(path);
            for (Resource resource : resources) {
                byte[] bytes = FileCopyUtils.copyToByteArray(resource.getInputStream());
                String string = Base64Utils.encodeToString(bytes);
                String filename = resource.getFilename();
                imgMap.put(filename, string);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return imgMap;
    }
}
