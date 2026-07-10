package com.ayrotek.service;

import com.ayrotek.config.DeviceProperties;
import com.ayrotek.dto.Capabilities;
import com.ayrotek.dto.GpuInventory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DeviceCapabilitiesService {

    private static final Logger log = LoggerFactory.getLogger(DeviceCapabilitiesService.class);
    private final DeviceProperties deviceProperties;

    public DeviceCapabilitiesService(DeviceProperties deviceProperties) {
        this.deviceProperties = deviceProperties;
    }

    public Capabilities calculateCapabilities(List<GpuInventory> gpuInventory) {
        int maxPower = calculateMaxPower(gpuInventory);
        int minPower = determineMinPower();
        return new Capabilities(maxPower, minPower);
    }

    private int calculateMaxPower(List<GpuInventory> gpuInventory) {
        int totalGpuTdp = gpuInventory.stream()
            .mapToInt(gpu -> {
                if (gpu.getTdpW() == null || gpu.getTdpW() <= 0) {
                    log.warn("GPU {} ({}) has a missing or invalid TDP value. It will not be included in max_power_w calculation.",
                        gpu.getGpuId(), gpu.getName());
                    return 0;
                }
                return gpu.getTdpW();
            })
            .sum();

        Integer systemOverhead = deviceProperties.getSystemPowerOverheadW();
        int maxPower = totalGpuTdp + systemOverhead;
        
        log.info("Calculated max_power_w: {} W ({} W from GPUs + {} W system overhead)", maxPower, totalGpuTdp, systemOverhead);
        if (totalGpuTdp == 0 && !gpuInventory.isEmpty()) {
            log.warn("max_power_w may be inaccurate as no valid GPU TDP values were found.");
        }

        return maxPower;
    }

    private int determineMinPower() {
        // Per requirements, real measurement is not implemented in this phase.
        // We will use the configuration value.
        Integer idlePowerFromConfig = deviceProperties.getIdlePowerW();
        log.info("Using configured value for min_power_w (idle power): {} W. Real-time measurement is not implemented.", idlePowerFromConfig);
        return idlePowerFromConfig;
    }
}
