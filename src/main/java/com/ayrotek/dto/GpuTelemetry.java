package com.ayrotek.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public class GpuTelemetry {

    @NotBlank
    @JsonProperty("gpu_id")
    private String gpuId;

    @NotNull
    @PositiveOrZero
    @JsonProperty("power_draw_w")
    private Double powerDrawW;

    @NotNull
    @JsonProperty("temp_c")
    private Integer tempC;

    @NotNull
    @PositiveOrZero
    @JsonProperty("load_pct")
    private Integer loadPct;

    // --- Constructors, Getters, and Setters ---

    public GpuTelemetry() {
    }

    public GpuTelemetry(String gpuId, Double powerDrawW, Integer tempC, Integer loadPct) {
        this.gpuId = gpuId;
        this.powerDrawW = powerDrawW;
        this.tempC = tempC;
        this.loadPct = loadPct;
    }

    public String getGpuId() {
        return gpuId;
    }

    public void setGpuId(String gpuId) {
        this.gpuId = gpuId;
    }

    public Double getPowerDrawW() {
        return powerDrawW;
    }

    public void setPowerDrawW(Double powerDrawW) {
        this.powerDrawW = powerDrawW;
    }

    public Integer getTempC() {
        return tempC;
    }

    public void setTempC(Integer tempC) {
        this.tempC = tempC;
    }

    public Integer getLoadPct() {
        return loadPct;
    }

    public void setLoadPct(Integer loadPct) {
        this.loadPct = loadPct;
    }
}
