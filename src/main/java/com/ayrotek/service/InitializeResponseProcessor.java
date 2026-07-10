package com.ayrotek.service;

import com.ayrotek.dto.EmsInitializeResponse;
import com.ayrotek.store.NodeRegistrationStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class InitializeResponseProcessor {

    private static final Logger log = LoggerFactory.getLogger(InitializeResponseProcessor.class);

    private final NodeRegistrationStore nodeRegistrationStore;
    private final ObjectMapper objectMapper;

    public InitializeResponseProcessor(NodeRegistrationStore nodeRegistrationStore, ObjectMapper objectMapper) {
        this.nodeRegistrationStore = nodeRegistrationStore;
        this.objectMapper = objectMapper;
    }

    public void process(ResponseEntity<String> emsResponse) {
        String responseBody = emsResponse.getBody();
        int statusCode = emsResponse.getStatusCode().value();

        if (responseBody == null || responseBody.isEmpty()) {
            log.error("EMS response body is empty or null. Status code: {}", statusCode);
            return;
        }

        try {
            EmsInitializeResponse response = objectMapper.readValue(responseBody, EmsInitializeResponse.class);

            if (statusCode == 202 && "pending_approval".equals(response.status())) {
                handlePendingApproval(response);
            } else if (statusCode == 200 && "active".equals(response.status())) {
                handleActive(response);
            } else {
                log.error("Unexpected EMS response. Status code: {}, Body: {}", statusCode, responseBody);
            }
        } catch (IOException e) {
            log.error("Failed to parse EMS initialize response. Body: {}", responseBody, e);
        }
    }

    private void handlePendingApproval(EmsInitializeResponse response) {
        if (response.retryAfter() == null || response.message() == null) {
            log.error("Invalid pending_approval response: missing retry_after or message. Response: {}", response);
            return;
        }
        nodeRegistrationStore.savePending(response.retryAfter(), response.message());
    }

    private void handleActive(EmsInitializeResponse response) {
        if (response.nodeId() == null || response.apiToken() == null || response.initialCommand() == null) {
            log.error("Invalid active response: missing nodeId, apiToken, or initialCommand. Response: {}", response);
            return;
        }
        nodeRegistrationStore.saveActive(response.nodeId(), response.apiToken(), response.initialCommand());
    }
}
