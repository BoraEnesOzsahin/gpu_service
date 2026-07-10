package com.ayrotek.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class InitializeRequest {

    @NotBlank
    @JsonProperty("hardware_id")
    private String hardwareId;

    @NotBlank
    private String model;

    @NotBlank
    @JsonProperty("fw_version")
    private String fwVersion;

    @NotNull
    @Valid
    private Capabilities capabilities;

    @NotEmpty
    @Valid
    @JsonProperty("gpu_inventory")
    private List<GpuInventory> gpuInventory;

    // --- Constructors, Getters, and Setters ---

    public InitializeRequest() {
    }

    public InitializeRequest(String hardwareId, String model, String fwVersion, Capabilities capabilities, List<GpuInventory> gpuInventory) {
        this.hardwareId = hardwareId;
        this.model = model;
        this.fwVersion = fwVersion;
        this.capabilities = capabilities;
        this.gpuInventory = gpuInventory;
    }

    public String getHardwareId() {
        return hardwareId;
    }

    public void setHardwareId(String hardwareId) {
        this.hardwareId = hardwareId;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getFwVersion() {
        return fwVersion;
    }

    public void setFwVersion(String fwVersion) {
        this.fwVersion = fwVersion;
    }

    public Capabilities getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(Capabilities capabilities) {
        this.capabilities = capabilities;
    }

    public List<GpuInventory> getGpuInventory() {
        return gpuInventory;
    }

    public void setGpuInventory(List<GpuInventory> gpuInventory) {
        this.gpuInventory = gpuInventory;
    }
}
