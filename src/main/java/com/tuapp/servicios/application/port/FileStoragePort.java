package com.tuapp.servicios.application.port;

import java.time.Duration;

public interface FileStoragePort {
    String upload(String objectKey, byte[] content, String contentType);
    String generatePresignedUrl(String objectKey, Duration expiry);
    void delete(String objectKey);
}
