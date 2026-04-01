package com.tuapp.servicios.infrastructure.security;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

@Service
@Slf4j
public class FileValidationService {

    private static final Set<String> ALLOWED_MIME_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024; // 10 MB
    private final Tika tika = new Tika();

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("El archivo no puede estar vacío");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidFileException("Archivo demasiado grande. Máximo permitido: 10 MB");
        }
        String detectedMimeType = detectRealMimeType(file);
        if (!ALLOWED_MIME_TYPES.contains(detectedMimeType)) {
            throw new InvalidFileException("Tipo de archivo no permitido: " + detectedMimeType +
                    ". Solo se aceptan JPEG, PNG y WebP");
        }
    }

    private String detectRealMimeType(MultipartFile file) {
        try {
            return tika.detect(file.getInputStream());
        } catch (IOException e) {
            log.error("Error detectando tipo MIME real del archivo");
            throw new InvalidFileException("No se pudo verificar el tipo del archivo");
        }
    }

    public static class InvalidFileException extends RuntimeException {
        public InvalidFileException(String message) {
            super(message);
        }
    }
}
