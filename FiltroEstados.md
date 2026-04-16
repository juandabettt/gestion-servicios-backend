# FEATURE-001 BACKEND: Filtro de estados de facturas

## Contexto
Las facturas actualmente no se clasifican correctamente por estado. Todas aparecen en las 4 categorías al mismo tiempo. Se necesita que el backend devuelva el estado correcto de cada factura (PENDIENTE, PAGADA, VENCIDA) basándose en la fecha de vencimiento y el estado de pago.

## Reglas de negocio para clasificar el estado

| Estado | Condición |
|---|---|
| PAGADA | La factura tiene al menos un pago exitoso asociado |
| VENCIDA | La fecha de vencimiento ya pasó Y no está pagada |
| PENDIENTE | La fecha de vencimiento no ha pasado Y no está pagada |

## Lo que necesitas hacer

### 1. Verificar la entidad Invoice

Busca `Invoice.java` y verifica que tenga estos campos:
- `fechaVencimiento` (LocalDate o LocalDateTime) — fecha límite de pago
- `estado` (String o Enum) — estado actual: PENDIENTE, PAGADA, VENCIDA
- `activo` (boolean) — para soft delete

Si el campo `estado` no existe como Enum, créalo en:
`src/main/java/com/tuapp/servicios/domain/model/InvoiceEstado.java`

```java
public enum InvoiceEstado {
    PENDIENTE,
    PAGADA,
    VENCIDA
}
```

### 2. Crear un servicio o método que calcule el estado correcto

En `InvoiceServiceImpl.java`, agrega un método privado que calcule el estado real de una factura:

```java
private InvoiceEstado calcularEstado(Invoice invoice) {
    // Si tiene pagos exitosos, está PAGADA
    if (invoice.getPagos() != null && 
        invoice.getPagos().stream().anyMatch(p -> "EXITOSO".equals(p.getEstado()))) {
        return InvoiceEstado.PAGADA;
    }
    // Si la fecha de vencimiento ya pasó, está VENCIDA
    if (invoice.getFechaVencimiento() != null && 
        invoice.getFechaVencimiento().isBefore(LocalDate.now())) {
        return InvoiceEstado.VENCIDA;
    }
    // En cualquier otro caso, está PENDIENTE
    return InvoiceEstado.PENDIENTE;
}
```

Y llama a este método cada vez que se devuelve una factura al frontend para actualizar su estado:

```java
private Invoice actualizarEstado(Invoice invoice) {
    InvoiceEstado estadoReal = calcularEstado(invoice);
    if (!estadoReal.name().equals(invoice.getEstado())) {
        invoice.setEstado(estadoReal.name());
        invoiceRepository.save(invoice);
    }
    return invoice;
}
```

### 3. Agregar endpoint con filtro por estado

En `InvoiceController.java`, modifica o agrega el endpoint de listado para aceptar un parámetro de filtro opcional:

```java
@GetMapping
@PreAuthorize("hasRole('USER')")
public ResponseEntity<Page<InvoiceDTO>> getInvoices(
    @AuthenticationPrincipal UserDetails userDetails,
    @RequestParam(required = false) String estado,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size
) {
    Page<InvoiceDTO> invoices = invoiceService.getInvoicesByUsuario(
        userDetails.getUsername(), estado, PageRequest.of(page, size)
    );
    return ResponseEntity.ok(invoices);
}
```

### 4. Actualizar el servicio para filtrar por estado

En `InvoiceServiceImpl.java`, actualiza el método de obtener facturas para soportar el filtro:

```java
@Override
public Page<InvoiceDTO> getInvoicesByUsuario(String email, String estado, Pageable pageable) {
    Page<Invoice> invoices;
    
    if (estado != null && !estado.isEmpty()) {
        invoices = invoiceRepository.findByUsuarioEmailAndEstadoAndActivoTrue(
            email, estado.toUpperCase(), pageable
        );
    } else {
        invoices = invoiceRepository.findByUsuarioEmailAndActivoTrue(email, pageable);
    }
    
    // Actualizar estado de cada factura antes de devolverla
    return invoices.map(inv -> {
        actualizarEstado(inv);
        return invoiceMapper.toDTO(inv);
    });
}
```

### 5. Agregar queries en InvoiceRepository

En `InvoiceRepository.java` agrega los métodos necesarios:

```java
Page<Invoice> findByUsuarioEmailAndActivoTrue(String email, Pageable pageable);

Page<Invoice> findByUsuarioEmailAndEstadoAndActivoTrue(
    String email, String estado, Pageable pageable
);

// Para el scheduler de vencimientos (paso 6)
List<Invoice> findByEstadoAndFechaVencimientoBeforeAndActivoTrue(
    String estado, LocalDate fecha
);
```

### 6. Crear un Scheduler que actualice estados automáticamente

Crea el archivo:
`src/main/java/com/tuapp/servicios/infrastructure/scheduler/InvoiceStatusScheduler.java`

```java
@Component
@RequiredArgsConstructor
public class InvoiceStatusScheduler {

    private final InvoiceRepository invoiceRepository;
    private static final Logger log = LoggerFactory.getLogger(InvoiceStatusScheduler.class);

    // Ejecutar todos los días a las 00:01
    @Scheduled(cron = "0 1 0 * * *")
    public void actualizarFacturasVencidas() {
        log.info("Ejecutando scheduler de actualización de facturas vencidas");
        
        List<Invoice> pendientes = invoiceRepository
            .findByEstadoAndFechaVencimientoBeforeAndActivoTrue(
                "PENDIENTE", LocalDate.now()
            );
        
        pendientes.forEach(inv -> {
            inv.setEstado("VENCIDA");
            invoiceRepository.save(inv);
            log.info("Factura {} marcada como VENCIDA", inv.getId());
        });
        
        log.info("Scheduler completado: {} facturas actualizadas", pendientes.size());
    }
}
```

### 7. Verificar que @EnableScheduling está activo

Busca la clase principal `ServiceApplication.java` y verifica que tenga la anotación `@EnableScheduling`. Si no la tiene, agrégala:

```java
@SpringBootApplication
@EnableScheduling  // agregar si no existe
public class ServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServiceApplication.class, args);
    }
}
```

### 8. Migración si el campo estado no existe en la tabla

Si la columna `estado` no existe en la tabla `invoices`, crea una migración Flyway.
El archivo debe llamarse con el número siguiente al último migration existente, por ejemplo `V17__add_estado_to_invoices.sql`:

```sql
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE';
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS fecha_vencimiento DATE;

-- Actualizar facturas que ya tienen pagos exitosos
UPDATE invoices i
SET estado = 'PAGADA'
WHERE EXISTS (
    SELECT 1 FROM payment_transactions pt
    WHERE pt.invoice_id = i.id
    AND pt.estado = 'EXITOSO'
);
```

## Archivos a modificar
- `Invoice.java` — verificar/agregar campo estado y fechaVencimiento
- `InvoiceEstado.java` — crear enum si no existe
- `InvoiceController.java` — agregar parámetro ?estado= al endpoint GET
- `InvoiceService.java` — actualizar interfaz
- `InvoiceServiceImpl.java` — implementar filtro y cálculo de estado
- `InvoiceRepository.java` — agregar queries con filtro
- `InvoiceStatusScheduler.java` — crear scheduler
- `ServiceApplication.java` — verificar @EnableScheduling
- `V17__add_estado_to_invoices.sql` — crear si se necesita

## NO modificar
- Lógica de OCR ni upload
- Lógica de pagos
- Otros endpoints que no sean de facturas