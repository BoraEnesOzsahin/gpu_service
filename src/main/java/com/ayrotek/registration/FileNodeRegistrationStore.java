package com.ayrotek.registration;

import com.ayrotek.dto.InitialCommand;
import com.ayrotek.exception.NodeRegistrationStorageException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

@Component
public class FileNodeRegistrationStore implements NodeRegistrationStore {

    private static final Logger log = LoggerFactory.getLogger(FileNodeRegistrationStore.class);
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

    private final ObjectMapper objectMapper;
    private final Path storageDirectory = Path.of(System.getProperty("user.home"), ".gpu-service");
    private final Path dataFile = storageDirectory.resolve("node-registration.json");
    private final Path keyFile = storageDirectory.resolve("node-registration.key");

    private NodeRegistrationStatus status = NodeRegistrationStatus.NOT_INITIALIZED;
    private String nodeId;
    private String apiToken;
    private InitialCommand initialCommand;
    private Integer retryAfterSeconds;
    private String pendingMessage;

    public FileNodeRegistrationStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void load() {
        synchronized (this) {
            if (!Files.exists(dataFile)) {
                return;
            }
            try {
                StoredRegistration stored = objectMapper.readValue(dataFile.toFile(), StoredRegistration.class);
                status = stored.status() == null ? NodeRegistrationStatus.NOT_INITIALIZED : stored.status();
                nodeId = stored.nodeId();
                apiToken = stored.encryptedApiToken() == null ? null : decrypt(stored.encryptedApiToken());
                initialCommand = stored.initialCommand();
                retryAfterSeconds = stored.retryAfterSeconds();
                pendingMessage = stored.pendingMessage();
                log.info("Loaded persisted node registration status={}", status);
            } catch (Exception ex) {
                throw new NodeRegistrationStorageException("Could not load node registration state", ex);
            }
        }
    }

    @Override
    public synchronized void savePending(int retryAfterSeconds, String message) {
        status = NodeRegistrationStatus.PENDING_APPROVAL;
        nodeId = null;
        apiToken = null;
        initialCommand = null;
        this.retryAfterSeconds = retryAfterSeconds;
        pendingMessage = message;
        persist();
        log.info("Node registration status changed to PENDING_APPROVAL");
    }

    @Override
    public synchronized void saveActive(String nodeId, String apiToken, InitialCommand initialCommand) {
        status = NodeRegistrationStatus.ACTIVE;
        this.nodeId = nodeId;
        this.apiToken = apiToken;
        this.initialCommand = initialCommand;
        retryAfterSeconds = null;
        pendingMessage = null;
        persist();
        log.info("Node registration status changed to ACTIVE for nodeId={}", nodeId);
    }

    @Override public synchronized NodeRegistrationStatus getStatus() { return status; }
    @Override public synchronized Optional<String> getNodeId() { return Optional.ofNullable(nodeId); }
    @Override public synchronized Optional<String> getApiToken() { return Optional.ofNullable(apiToken); }
    @Override public synchronized Optional<InitialCommand> getInitialCommand() { return Optional.ofNullable(initialCommand); }
    @Override public synchronized Optional<Integer> getRetryAfterSeconds() { return Optional.ofNullable(retryAfterSeconds); }
    @Override public synchronized Optional<String> getPendingMessage() { return Optional.ofNullable(pendingMessage); }
    @Override public synchronized boolean isActive() { return status == NodeRegistrationStatus.ACTIVE; }

    private void persist() {
        try {
            Files.createDirectories(storageDirectory);
            StoredRegistration stored = new StoredRegistration(status, nodeId,
                    apiToken == null ? null : encrypt(apiToken), initialCommand, retryAfterSeconds, pendingMessage);
            objectMapper.writeValue(dataFile.toFile(), stored);
        } catch (Exception ex) {
            throw new NodeRegistrationStorageException("Could not persist node registration state", ex);
        }
    }

    private String encrypt(String value) throws Exception {
        byte[] iv = new byte[IV_LENGTH_BYTES];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, loadOrCreateKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
        byte[] encrypted = cipher.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] payload = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, payload, 0, iv.length);
        System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
        return Base64.getEncoder().encodeToString(payload);
    }

    private String decrypt(String encoded) throws Exception {
        byte[] payload = Base64.getDecoder().decode(encoded);
        byte[] iv = java.util.Arrays.copyOfRange(payload, 0, IV_LENGTH_BYTES);
        byte[] encrypted = java.util.Arrays.copyOfRange(payload, IV_LENGTH_BYTES, payload.length);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, loadOrCreateKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
        return new String(cipher.doFinal(encrypted), java.nio.charset.StandardCharsets.UTF_8);
    }

    private SecretKey loadOrCreateKey() throws Exception {
        Files.createDirectories(storageDirectory);
        if (Files.exists(keyFile)) {
            return new javax.crypto.spec.SecretKeySpec(Base64.getDecoder().decode(Files.readString(keyFile)), "AES");
        }
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        SecretKey key = generator.generateKey();
        Files.writeString(keyFile, Base64.getEncoder().encodeToString(key.getEncoded()));
        return key;
    }

    private record StoredRegistration(NodeRegistrationStatus status, String nodeId, String encryptedApiToken,
                                      InitialCommand initialCommand, Integer retryAfterSeconds, String pendingMessage) { }
}
