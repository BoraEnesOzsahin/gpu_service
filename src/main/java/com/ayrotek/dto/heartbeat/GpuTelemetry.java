package com.ayrotek.dto.heartbeat;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GpuTelemetry(
        @JsonProperty("gpu_id")
        String gpuId,

        @JsonProperty("load_pct")
        Double loadPct,

        @JsonProperty("temp_c")
        Double tempC,

        @JsonProperty("power_draw_w")
        Double powerDrawW,

        @JsonProperty("current_performance")
        CurrentPerformance currentPerformance
) {
}
