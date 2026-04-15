package com.tuapp.servicios.infrastructure.adapter.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.tuapp.servicios.application.port.FileStoragePort;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

@Component
@Profile("production")
@Slf4j
public class CloudinaryFileStorageAdapter implements FileStoragePort {

    @Value("${cloudinary.cloud-name}")
    private String cloudName;

    @Value("${cloudinary.api-key}")
    private String apiKey;

    @Value("${cloudinary.api-secret}")
    private String apiSecret;

    private Cloudinary cloudinary;

    @PostConstruct
    public void init() {
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true
        ));
        log.info("Cloudinary storage adapter inicializado correctamente");
    }

    @Override
    public String upload(String objectKey, byte[] content, String contentType) {
        try {
            String publicId = objectKey.replace("/", "_").replace(".", "_");
            Map uploadResult = cloudinary.uploader().upload(content,
                    ObjectUtils.asMap(
                            "public_id", publicId,
                            "resource_type", "image",
                            "folder", "facturas"
                    ));
            String url = (String) uploadResult.get("secure_url");
            log.info("Imagen subida a Cloudinary exitosamente");
            return url;
        } catch (IOException e) {
            log.error("Error subiendo imagen a Cloudinary");
            throw new RuntimeException("Error al subir imagen a Cloudinary", e);
        }
    }

    @Override
    public String generatePresignedUrl(String objectKey, Duration expiry) {
        // Cloudinary devuelve URLs publicas directamente
        // Si el objectKey ya es una URL de Cloudinary la retornamos tal cual
        if (objectKey != null && objectKey.startsWith("https://res.cloudinary.com")) {
            return objectKey;
        }
        // Si es un public_id generar la URL
        try {
            return cloudinary.url()
                    .secure(true)
                    .resourceType("image")
                    .generate(objectKey);
        } catch (Exception e) {
            log.error("Error generando URL de Cloudinary");
            return objectKey;
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            String publicId = objectKey;
            if (objectKey.startsWith("https://res.cloudinary.com")) {
                // Extraer public_id de la URL
                String[] parts = objectKey.split("/");
                publicId = parts[parts.length - 1].split("\\.")[0];
            }
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            log.info("Imagen eliminada de Cloudinary");
        } catch (IOException e) {
            log.error("Error eliminando imagen de Cloudinary");
        }
    }
}
