# FEATURE-002 BACKEND: Eliminar factura

## Contexto
Se necesita un endpoint para eliminar facturas usando soft delete (nunca borrar físicamente registros financieros).

## Lo que necesitas hacer

### 1. Verificar si el endpoint ya existe

Busca en el proyecto si ya existe un endpoint:
```
DELETE /api/v1/invoices/{id}
```

Revisa en:
- `src/main/java/com/tuapp/servicios/infrastructure/adapter/web/InvoiceController.java`
- Cualquier archivo `*Controller.java` relacionado con facturas

Si ya existe, verifica que use soft delete y no borrado físico. Si no existe, créalo siguiendo los pasos a continuación.

### 2. Agregar endpoint en InvoiceController.java

```java
@DeleteMapping("/{id}")
@PreAuthorize("hasRole('USER')")
public ResponseEntity<Void> deleteInvoice(
    @PathVariable UUID id,
    @AuthenticationPrincipal UserDetails userDetails
) {
    invoiceService.deleteInvoice(id, userDetails.getUsername());
    return ResponseEntity.noContent().build();
}
```

### 3. Agregar método en la interfaz del servicio

Busca `InvoiceService.java` (la interfaz) y agrega:

```java
void deleteInvoice(UUID invoiceId, String userEmail);
```

### 4. Implementar el método en InvoiceServiceImpl.java

```java
@Override
@Transactional
public void deleteInvoice(UUID invoiceId, String userEmail) {
    Invoice invoice = invoiceRepository.findByIdAndUsuarioEmail(invoiceId, userEmail)
        .orElseThrow(() -> new ResourceNotFoundException("Factura no encontrada"));

    // Soft delete - NUNCA borrar físicamente registros financieros
    invoice.setDeletedAt(LocalDateTime.now());
    invoice.setActivo(false);
    invoiceRepository.save(invoice);
}
```

> Si la entidad Invoice no tiene los campos deletedAt o activo, agrégalos:
> - private LocalDateTime deletedAt;
> - private boolean activo = true;

### 5. Verificar que las queries excluyen facturas eliminadas

Busca en `InvoiceRepository.java` los métodos de búsqueda y asegúrate de que filtren por `activo = true` o `deletedAt IS NULL`. Por ejemplo:

```java
// Si usa activo:
List<Invoice> findByUsuarioEmailAndActivoTrue(String email);

// Si usa deletedAt:
List<Invoice> findByUsuarioEmailAndDeletedAtIsNull(String email);
```

Si las queries existentes no tienen este filtro, agrégalo para que las facturas eliminadas no aparezcan en los listados.

### 6. Migración de base de datos si se agregan campos nuevos

Si los campos `deleted_at` o `activo` no existen en la tabla, crea una nueva migración Flyway en:
`src/main/resources/db/migration/`

El archivo debe llamarse `V16__add_soft_delete_to_invoices.sql` (usa el número siguiente al último que exista):

```sql
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS activo BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
```

## Archivos a modificar
- `InvoiceController.java` — agregar endpoint DELETE
- `InvoiceService.java` — agregar método en la interfaz
- `InvoiceServiceImpl.java` — implementar el método
- `InvoiceRepository.java` — verificar y actualizar queries
- `Invoice.java` — agregar campos si no existen
- `V16__add_soft_delete_to_invoices.sql` — crear si se necesitan campos nuevos

## NO modificar
- Lógica de upload ni OCR
- Otros endpoints de facturas
- Tablas de pagos ni transacciones