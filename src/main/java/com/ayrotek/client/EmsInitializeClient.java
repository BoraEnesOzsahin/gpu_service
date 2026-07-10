package com.ayrotek.client;

import com.ayrotek.dto.InitializeRequest;

public interface EmsInitializeClient {

    EmsRawResponse send(InitializeRequest initializeRequest);
}
