# HOTFIX BACKEND: Filtro por estado de facturas devuelve 500

## Síntoma
El endpoint GET /api/v1/invoices?estado=PENDIENTE (y PAGADA, VENCIDA) responde 500 Internal Server Error.
El endpoint GET /api/v1/invoices sin parámetro funciona correctamente (200).

## Lo que necesitas hacer

### 1. Revisar InvoiceController.java

Busca el método que maneja GET /invoices y verifica cómo recibe el parámetro `estado`.
Debe verse así:

```java
@GetMapping
@PreAuthorize("hasRole('USER')")
public ResponseEntity<?> getInvoices(
    @AuthenticationPrincipal UserDetails userDetails,
    @RequestParam(required = false) String estado,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "10") int size
) {
    // ...
}
```

Si el parámetro `estado` no está declarado con `required = false`, agrégalo.

### 2. Revisar InvoiceRepository.java

El error 500 probablemente viene de una query que no existe o tiene un tipo incorrecto.
Verifica que existan estos métodos o equivalentes:

```java
// Sin filtro de estado
Page<Invoice> findByUsuarioId(UUID usuarioId, Pageable pageable);

// Con filtro de estado  
Page<Invoice> findByUsuarioIdAndEstado(UUID usuarioId, String estado, Pageable pageable);
```

Si el repositorio usa `email` en lugar de `id`:
```java
Page<Invoice> findByUsuarioEmail(String email, Pageable pageable);
Page<Invoice> findByUsuarioEmailAndEstado(String email, String estado, Pageable pageable);
```

Agrega el método que falte.

### 3. Revisar InvoiceServiceImpl.java

Busca el método que obtiene las facturas y verifica que maneje el parámetro estado sin lanzar excepción cuando es null o vacío:

```java
public Page<InvoiceDTO> getInvoices(String userIdentifier, String estado, Pageable pageable) {
    Page<Invoice> invoices;
    
    try {
        if (estado != null && !estado.isBlank()) {
            // Con filtro
            invoices = invoiceRepository.findByUsuarioEmailAndEstado(
                userIdentifier, estado.toUpperCase().trim(), pageable
            );
        } else {
            // Sin filtro - todas las facturas
            invoices = invoiceRepository.findByUsuarioEmail(
                userIdentifier, pageable
            );
        }
    } catch (Exception e) {
        log.error("Error al filtrar facturas por estado {}: {}", estado, e.getMessage());
        throw e;
    }
    
    return invoices.map(invoiceMapper::toDTO);
}
```

### 4. Revisar el campo `estado` en la entidad Invoice.java

Verifica que el campo `estado` exista en la entidad y esté mapeado correctamente a la columna de la base de datos:

```java
@Column(name = "estado", nullable = false)
private String estado = "PENDIENTE";
```

Si el campo se llama diferente (por ejemplo `status` o `invoiceStatus`), ajusta las queries del repositorio para usar el nombre correcto.

### 5. Agregar logs para diagnóstico

En el método del servicio que filtra por estado, agrega temporalmente un log para ver qué está fallando:

```java
log.info("Buscando facturas con estado: '{}' para usuario: '{}'", estado, userIdentifier);
```

## Objetivo
- GET /api/v1/invoices → devuelve todas las facturas ✅
- GET /api/v1/invoices?estado=PENDIENTE → devuelve solo pendientes
- GET /api/v1/invoices?estado=PAGADA → devuelve solo pagadas  
- GET /api/v1/invoices?estado=VENCIDA → devuelve solo vencidas

## Archivos a revisar y modificar
- `InvoiceController.java` — parámetro estado con required=false
- `InvoiceServiceImpl.java` — lógica de filtrado sin lanzar excepción
- `InvoiceRepository.java` — agregar query con filtro de estado si no existe
- `Invoice.java` — verificar que el campo estado existe y su nombre exacto

## NO modificar
- Lógica de upload ni OCR
- Lógica de pagos
- Migraciones existentes
