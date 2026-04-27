package com.example.ShoppingSystem.Utils;

import com.google.gson.JsonObject;
import com.ip2location.Configuration;
import com.ip2location.IPGeolocation;
import org.junit.jupiter.api.Test;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ip2LocationIoFixedIpIntegrationTest {

    static {
        try {
            System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    private static final String TEST_IP = "66.93.67.154";
    private static final String API_KEY = "";

    @Test
    void shouldQueryIp2LocationIoForFixedIp() throws Exception {
        Configuration configuration = new Configuration();
        configuration.setApiKey(API_KEY);

        IPGeolocation ipGeolocation = new IPGeolocation(configuration);
        JsonObject result = ipGeolocation.Lookup(TEST_IP);

        assertNotNull(result, "IP2Location.io 查询结果不能为空");
        assertTrue(result.has("country_code"), "返回结果必须包含 country_code");
        assertTrue(result.has("country_name"), "返回结果必须包含 country_name");
        assertTrue(result.has("region_name"), "返回结果必须包含 region_name");
        assertTrue(result.has("city_name"), "返回结果必须包含 city_name");

        printResult(result);
    }

    private void printResult(JsonObject result) {
        System.out.println("==== IP2Location.io Fixed IP Result ====");
        System.out.println("IP: " + TEST_IP);
        System.out.println("CountryCode: " + getAsText(result, "country_code"));
        System.out.println("CountryName: " + getAsText(result, "country_name"));
        System.out.println("RegionName: " + getAsText(result, "region_name"));
        System.out.println("CityName: " + getAsText(result, "city_name"));
        System.out.println("District: " + getAsText(result, "district"));
        System.out.println("Latitude: " + getAsText(result, "latitude"));
        System.out.println("Longitude: " + getAsText(result, "longitude"));
        System.out.println("ZipCode: " + getAsText(result, "zip_code"));
        System.out.println("TimeZone: " + getAsText(result, "time_zone"));
        System.out.println("ASN: " + getAsText(result, "asn"));
        System.out.println("AS: " + getAsText(result, "as"));
        System.out.println("ISP: " + getAsText(result, "isp"));
        System.out.println("Domain: " + getAsText(result, "domain"));
        System.out.println("NetSpeed: " + getAsText(result, "net_speed"));
        System.out.println("UsageType: " + getAsText(result, "usage_type"));
        System.out.println("AddressType: " + getAsText(result, "address_type"));
        System.out.println("AdsCategory: " + getAsText(result, "ads_category"));
        System.out.println("AdsCategoryName: " + getAsText(result, "ads_category_name"));
        System.out.println("IsProxy: " + getAsText(result, "is_proxy"));
        System.out.println("FraudScore: " + getAsText(result, "fraud_score"));
        System.out.println("ProxyType: " + getNestedAsText(result, "proxy", "proxy_type"));
        System.out.println("ProxyLastSeen: " + getNestedAsText(result, "proxy", "last_seen"));
        System.out.println("ProxyThreat: " + getNestedAsText(result, "proxy", "threat"));
        System.out.println("ProxyProvider: " + getNestedAsText(result, "proxy", "provider"));
        System.out.println("ProxyIsVpn: " + getNestedAsText(result, "proxy", "is_vpn"));
        System.out.println("ProxyIsTor: " + getNestedAsText(result, "proxy", "is_tor"));
        System.out.println("ProxyIsDataCenter: " + getNestedAsText(result, "proxy", "is_data_center"));
        System.out.println("ProxyIsPublicProxy: " + getNestedAsText(result, "proxy", "is_public_proxy"));
        System.out.println("ProxyIsResidentialProxy: " + getNestedAsText(result, "proxy", "is_residential_proxy"));
        System.out.println("AsDomain: " + getNestedAsText(result, "as_info", "as_domain"));
        System.out.println("AsUsageType: " + getNestedAsText(result, "as_info", "as_usage_type"));
        System.out.println("AsCIDR: " + getNestedAsText(result, "as_info", "as_cidr"));
    }

    private String getAsText(JsonObject result, String fieldName) {
        return result.has(fieldName) && !result.get(fieldName).isJsonNull()
                ? result.get(fieldName).getAsString()
                : "-";
    }

    private String getNestedAsText(JsonObject result, String objectName, String fieldName) {
        if (!result.has(objectName) || result.get(objectName).isJsonNull()) {
            return "-";
        }

        JsonObject nestedObject = result.getAsJsonObject(objectName);
        return nestedObject.has(fieldName) && !nestedObject.get(fieldName).isJsonNull()
                ? nestedObject.get(fieldName).getAsString()
                : "-";
    }
}
