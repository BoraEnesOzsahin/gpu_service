package com.ayrotek.controller;

import com.ayrotek.dto.InitializeRequest;
import com.ayrotek.service.InitializeRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/device")
@Tag(name = "Device Initialization", description = "Endpoints for device initialization")
public class InitializeController {

    private final InitializeRequestService initializeRequestService;

    public InitializeController(InitializeRequestService initializeRequestService) {
        this.initializeRequestService = initializeRequestService;
    }

    @Operation(
        summary = "Build initialize request",
        description = "Builds the initialize request from real device and GPU information without sending it to an external service. The response contains the complete hardware and GPU inventory.",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Successfully built the initialize request.",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = InitializeRequest.class))
            ),
            @ApiResponse(
                responseCode = "500",
                description = "Internal Server Error. Could not build the request, e.g., if the MAC address cannot be found.",
                content = @Content(mediaType = "application/json")
            )
        }
    )
    @GetMapping("/initialize")
    public ResponseEntity<InitializeRequest> initialize() {
        InitializeRequest initializeRequest = initializeRequestService.buildInitializeRequest();
        return ResponseEntity.ok(initializeRequest);
    }
}
