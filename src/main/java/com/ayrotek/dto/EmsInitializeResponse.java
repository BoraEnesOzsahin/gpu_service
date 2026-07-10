package com.ayrotek.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EmsInitializeResponse(
        String status,
        String message,

        @JsonProperty("retry_after")
        Integer retryAfter,

        @JsonProperty("node_id")
        String nodeId,

        @JsonProperty("api_token")
        String apiToken,

        @JsonProperty("initial_command")
        InitialCommand initialCommand
) {
}
