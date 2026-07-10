package com.ayrotek.service;

import com.ayrotek.dto.Capabilities;
import com.ayrotek.dto.GpuInventory;
import com.ayrotek.dto.InitializeRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InitializeRequestService {

    private final HardwareIdService hardwareIdService;
    private final DeviceInfoService deviceInfoService;
    private final GpuInventoryService gpuInventoryService;
    private final DeviceCapabilitiesService deviceCapabilitiesService;

    public InitializeRequestService(HardwareIdService hardwareIdService,
                                    DeviceInfoService deviceInfoService,
                                    GpuInventoryService gpuInventoryService,
                                    DeviceCapabilitiesService deviceCapabilitiesService) {
        this.hardwareIdService = hardwareIdService;
        this.deviceInfoService = deviceInfoService;
        this.gpuInventoryService = gpuInventoryService;
        this.deviceCapabilitiesService = deviceCapabilitiesService;
    }

    public InitializeRequest buildInitializeRequest() {
        // 1. Get hardware ID, model, and firmware version
        String hardwareId = hardwareIdService.getHardwareId();
        String model = deviceInfoService.getModel();
        String fwVersion = deviceInfoService.getFirmwareVersion();

        // 2. Detect all GPUs from all available providers
        List<GpuInventory> gpuInventory = gpuInventoryService.detectAllGpus();

        // 3. Calculate device capabilities based on the detected GPUs
        Capabilities capabilities = deviceCapabilitiesService.calculateCapabilities(gpuInventory);

        // 4. Assemble the final request object
        InitializeRequest request = new InitializeRequest();
        request.setHardwareId(hardwareId);
        request.setModel(model);
        request.setFwVersion(fwVersion);
        request.setGpuInventory(gpuInventory);
        request.setCapabilities(capabilities);

        return request;
    }
}
