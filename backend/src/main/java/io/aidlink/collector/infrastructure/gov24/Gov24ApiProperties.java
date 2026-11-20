package io.aidlink.collector.infrastructure.gov24;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aidlink.collector.gov24")
public class Gov24ApiProperties {

    private String baseUrl = "https://api.odcloud.kr/api/gov24/v3";
    private String serviceKey = "";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getServiceKey() {
        return serviceKey;
    }

    public void setServiceKey(String serviceKey) {
        this.serviceKey = serviceKey;
    }
}
