package com.ayrotek.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public class Capabilities {

    @NotNull
    @PositiveOrZero
    @JsonProperty("max_power_w")
    private Integer maxPowerW;

    @NotNull
    @PositiveOrZero
    @JsonProperty("min_power_w")
    private Integer minPowerW;

    // --- Constructors, Getters, and Setters ---

    public Capabilities() {
    }

    public Capabilities(Integer maxPowerW, Integer minPowerW) {
        this.maxPowerW = maxPowerW;
        this.minPowerW = minPowerW;
    }

    public Integer getMaxPowerW() {
        return maxPowerW;
    }

    public void setMaxPowerW(Integer maxPowerW) {
        this.maxPowerW = maxPowerW;
    }

    public Integer getMinPowerW() {
        return minPowerW;
    }

    public void setMinPowerW(Integer minPowerW) {
        this.minPowerW = minPowerW;
    }
}
