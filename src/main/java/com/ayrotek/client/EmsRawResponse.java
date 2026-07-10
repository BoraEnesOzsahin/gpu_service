package com.ayrotek.client;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;

public record EmsRawResponse(
        HttpStatusCode statusCode,
        HttpHeaders headers,
        byte[] body
) {
}
