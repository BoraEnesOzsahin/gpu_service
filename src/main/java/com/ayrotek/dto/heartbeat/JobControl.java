package com.ayrotek.dto.heartbeat;

import com.fasterxml.jackson.annotation.JsonProperty;

public record JobControl(
        @JsonProperty("keep_current_job")
        Boolean keepCurrentJob
) {
}
