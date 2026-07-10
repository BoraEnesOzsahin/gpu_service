package com.ayrotek.service;

import com.ayrotek.client.EmsInitializeClient;
import com.ayrotek.dto.InitializeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class InitializeSubmissionServiceImpl implements InitializeSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(InitializeSubmissionServiceImpl.class);
    private static final List<String> FORWARDED_HEADERS = List.of(
            HttpHeaders.CONTENT_TYPE,
            HttpHeaders.LOCATION
    );

    private final InitializeRequestService initializeRequestService;
    private final EmsInitializeClient emsInitializeClient;
    private final InitializeResponseProcessor initializeResponseProcessor;

    public InitializeSubmissionServiceImpl(InitializeRequestService initializeRequestService,
                                           EmsInitializeClient emsInitializeClient,
                                           InitializeResponseProcessor initializeResponseProcessor) {
        this.initializeRequestService = initializeRequestService;
        this.emsInitializeClient = emsInitializeClient;
        this.initializeResponseProcessor = initializeResponseProcessor;
    }

    @Override
    public ResponseEntity<String> sendInitializeRequest() {
        log.info("Starting initialize request submission to EMS");

        InitializeRequest initializeRequest = initializeRequestService.buildInitializeRequest();
        int gpuCount = initializeRequest.getGpuInventory() == null ? 0 : initializeRequest.getGpuInventory().size();
        log.info("Submitting initialize request for hardwareId={} with gpuCount={}",
                initializeRequest.getHardwareId(), gpuCount);

        ResponseEntity<String> emsResponse = emsInitializeClient.send(initializeRequest);

        if (Objects.nonNull(emsResponse)) {
            log.info("EMS returned status={}", emsResponse.getStatusCode());
            initializeResponseProcessor.process(emsResponse);

            return ResponseEntity.status(emsResponse.getStatusCode())
                    .headers(safeResponseHeaders(emsResponse.getHeaders()))
                    .body(emsResponse.getBody());
        }

        return ResponseEntity.internalServerError().build();
    }

    private HttpHeaders safeResponseHeaders(HttpHeaders sourceHeaders) {
        HttpHeaders forwardedHeaders = new HttpHeaders();
        FORWARDED_HEADERS.forEach(headerName -> {
            List<String> values = sourceHeaders.get(headerName);
            if (values != null) {
                forwardedHeaders.put(headerName, List.copyOf(values));
            }
        });
        return forwardedHeaders;
    }
}
