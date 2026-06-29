package com.anji.captcha.config;

import com.anji.captcha.model.common.ResponseModel;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Objects;

@Aspect
@ControllerAdvice
@Component
@ConditionalOnProperty(value = "aj.captcha.i18n.enabled",matchIfMissing = false)
public class I18nAspect implements ResponseBodyAdvice<Object> {

    {
        System.out.println("===>I18nAspect init...");
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return returnType.getParameterType().isAssignableFrom(ResponseModel.class);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ResponseModel) {
            ResponseModel res = (ResponseModel) body;
            String code = res.getRepCode();
            String desc = getMessage(code,res);
            res.setRepMsg(desc);
            return res;
        }
        return body;
    }

    private String getMessage(String code,ResponseModel res){
        try {
            if(Objects.isNull(messageSource)){
                messageSource = applicationContext.getBean(MessageSource.class);
            }
            return messageSource.getMessage(prefix + code, null, LocaleContextHolder.getLocale());
        } catch (Exception e) {
            return res.getRepMsg(); // 如果找不到对应的国际化信息，返回默认信息
        }
    }

    @Autowired(required = false)
    private MessageSource messageSource;

    @Value("${aj.captcha.i18n.prefix:aj.captcha.}")
    private String prefix;

    @Autowired
    private ApplicationContext applicationContext;
}
