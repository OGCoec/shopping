package com.example.ShoppingSystem.service.captcha.tianai.resource;

import cloud.tianai.captcha.application.vo.ImageCaptchaVO;
import cloud.tianai.captcha.common.response.ApiResponse;
import cloud.tianai.captcha.resource.common.model.dto.Resource;
import cloud.tianai.captcha.resource.common.model.dto.ResourceMap;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cloud.tianai.captcha.generator.impl.StandardSliderImageCaptchaGenerator.TEMPLATE_ACTIVE_IMAGE_NAME;
import static cloud.tianai.captcha.generator.impl.StandardSliderImageCaptchaGenerator.TEMPLATE_FIXED_IMAGE_NAME;

@Slf4j
@Aspect
@Component
@ConditionalOnProperty(prefix = "captcha.trace", name = "enabled", havingValue = "true")
public class TianaiCaptchaTraceAspect {

    @Around("execution(* com.example.ShoppingSystem.service.impl.TianaiCaptchaServiceImpl.generateRotateCaptcha(..))"
            + " || execution(* com.example.ShoppingSystem.service.impl.TianaiCaptchaServiceImpl.generateSliderCaptcha(..))"
            + " || execution(* com.example.ShoppingSystem.service.impl.TianaiCaptchaServiceImpl.generateConcatCaptcha(..))"
            + " || execution(* com.example.ShoppingSystem.service.impl.TianaiCaptchaServiceImpl.generateWordClickCaptcha(..))"
            + " || execution(* com.example.ShoppingSystem.service.impl.TianaiCaptchaServiceImpl.generateCaptcha(..))")
    public Object traceCaptchaGeneration(ProceedingJoinPoint joinPoint) throws Throwable {
        String requestedType = resolveRequestedType(joinPoint);
        log.info("[TianaiCaptcha] generating type={}", requestedType);
        Object result = joinPoint.proceed();
        ImageCaptchaVO captcha = extractCaptcha(result);
        if (captcha != null) {
            log.info("[TianaiCaptcha] generated type={} id={} bg={}x{} template={}x{} bgTag={} templateTag={}",
                    valueOrUnknown(captcha.getType(), requestedType),
                    captcha.getId(),
                    captcha.getBackgroundImageWidth(),
                    captcha.getBackgroundImageHeight(),
                    captcha.getTemplateImageWidth(),
                    captcha.getTemplateImageHeight(),
                    captcha.getBackgroundImageTag(),
                    captcha.getTemplateImageTag());
        }
        return result;
    }

    @Around("execution(* cloud.tianai.captcha.resource.ResourceStore.randomGetResourceByTypeAndTag(..))"
            + " || execution(* cloud.tianai.captcha.spring.plugins.RedisResourceStore.randomGetResourceByTypeAndTag(..))")
    public Object traceResourceSelection(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();
        Object[] args = joinPoint.getArgs();
        String type = argAsString(args, 0);
        String tag = argAsString(args, 1);
        Integer quantity = argAsInteger(args, 2);
        if (result instanceof Collection<?> resources) {
            log.info("[TianaiCaptcha] selected resource type={} tag={} quantity={} resources={}",
                    type, tag, quantity, formatResources(resources));
        }
        return result;
    }

    @Around("execution(* cloud.tianai.captcha.resource.ResourceStore.randomGetTemplateByTypeAndTag(..))"
            + " || execution(* cloud.tianai.captcha.spring.plugins.RedisResourceStore.randomGetTemplateByTypeAndTag(..))")
    public Object traceTemplateSelection(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();
        Object[] args = joinPoint.getArgs();
        String type = argAsString(args, 0);
        String tag = argAsString(args, 1);
        Integer quantity = argAsInteger(args, 2);
        if (result instanceof Collection<?> templates) {
            log.info("[TianaiCaptcha] selected template type={} tag={} quantity={} templates={}",
                    type, tag, quantity, formatTemplates(templates));
        }
        return result;
    }

    private String resolveRequestedType(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args.length > 0 && args[0] instanceof String type) {
            return type;
        }
        return switch (joinPoint.getSignature().getName()) {
            case "generateRotateCaptcha" -> "ROTATE";
            case "generateSliderCaptcha" -> "SLIDER";
            case "generateConcatCaptcha" -> "CONCAT";
            case "generateWordClickCaptcha" -> "WORD_IMAGE_CLICK";
            default -> "UNKNOWN";
        };
    }

    private ImageCaptchaVO extractCaptcha(Object result) {
        if (result instanceof ImageCaptchaVO captcha) {
            return captcha;
        }
        if (result instanceof ApiResponse<?> response && response.getData() instanceof ImageCaptchaVO captcha) {
            return captcha;
        }
        return null;
    }

    private String formatResources(Collection<?> resources) {
        return resources.stream()
                .filter(Resource.class::isInstance)
                .map(Resource.class::cast)
                .map(this::formatResource)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private String formatResource(Resource resource) {
        return "{id=" + resource.getId()
                + ", tag=" + resource.getTag()
                + ", source=" + resource.getType() + ":" + resource.getData()
                + "}";
    }

    private String formatTemplates(Collection<?> templates) {
        return templates.stream()
                .filter(ResourceMap.class::isInstance)
                .map(ResourceMap.class::cast)
                .map(this::formatTemplate)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private String formatTemplate(ResourceMap template) {
        Resource active = template.get(TEMPLATE_ACTIVE_IMAGE_NAME);
        Resource fixed = template.get(TEMPLATE_FIXED_IMAGE_NAME);
        return "{id=" + template.getId()
                + ", tag=" + template.getTag()
                + ", name=" + inferTemplateName(active, fixed)
                + ", active=" + formatResourceSource(active)
                + ", fixed=" + formatResourceSource(fixed)
                + ", entries=" + formatTemplateEntries(template)
                + "}";
    }

    private String inferTemplateName(Resource active, Resource fixed) {
        String data = active != null ? active.getData() : fixed != null ? fixed.getData() : null;
        if (data == null || data.isBlank()) {
            return "UNKNOWN";
        }
        String normalized = data.replace('\\', '/');
        int marker = normalized.lastIndexOf('/');
        if (marker <= 0) {
            return normalized;
        }
        String fileName = normalized.substring(marker + 1);
        String parentPath = normalized.substring(0, marker);
        int parentMarker = parentPath.lastIndexOf('/');
        String parent = parentMarker >= 0 ? parentPath.substring(parentMarker + 1) : parentPath;
        return fileName.endsWith(".png") ? parent : fileName;
    }

    private String formatResourceSource(Resource resource) {
        if (resource == null) {
            return "null";
        }
        return resource.getType() + ":" + resource.getData();
    }

    private String formatTemplateEntries(ResourceMap template) {
        Map<String, Resource> resourceMap = template.getResourceMap();
        if (resourceMap == null || resourceMap.isEmpty()) {
            return "{}";
        }
        return resourceMap.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + formatResourceSource(entry.getValue()))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    private String argAsString(Object[] args, int index) {
        if (args.length <= index || args[index] == null) {
            return null;
        }
        return String.valueOf(args[index]);
    }

    private Integer argAsInteger(Object[] args, int index) {
        if (args.length <= index || args[index] == null) {
            return null;
        }
        if (args[index] instanceof Integer value) {
            return value;
        }
        return null;
    }

    private String valueOrUnknown(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
