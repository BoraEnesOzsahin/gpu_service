package com.ayrotek.controller;

import com.ayrotek.dto.heartbeat.HeartbeatRequest;
import com.ayrotek.service.HeartbeatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/device")
@Tag(name = "Heartbeat", description = "Operations related to device heartbeat")
public class HeartbeatController {

    private final HeartbeatService heartbeatService;

    public HeartbeatController(HeartbeatService heartbeatService) {
        this.heartbeatService = heartbeatService;
    }

    @GetMapping("/heartbeat")
    @Operation(
            summary = "Build heartbeat request preview",
            description = "Builds and previews the heartbeat request from real system and GPU telemetry without sending it to EMS. Node initialization is not required for preview.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successfully built heartbeat preview")
            })
    public ResponseEntity<HeartbeatRequest> buildHeartbeatRequest() {
        return ResponseEntity.ok(heartbeatService.buildHeartbeatRequest());
    }

    @PostMapping("/heartbeat/send")
    @Operation(
            summary = "Send heartbeat to EMS",
            description = "Builds the heartbeat request and sends it to EMS using the node API token. The node must be initialized and active.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "EMS accepted the heartbeat; response forwarded unchanged"),
                    @ApiResponse(responseCode = "409", description = "Node is not initialized or not yet active"),
                    @ApiResponse(responseCode = "502", description = "Could not connect to EMS"),
                    @ApiResponse(responseCode = "504", description = "Connection to EMS timed out")
            })
    public ResponseEntity<Object> sendHeartbeat() {
        return heartbeatService.sendHeartbeat();
    }
}
