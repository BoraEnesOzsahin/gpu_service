package com.ayrotek.service;

import org.springframework.http.ResponseEntity;

public interface InitializeSubmissionService {

    ResponseEntity<byte[]> sendInitializeRequest();
}
