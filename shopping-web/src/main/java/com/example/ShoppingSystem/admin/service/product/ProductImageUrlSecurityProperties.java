package com.example.ShoppingSystem.admin.service.product;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "shopping.admin.product-image-security")
public class ProductImageUrlSecurityProperties {

    private List<String> allowedHosts = new ArrayList<>();
    private List<String> allowedPathPrefixes = new ArrayList<>(List.of("/shopping/"));
    private boolean allowLocalHttp = true;

    public List<String> getAllowedHosts() {
        return allowedHosts;
    }

    public void setAllowedHosts(List<String> allowedHosts) {
        this.allowedHosts = allowedHosts == null ? new ArrayList<>() : new ArrayList<>(allowedHosts);
    }

    public List<String> getAllowedPathPrefixes() {
        return allowedPathPrefixes;
    }

    public void setAllowedPathPrefixes(List<String> allowedPathPrefixes) {
        this.allowedPathPrefixes = allowedPathPrefixes == null ? new ArrayList<>() : new ArrayList<>(allowedPathPrefixes);
    }

    public boolean isAllowLocalHttp() {
        return allowLocalHttp;
    }

    public void setAllowLocalHttp(boolean allowLocalHttp) {
        this.allowLocalHttp = allowLocalHttp;
    }
}
