package com.example.ShoppingSystem.quota;

public interface Ip2LocationBinCountryService {
    public String queryCountryCode(String ip);

    public IpGeoSnapshot queryGeo(String ip);

    public void close();
}
