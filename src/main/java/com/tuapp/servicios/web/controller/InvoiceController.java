package com.tuapp.servicios.web.controller;

import com.tuapp.servicios.application.dto.request.CorrectInvoiceRequest;
import com.tuapp.servicios.application.dto.response.InvoiceResponse;
import com.tuapp.servicios.application.dto.response.UploadInvoiceResponse;
import com.tuapp.servicios.application.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import com.tuapp.servicios.domain.repository.UserRepository;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
@Tag(name = "Facturas", description = "Gestión de facturas de servicios públicos")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final UserRepository userRepository;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Subir foto de factura para procesamiento OCR asíncrono")
    @ApiResponse(responseCode = "202", description = "Factura recibida, procesamiento OCR iniciado")
    public ResponseEntity<UploadInvoiceResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam("propertyId") UUID propertyId,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(invoiceService.uploadInvoice(file, propertyId, userId));
    }

    @GetMapping
    @Operation(summary = "Listar facturas del usuario (paginadas)")
    public ResponseEntity<Page<InvoiceResponse>> list(
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(invoiceService.listByUser(userId, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener detalle de una factura con URL pre-firmada")
    public ResponseEntity<InvoiceResponse> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(invoiceService.getById(id, userId));
    }

    @PutMapping("/{id}/correct")
    @Operation(summary = "Corregir datos extraídos por OCR manualmente")
    public ResponseEntity<InvoiceResponse> correct(
            @PathVariable UUID id,
            @Valid @RequestBody CorrectInvoiceRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(invoiceService.correctOcrData(id, request, userId));
    }

    private UUID resolveUserId(UserDetails userDetails) {
        if (userDetails == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticación requerida");
        }
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"))
                .getId();
    }
}
