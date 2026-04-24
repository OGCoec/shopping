package com.example.ShoppingSystem.Utils;

import com.ip2location.IP2Location;
import com.ip2location.IPResult;
import org.junit.jupiter.api.Test;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ip2LocationAsnFixedIpIntegrationTest {

    static {
        try {
            System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    private static final String TEST_IP = "13.212.179.96";
    private static final Path ASN_BIN_PATH = Path
            .of("C:/Users/damn/Desktop/IP2LOCATION-LITE-ASN.IPV6.BIN/IP2LOCATION-LITE-ASN.IPV6.BIN");

    @Test
    void shouldQueryAsnBinForFixedIp() throws Exception {
        assertTrue(Files.exists(ASN_BIN_PATH), "ASN BIN 文件必须存在");

        IP2Location ip2Location = new IP2Location();
        try {
            ip2Location.Open(ASN_BIN_PATH.toString(), true);
            IPResult result = ip2Location.IPQuery(TEST_IP);

            assertNotNull(result, "ASN BIN 查询结果不能为空");
            assertEquals("OK", result.getStatus(), "ASN BIN 查询状态必须为 OK");
            assertNotNull(result.getASN(), "ASN 不能为空");
            assertTrue(!result.getASN().isBlank(), "ASN 不能为空字符串");
            assertNotNull(result.getAS(), "AS 不能为空");
            assertTrue(!result.getAS().isBlank(), "AS 不能为空字符串");

            printAsnResult(result);
        } finally {
            ip2Location.Close();
        }
    }

    private void printAsnResult(IPResult result) {
        System.out.println("==== ASN Fixed IP Result ====");
        System.out.println("IP: " + TEST_IP);
        System.out.println("Status: " + result.getStatus());
        System.out.println("CountryShort: " + result.getCountryShort());
        System.out.println("CountryLong: " + result.getCountryLong());
        System.out.println("ISP: " + result.getISP());
        System.out.println("Domain: " + result.getDomain());
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
