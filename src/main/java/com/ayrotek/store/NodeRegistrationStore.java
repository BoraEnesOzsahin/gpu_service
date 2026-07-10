package com.ayrotek.store;

import com.ayrotek.dto.InitialCommand;
import com.ayrotek.model.NodeRegistrationStatus;

import java.util.Optional;

public interface NodeRegistrationStore {

    void savePending(
            int retryAfterSeconds,
            String message
    );

    void saveActive(
            String nodeId,
            String apiToken,
            InitialCommand initialCommand
    );

    NodeRegistrationStatus getStatus();

    Optional<String> getNodeId();

    Optional<String> getApiToken();

    Optional<InitialCommand> getInitialCommand();

    Optional<Integer> getRetryAfterSeconds();

    Optional<String> getPendingMessage();

    boolean isActive();
}
