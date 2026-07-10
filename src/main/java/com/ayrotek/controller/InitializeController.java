package com.ayrotek.controller;

import com.ayrotek.dto.InitializeRequest;
import com.ayrotek.service.InitializeRequestService;
import com.ayrotek.service.InitializeSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/device")
@Tag(name = "Device Initialization", description = "Endpoints for device initialization")
public class InitializeController {

    private final InitializeRequestService initializeRequestService;
    private final InitializeSubmissionService initializeSubmissionService;

    public InitializeController(InitializeRequestService initializeRequestService,
                                InitializeSubmissionService initializeSubmissionService) {
        this.initializeRequestService = initializeRequestService;
        this.initializeSubmissionService = initializeSubmissionService;
    }

    @Operation(
        summary = "Build initialize request",
        description = "Builds and returns the initialize request without sending it to EMS.",
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

    @Operation(
        summary = "Send initialize request to EMS",
        description = "Builds the initialize request from real device information, sends it to EMS and returns the EMS response unchanged.",
        responses = {
            @ApiResponse(responseCode = "200", description = "EMS response forwarded unchanged."),
            @ApiResponse(responseCode = "201", description = "EMS response forwarded unchanged."),
            @ApiResponse(responseCode = "400", description = "EMS response forwarded unchanged."),
            @ApiResponse(responseCode = "409", description = "EMS response forwarded unchanged."),
            @ApiResponse(responseCode = "500", description = "Local request creation failure or EMS response forwarded unchanged."),
            @ApiResponse(responseCode = "502", description = "EMS could not be reached."),
            @ApiResponse(responseCode = "504", description = "EMS connection or read timed out.")
        }
    )
    @PostMapping("/initialize/send")
    public ResponseEntity<String> sendInitializeRequest() {
        return initializeSubmissionService.sendInitializeRequest();
    }
}
