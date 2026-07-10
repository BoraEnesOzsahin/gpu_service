package com.ayrotek.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InitialCommand(
        @JsonProperty("target_power_w")
        Integer targetPowerW,

        @JsonProperty("heartbeat_interval")
        Integer heartbeatInterval
) {
}
