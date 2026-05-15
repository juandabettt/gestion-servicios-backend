package com.tuapp.servicios.infrastructure.adapter.storage;

import com.tuapp.servicios.application.port.FileStoragePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.time.Duration;
import org.springframework.context.annotation.Lazy;

@Component
@Profile("local")
@Lazy
@Slf4j
public class MinIOFileStorageAdapter implements FileStoragePort {

    @Value("${storage.endpoint}")
    private String endpoint;
    @Value("${storage.access-key}")
    private String accessKey;
    @Value("${storage.secret-key}")
    private String secretKey;
    @Value("${storage.bucket-name}")
    private String bucketName;
    @Value("${storage.region}")
    private String region;

    private S3Client s3Client;
    private S3Presigner presigner;

    @PostConstruct
    public void init() {
        try {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
            StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(credentials);
            Region awsRegion = Region.of(region);

            this.s3Client = S3Client.builder()
                    .endpointOverride(URI.create(endpoint))
                    .credentialsProvider(credentialsProvider)
                    .region(awsRegion)
                    .forcePathStyle(true)
                    .build();

            this.presigner = S3Presigner.builder()
                    .endpointOverride(URI.create(endpoint))
                    .credentialsProvider(credentialsProvider)
                    .region(awsRegion)
                    .build();

            ensureBucketExists();
            log.info("MinIO initialized successfully at {}", endpoint);
        } catch (Exception e) {
            log.warn("MinIO initialization failed (expected in local without proper setup): {}", e.getMessage());
            log.debug("MinIO error details", e);
        }
    }

    private void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
            log.info("Bucket '{}' creado en MinIO", bucketName);
        }
    }

    @Override
    public String upload(String objectKey, byte[] content, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(objectKey)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(content));
        log.info("Archivo subido a MinIO: {}", objectKey);
        return objectKey;
    }

    @Override
    public String generatePresignedUrl(String objectKey, Duration expiry) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiry)
                .getObjectRequest(r -> r.bucket(bucketName).key(objectKey))
                .build();
        return presigner.presignGetObject(presignRequest).url().toString();
    }

    @Override
    public void delete(String objectKey) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(objectKey).build());
        log.info("Archivo eliminado de MinIO: {}", objectKey);
    }
}
