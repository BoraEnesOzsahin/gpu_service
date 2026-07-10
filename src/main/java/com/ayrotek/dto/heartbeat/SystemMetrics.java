package com.ayrotek.dto.heartbeat;

import com.ayrotek.model.NodeStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

public record SystemMetrics(
        @JsonProperty("system_temp_c")
        Double systemTempC,

        NodeStatus status
) {
}
