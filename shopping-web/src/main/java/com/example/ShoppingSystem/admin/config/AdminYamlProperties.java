package com.example.ShoppingSystem.admin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AdminYamlProperties {

    private final String path;

    public AdminYamlProperties(@Value("${admin.config.path:config/admin.yaml}") String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}
