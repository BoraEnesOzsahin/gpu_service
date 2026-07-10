package com.ayrotek.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum NodeStatus {
    WORKING("working"),
    IDLE("idle"),
    ERROR("error");

    private final String value;

    NodeStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
