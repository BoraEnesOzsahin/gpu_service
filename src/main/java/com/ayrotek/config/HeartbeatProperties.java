package com.ayrotek.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "heartbeat")
public class HeartbeatProperties {

    private int workingLoadThresholdPct = 10;
    private Duration commandTimeout = Duration.ofSeconds(5);

    public int getWorkingLoadThresholdPct() {
        return workingLoadThresholdPct;
    }

    public void setWorkingLoadThresholdPct(int workingLoadThresholdPct) {
        this.workingLoadThresholdPct = workingLoadThresholdPct;
    }

    public Duration getCommandTimeout() {
        return commandTimeout;
    }

    public void setCommandTimeout(Duration commandTimeout) {
        this.commandTimeout = commandTimeout;
    }
}
