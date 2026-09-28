package com.anji.captcha.config;

import com.anji.captcha.model.common.Const;
import com.anji.captcha.service.CaptchaCacheService;
import com.anji.captcha.service.CaptchaService;
import com.anji.captcha.service.impl.CaptchaServiceFactory;
import com.anji.captcha.util.Base64Utils;
import com.anji.captcha.util.ImageUtils;
import com.anji.captcha.util.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import org.springframework.util.FileCopyUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Configuration
public class CaptchaConfig {

    @Bean(name = "AjCaptchaCacheService")
    public CaptchaCacheService captchaCacheService() {
        //缓存类型redis/local/....
        return CaptchaServiceFactory.getCache("local");
    }

    @Bean
    @DependsOn("AjCaptchaCacheService")
    public CaptchaService captchaService() {
        Properties config = new Properties();
//        try {
//            try (InputStream input = CaptchaConfig.class.getClassLoader()
//                    .getResourceAsStream("application.properties")) {
//                config.load(input);
//            }
//        }catch (Exception ex){
//            ex.printStackTrace();
//        }
        //各种参数设置....
        //缓存类型redis/local/....
        config.put(Const.CAPTCHA_CACHETYPE, "local");
        config.put(Const.CAPTCHA_WATER_MARK, "我的水印");
        config.put(Const.CAPTCHA_FONT_TYPE, "宋体");
        config.put(Const.CAPTCHA_TYPE, "default");
        config.put(Const.CAPTCHA_INTERFERENCE_OPTIONS, "0");
        config.put(Const.ORIGINAL_PATH_JIGSAW, "");
        config.put(Const.ORIGINAL_PATH_PIC_CLICK, "");
        config.put(Const.ORIGINAL_PATH_CURVE_SLIDER, "");
        config.put(Const.CAPTCHA_SLIP_OFFSET, "5");
        config.put(Const.CAPTCHA_CURVE_OFFSET, "8");
        config.put(Const.CAPTCHA_AES_STATUS, "true");
        config.put(Const.CAPTCHA_WATER_FONT, "宋体");
        config.put(Const.CAPTCHA_CACAHE_MAX_NUMBER, "1000");
        config.put(Const.CAPTCHA_TIMING_CLEAR_SECOND, "180");
        //更多自定义参数，请参考service/springboot/../resources/application.properties
        if ((StringUtils.isNotBlank(config.getProperty(Const.ORIGINAL_PATH_JIGSAW))
                && config.getProperty(Const.ORIGINAL_PATH_JIGSAW).startsWith("classpath:"))
                || (StringUtils.isNotBlank(config.getProperty(Const.ORIGINAL_PATH_PIC_CLICK))
                && config.getProperty(Const.ORIGINAL_PATH_PIC_CLICK).startsWith("classpath:"))
                || (StringUtils.isNotBlank(config.getProperty(Const.ORIGINAL_PATH_CURVE_SLIDER))
                && config.getProperty(Const.ORIGINAL_PATH_CURVE_SLIDER).startsWith("classpath:"))) {
            //自定义resources目录下初始化底图
            config.put(Const.CAPTCHA_INIT_ORIGINAL, "true");
            initializeBaseMap(config.getProperty(Const.ORIGINAL_PATH_JIGSAW),
                    config.getProperty(Const.ORIGINAL_PATH_PIC_CLICK),
                    config.getProperty(Const.ORIGINAL_PATH_CURVE_SLIDER));
        }
        CaptchaService s = CaptchaServiceFactory.getInstance(config);
        return s;
    }

    private static void initializeBaseMap(String jigsaw, String picClick, String curveSlider) {
        String curvePath = StringUtils.isNotBlank(curveSlider) ? curveSlider : null;
        ImageUtils.cacheBootImage(
                getResourcesImagesFile(jigsaw + "/original/*.png"),
                getResourcesImagesFile(jigsaw + "/slidingBlock/*.png"),
                getResourcesImagesFile(picClick + "/*.png"),
                curvePath != null ? getResourcesImagesFile(curvePath + "/original/*.png") : new HashMap<>());
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
