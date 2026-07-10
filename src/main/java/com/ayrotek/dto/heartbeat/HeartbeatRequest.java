package com.ayrotek.dto.heartbeat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HeartbeatRequest(
        @JsonProperty("node_id")
        String nodeId,

        Instant timestamp,
        SystemMetrics metrics,

        @JsonProperty("gpu_telemetry")
        List<GpuTelemetry> gpuTelemetry
) {
}
