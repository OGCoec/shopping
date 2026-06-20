package com.example.ShoppingSystem.config.datasource;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.common.datasource.DataSourceRouteGroups;
import com.example.ShoppingSystem.common.datasource.RoutingDataSourceContext;
import com.example.ShoppingSystem.common.datasource.UseDataSource;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UseDataSourceAspect {

    @Around("@within(com.example.ShoppingSystem.common.datasource.UseDataSource)"
            + " || @annotation(com.example.ShoppingSystem.common.datasource.UseDataSource)")
    public Object route(ProceedingJoinPoint joinPoint) throws Throwable {
        UseDataSource useDataSource = resolveUseDataSource(joinPoint);
        if (useDataSource == null) {
            return joinPoint.proceed();
        }
        return route(useDataSource.value(), joinPoint);
    }

    private Object route(DataSourceRoute route, ProceedingJoinPoint joinPoint) throws Throwable {
        DataSourceRoute previousRoute = RoutingDataSourceContext.snapshot();
        if (previousRoute != null && !DataSourceRouteGroups.sameDomain(previousRoute, route)) {
            throw new IllegalStateException(
                    "DataSource route conflict, current=" + previousRoute + ", requested=" + route
            );
        }
        try {
            RoutingDataSourceContext.use(route);
            return joinPoint.proceed();
        } finally {
            RoutingDataSourceContext.restore(previousRoute);
        }
    }

    private UseDataSource resolveUseDataSource(ProceedingJoinPoint joinPoint) {
        if (joinPoint.getSignature() instanceof MethodSignature methodSignature) {
            Method method = methodSignature.getMethod();
            UseDataSource methodAnnotation = AnnotationUtils.findAnnotation(method, UseDataSource.class);
            if (methodAnnotation != null) {
                return methodAnnotation;
            }
        }
        Object target = joinPoint.getTarget();
        return target == null ? null : AnnotationUtils.findAnnotation(target.getClass(), UseDataSource.class);
    }
}
