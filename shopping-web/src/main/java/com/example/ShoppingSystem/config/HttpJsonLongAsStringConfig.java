package com.example.ShoppingSystem.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Configure only Spring MVC JSON output so service-level ObjectMapper beans keep their existing behavior.
 */
@Configuration
public class HttpJsonLongAsStringConfig implements WebMvcConfigurer {

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter jacksonConverter) {
                ObjectMapper httpObjectMapper = jacksonConverter.getObjectMapper().copy();
                SimpleModule longAsStringModule = new SimpleModule()
                        .addSerializer(Long.class, ToStringSerializer.instance)
                        .addSerializer(Long.TYPE, ToStringSerializer.instance);
                httpObjectMapper.registerModule(longAsStringModule);
                jacksonConverter.setObjectMapper(httpObjectMapper);
            }
        }
    }
}
