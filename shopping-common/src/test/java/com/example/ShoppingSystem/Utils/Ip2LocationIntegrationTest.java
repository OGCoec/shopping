package com.example.ShoppingSystem.Utils;

import com.ip2location.IP2Location;
import com.ip2location.IPResult;
import org.junit.jupiter.api.Test;

import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ip2LocationIntegrationTest {

    static {
        try {
            System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    private static final Path DB11_BIN_PATH = Path.of("C:/Users/damn/Desktop/IP2LOCATION-LITE-DB11.IPV6.BIN/IP2LOCATION-LITE-DB11.IPV6.BIN");

    @Test
    void shouldQueryDb11BinForIpv4Address() throws Exception {
        assertTrue(Files.exists(DB11_BIN_PATH), "DB11 BIN 文件必须存在");
        String testIp = fetchPublicIp();

        IP2Location ip2Location = new IP2Location();
        try {
            ip2Location.Open(DB11_BIN_PATH.toString(), true);
            IPResult result = ip2Location.IPQuery(testIp);

            assertNotNull(result, "DB11 查询结果不能为空");
            assertEquals("OK", result.getStatus(), "DB11 查询状态必须为 OK");
            assertNotNull(result.getCountryShort(), "国家简码不能为空");
            assertTrue(!result.getCountryShort().isBlank(), "国家简码不能为空字符串");

            printDb11Result(testIp, result);
        } finally {
            ip2Location.Close();
        }
    }

    private String fetchPublicIp() throws Exception {
        HttpClient httpClient = HttpClient.newHttpClient();
        String[] candidateUrls = {
                "https://api.ipify.org",
                "https://ipv4.icanhazip.com"
        };

        for (String candidateUrl : candidateUrls) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(candidateUrl))
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                String ip = response.body() == null ? "" : response.body().trim();
                if (!ip.isBlank()) {
                    return ip;
                }
            } catch (Exception ignored) {
            }
        }

        throw new IllegalStateException("无法动态获取本机公网 IP");
    }

    private void printDb11Result(String testIp, IPResult result) {
        System.out.println("==== DB11 查询结果 ====");
        System.out.println("IP: " + testIp);
        System.out.println("Status: " + result.getStatus());
        System.out.println("CountryShort: " + result.getCountryShort());
        System.out.println("CountryLong: " + result.getCountryLong());
        System.out.println("Region: " + result.getRegion());
        System.out.println("City: " + result.getCity());
        System.out.println("District: " + result.getDistrict());
        System.out.println("Latitude: " + result.getLatitude());
        System.out.println("Longitude: " + result.getLongitude());
        System.out.println("ISP: " + result.getISP());
        System.out.println("Domain: " + result.getDomain());
        System.out.println("ZipCode: " + result.getZipCode());
        System.out.println("TimeZone: " + result.getTimeZone());
        System.out.println("NetSpeed: " + result.getNetSpeed());
        System.out.println("UsageType: " + result.getUsageType());
        System.out.println("AddressType: " + result.getAddressType());
        System.out.println("Category: " + result.getCategory());
        System.out.println("ASN: " + result.getASN());
        System.out.println("AS: " + result.getAS());
        System.out.println("ASDomain: " + result.getASDomain());
        System.out.println("ASUsageType: " + result.getASUsageType());
        System.out.println("ASCIDR: " + result.getASCIDR());
    }
}
