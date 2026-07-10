package com.ayrotek.client;

import com.ayrotek.config.EmsProperties;
import com.ayrotek.dto.InitializeRequest;
import com.ayrotek.exception.EmsConnectionException;
import com.ayrotek.exception.EmsTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;

@Component
public class EmsInitializeClientImpl implements EmsInitializeClient {

    private static final Logger log = LoggerFactory.getLogger(EmsInitializeClientImpl.class);

    private final RestClient emsRestClient;
    private final EmsProperties emsProperties;

    public EmsInitializeClientImpl(RestClient emsRestClient, EmsProperties emsProperties) {
        this.emsRestClient = emsRestClient;
        this.emsProperties = emsProperties;
    }

    @Override
    public ResponseEntity<String> send(InitializeRequest initializeRequest) {
        String endpoint = emsProperties.getBaseUrl() + emsProperties.getInitializePath();
        log.info("Sending initialize request to EMS endpoint={}", endpoint);

        try {
            return emsRestClient.post()
                    .uri(emsProperties.getInitializePath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(initializeRequest)
                    .exchange((request, response) -> {
                        HttpHeaders headers = new HttpHeaders();
                        headers.putAll(response.getHeaders());
                        String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        return new ResponseEntity<>(body, headers, response.getStatusCode());
                    });
        } catch (ResourceAccessException ex) {
            if (isTimeout(ex)) {
                log.warn("Timed out while calling EMS endpoint={}: {}", endpoint, ex.getMessage());
                throw new EmsTimeoutException("Timed out while calling EMS", ex);
            }

            log.warn("Could not connect to EMS endpoint={}: {}", endpoint, ex.getMessage());
            throw new EmsConnectionException("Could not connect to EMS", ex);
        }
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException
                    || current instanceof InterruptedIOException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
