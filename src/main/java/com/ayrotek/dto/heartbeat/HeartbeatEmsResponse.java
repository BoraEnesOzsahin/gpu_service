package com.ayrotek.dto.heartbeat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the EMS response body after a successful heartbeat POST.
 * All fields are optional (wrapper types) as EMS may omit any of them.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HeartbeatEmsResponse(
        String command,

        @JsonProperty("setpoint_power_w")
        Integer setpointPowerW,

        @JsonProperty("next_heartbeat")
        Integer nextHeartbeat,

        @JsonProperty("job_control")
        JobControl jobControl
) {
}
