package com.ayrotek.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "device")
@Validated
public class DeviceProperties {

    @NotBlank
    private String model;

    @NotBlank
    private String firmwareVersion;

    @NotNull
    @PositiveOrZero
    private Integer systemPowerOverheadW;

    @NotNull
    @PositiveOrZero
    private Integer idlePowerW;

    // --- Manual Getters and Setters ---

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getFirmwareVersion() {
        return firmwareVersion;
    }

    public void setFirmwareVersion(String firmwareVersion) {
        this.firmwareVersion = firmwareVersion;
    }

    public Integer getSystemPowerOverheadW() {
        return systemPowerOverheadW;
    }

    public void setSystemPowerOverheadW(Integer systemPowerOverheadW) {
        this.systemPowerOverheadW = systemPowerOverheadW;
    }

    public Integer getIdlePowerW() {
        return idlePowerW;
    }

    public void setIdlePowerW(Integer idlePowerW) {
        this.idlePowerW = idlePowerW;
    }
}
