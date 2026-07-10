package com.ayrotek.client;

import com.ayrotek.dto.InitializeRequest;
import org.springframework.http.ResponseEntity;

public interface EmsInitializeClient {

    ResponseEntity<String> send(InitializeRequest initializeRequest);
}
