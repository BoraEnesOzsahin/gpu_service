package com.ayrotek.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class ComputeCapability {

    @NotNull
    @Positive
    private Integer value;

    @NotBlank
    private String unit;

    // --- Constructors, Getters, and Setters ---

    public ComputeCapability() {
    }

    public ComputeCapability(Integer value, String unit) {
        this.value = value;
        this.unit = unit;
    }

    public Integer getValue() {
        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }
}
