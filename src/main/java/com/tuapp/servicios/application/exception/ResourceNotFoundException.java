package com.tuapp.servicios.application.exception;

import java.util.UUID;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
    public ResourceNotFoundException(String resource, UUID id) {
        super(resource + " no encontrado con id: " + id);
    }
}
