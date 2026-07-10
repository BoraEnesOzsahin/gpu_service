package com.ayrotek.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "ems")
public class EmsProperties {

    @NotBlank
    private String baseUrl;

    @NotBlank
    private String initializePath;

    @NotBlank
    private String heartbeatPath;

    @NotNull
    private Duration connectTimeout;

    @NotNull
    private Duration readTimeout;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getInitializePath() {
        return initializePath;
    }

    public void setInitializePath(String initializePath) {
        this.initializePath = initializePath;
    }

    public String getHeartbeatPath() {
        return heartbeatPath;
    }

    public void setHeartbeatPath(String heartbeatPath) {
        this.heartbeatPath = heartbeatPath;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}
