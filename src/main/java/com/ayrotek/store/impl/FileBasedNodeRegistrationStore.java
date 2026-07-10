package com.ayrotek.store.impl;

import com.ayrotek.dto.InitialCommand;
import com.ayrotek.model.NodeRegistrationStatus;
import com.ayrotek.store.NodeRegistrationStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

@Component
public class FileBasedNodeRegistrationStore implements NodeRegistrationStore {

    private static final Logger log = LoggerFactory.getLogger(FileBasedNodeRegistrationStore.class);
    private static final String STORE_FILE = "node-registration.json";

    private final ObjectMapper objectMapper;
    private NodeRegistrationData data;

    public FileBasedNodeRegistrationStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.data = loadData();
    }

    @Override
    public void savePending(int retryAfterSeconds, String message) {
        data = new NodeRegistrationData();
        data.setStatus(NodeRegistrationStatus.PENDING_APPROVAL);
        data.setRetryAfterSeconds(retryAfterSeconds);
        data.setPendingMessage(message);
        saveData();
        log.info("Node registration status updated to PENDING_APPROVAL. Message: {}, Retry after: {}s", message, retryAfterSeconds);
    }

    @Override
    public void saveActive(String nodeId, String apiToken, InitialCommand initialCommand) {
        data = new NodeRegistrationData();
        data.setStatus(NodeRegistrationStatus.ACTIVE);
        data.setNodeId(nodeId);
        data.setApiToken(apiToken); // In a real scenario, this should be encrypted
        data.setInitialCommand(initialCommand);
        saveData();
        log.info("Node registration status updated to ACTIVE. Node ID: {}", nodeId);
    }

    @Override
    public NodeRegistrationStatus getStatus() {
        return data.getStatus();
    }

    @Override
    public Optional<String> getNodeId() {
        return Optional.ofNullable(data.getNodeId());
    }

@Override
    public Optional<String> getApiToken() {
        return Optional.ofNullable(data.getApiToken());
    }

    @Override
    public Optional<InitialCommand> getInitialCommand() {
        return Optional.ofNullable(data.getInitialCommand());
    }

    @Override
    public Optional<Integer> getRetryAfterSeconds() {
        return Optional.ofNullable(data.getRetryAfterSeconds());
    }

    @Override
    public Optional<String> getPendingMessage() {
        return Optional.ofNullable(data.getPendingMessage());
    }

    @Override
    public boolean isActive() {
        return getStatus() == NodeRegistrationStatus.ACTIVE && getNodeId().isPresent() && getApiToken().isPresent();
    }

    private NodeRegistrationData loadData() {
        File file = new File(STORE_FILE);
        if (file.exists()) {
            try {
                return objectMapper.readValue(file, NodeRegistrationData.class);
            } catch (IOException e) {
                log.error("Failed to load node registration data from {}", STORE_FILE, e);
            }
        }
        NodeRegistrationData defaultData = new NodeRegistrationData();
        defaultData.setStatus(NodeRegistrationStatus.NOT_INITIALIZED);
        return defaultData;
    }

    private void saveData() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(STORE_FILE), data);
        } catch (IOException e) {
            log.error("Failed to save node registration data to {}", STORE_FILE, e);
        }
    }

    // Inner class for data structure
    private static class NodeRegistrationData {
        private NodeRegistrationStatus status;
        private String nodeId;
        private String apiToken;
        private InitialCommand initialCommand;
        private Integer retryAfterSeconds;
        private String pendingMessage;

        // Getters and setters
        public NodeRegistrationStatus getStatus() { return status; }
        public void setStatus(NodeRegistrationStatus status) { this.status = status; }
        public String getNodeId() { return nodeId; }
        public void setNodeId(String nodeId) { this.nodeId = nodeId; }
        public String getApiToken() { return apiToken; }
        public void setApiToken(String apiToken) { this.apiToken = apiToken; }
        public InitialCommand getInitialCommand() { return initialCommand; }
        public void setInitialCommand(InitialCommand initialCommand) { this.initialCommand = initialCommand; }
        public Integer getRetryAfterSeconds() { return retryAfterSeconds; }
        public void setRetryAfterSeconds(Integer retryAfterSeconds) { this.retryAfterSeconds = retryAfterSeconds; }
        public String getPendingMessage() { return pendingMessage; }
        public void setPendingMessage(String pendingMessage) { this.pendingMessage = pendingMessage; }
    }
}
