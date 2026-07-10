package com.ayrotek.service;

import com.ayrotek.config.DeviceProperties;
import org.springframework.stereotype.Service;

@Service
public class DeviceInfoService {

    private final DeviceProperties deviceProperties;

    public DeviceInfoService(DeviceProperties deviceProperties) {
        this.deviceProperties = deviceProperties;
    }

    public String getModel() {
        return deviceProperties.getModel();
    }

    public String getFirmwareVersion() {
        return deviceProperties.getFirmwareVersion();
    }
}
