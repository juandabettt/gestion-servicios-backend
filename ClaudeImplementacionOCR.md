Necesito corregir un bug en InvoiceService.java. El método upload() de 
CloudinaryFileStorageAdapter devuelve la URL pública real de Cloudinary, 
pero InvoiceService ignora ese valor y guarda el objectKey original, 
causando un 404 al intentar descargar la imagen después.

## CAMBIO ÚNICO — InvoiceService.java

Archivo: src/main/java/com/tuapp/servicios/application/service/InvoiceService.java

Dentro del método uploadInvoice(), busca este bloque exacto:

    String objectKey = buildObjectKey(userId, propertyId, file.getOriginalFilename());
    try {
        fileStoragePort.upload(objectKey, file.getBytes(), file.getContentType());
    } catch (Exception e) {
        throw new BusinessException("Error al subir el archivo", HttpStatus.INTERNAL_SERVER_ERROR);
    }
    Invoice invoice = Invoice.builder()
            .property(property)
            .estado(EstadoFactura.PROCESANDO_OCR)
            .urlFotoFactura(objectKey)
            .build();
    invoice = invoiceRepository.save(invoice);
    jobQueueService.enqueue(TipoJob.OCR_FACTURA, Map.of(
            "invoiceId", invoice.getId().toString(),
            "objectKey", objectKey));

Reemplázalo por esto:

    String objectKey = buildObjectKey(userId, propertyId, file.getOriginalFilename());
    String storageUrl;
    try {
        storageUrl = fileStoragePort.upload(objectKey, file.getBytes(), file.getContentType());
    } catch (Exception e) {
        throw new BusinessException("Error al subir el archivo", HttpStatus.INTERNAL_SERVER_ERROR);
    }
    Invoice invoice = Invoice.builder()
            .property(property)
            .estado(EstadoFactura.PROCESANDO_OCR)
            .urlFotoFactura(storageUrl)
            .build();
    invoice = invoiceRepository.save(invoice);
    jobQueueService.enqueue(TipoJob.OCR_FACTURA, Map.of(
            "invoiceId", invoice.getId().toString(),
            "objectKey", storageUrl));

## RESTRICCIONES
- Modifica SOLO el archivo InvoiceService.java
- SOLO cambia el bloque indicado dentro de uploadInvoice()
- No toques ningún otro método ni archivo
- Confirma qué líneas cambiaste al terminar