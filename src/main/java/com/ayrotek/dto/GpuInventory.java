package com.ayrotek.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class GpuInventory {

    @NotBlank
    @JsonProperty("gpu_id")
    private String gpuId;

    @NotBlank
    private String name;

    @NotNull
    @Positive
    @JsonProperty("tdp_w")
    private Integer tdpW;

    @NotNull
    @Valid
    @JsonProperty("compute_capability")
    private ComputeCapability computeCapability;

    // --- Constructors, Getters, and Setters ---

    public GpuInventory() {
    }

    public GpuInventory(String gpuId, String name, Integer tdpW, ComputeCapability computeCapability) {
        this.gpuId = gpuId;
        this.name = name;
        this.tdpW = tdpW;
        this.computeCapability = computeCapability;
    }

    public String getGpuId() {
        return gpuId;
    }

    public void setGpuId(String gpuId) {
        this.gpuId = gpuId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getTdpW() {
        return tdpW;
    }

    public void setTdpW(Integer tdpW) {
        this.tdpW = tdpW;
    }

    public ComputeCapability getComputeCapability() {
        return computeCapability;
    }

    public void setComputeCapability(ComputeCapability computeCapability) {
        this.computeCapability = computeCapability;
    }
}
