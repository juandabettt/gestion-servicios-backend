
> **Instrucciones para Claude Code:** Lee este documento completo antes de escribir una sola línea de código.
> Cada sección contiene decisiones arquitectónicas obligatorias. No las omitas ni simplifiques.
> Cuando termines una sección, marca mentalmente los puntos críticos resueltos antes de continuar.

---

## 1. Visión General del Sistema

Desarrollar un backend de nivel empresarial para una plataforma que centraliza la gestión y pago de servicios públicos domésticos (agua, energía, gas, internet). El sistema permite:

1. Digitalizar facturas físicas mediante fotografía (OCR con IA).
2. Pagar facturas consolidadas vía PSE, Nequi y Tarjeta de Crédito.
3. Analizar el historial de consumo con IA para detectar anomalías, predecir gastos futuros y emitir recomendaciones de ahorro personalizadas.
4. Notificar proactivamente al usuario sobre vencimientos, confirmaciones de pago y alertas de consumo.
5. Permitir pagos automáticos programados sin intervención del usuario.

**Mercado objetivo:** Hogares colombianos. Pasarela de pagos: Wompi / ePayco (implementar con Mock).

---

## 2. Stack Tecnológico Obligatorio

| Capa | Tecnología | Versión mínima |
|---|---|---|
| Lenguaje | Java | 17 (usar records, sealed classes, text blocks) |
| Framework | Spring Boot | 3.2.x |
| Base de datos principal | PostgreSQL | 15+ |
| Caché | Redis | 7+ (vía Spring Data Redis) |
| ORM | Spring Data JPA + Hibernate | — |
| Seguridad | Spring Security + JWT | — |
| Resiliencia | Resilience4j | 2.x |
| Mensajería interna | Spring Events + tabla `job_queue` en PostgreSQL | — |
| Validación | Spring Validation (Jakarta) | — |
| Utilidades | Lombok | — |
| Testing | JUnit 5 + Mockito + Testcontainers | — |
| Documentación API | SpringDoc OpenAPI (Swagger UI) | 2.x |
| Migraciones DB | Flyway | — |

> **Sobre mensajería:** No se requiere RabbitMQ ni Kafka en v1. Usar una tabla `job_queue` en PostgreSQL con polling vía `@Scheduled`. Diseñar las interfaces de forma que en v2 se pueda reemplazar por un broker real sin cambiar la capa de negocio.

---

## 3. Reglas Arquitectónicas — SIN EXCEPCIÓN

### 3.1 Capa estricta
```
Controller → Service → Repository
```
- Los **Controllers** solo reciben y devuelven DTOs. Nunca acceden a repositorios directamente.
- Los **Services** contienen toda la lógica de negocio. Nunca devuelven entidades JPA al exterior.
- Los **Repositories** son interfaces de Spring Data JPA. Nunca contienen lógica de negocio.
- Los **Mappers** son clases dedicadas (usar MapStruct o métodos estáticos en los DTOs). Nunca mapear en el controller ni en el service directamente.

### 3.2 Dinero
- Todo campo monetario usa **`BigDecimal`** sin excepción.
- Prohibido `Double`, `Float` o `double` para representar dinero.
- En PostgreSQL: `NUMERIC(19, 4)`.
- En JPA: `@Column(precision = 19, scale = 4)`.
- En JSON (serialización): usar `JsonSerializer` de Jackson para mantener escala fija de 2 decimales en la respuesta.

### 3.3 Manejo global de excepciones
Implementar **un único** `@RestControllerAdvice` llamado `GlobalExceptionHandler`. Toda excepción no controlada debe retornar una respuesta JSON con este formato (RFC 7807 Problem Details):

```json
{
  "timestamp": "2025-01-15T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "El campo 'monto_total' es obligatorio",
  "path": "/api/v1/invoices",
  "traceId": "abc123def456"
}
```

Nunca exponer stack traces, nombres de clases internas ni mensajes de excepción de Hibernate/SQL al cliente.

### 3.4 Paginación obligatoria
Todo endpoint que devuelva una colección **debe** aceptar `Pageable` como parámetro y devolver `Page<T>`. Sin excepción.

### 3.5 Procesamiento asíncrono
- Configurar un `ThreadPoolTaskExecutor` explícito con nombre `taskExecutor`. No usar el pool por defecto de Spring.
- Parámetros mínimos: `corePoolSize=4`, `maxPoolSize=10`, `queueCapacity=200`.
- Toda tarea `@Async` debe tener manejo de errores con `AsyncUncaughtExceptionHandler`.
- Para tareas críticas (OCR, pagos) usar la tabla `job_queue` en lugar de `@Async` puro, para garantizar persistencia ante reinicios.

### 3.6 Logging
- Usar SLF4J con Logback.
- **Nunca** loguear: montos de transacción, números de referencia de factura, tokens JWT, contraseñas, ni datos de tarjeta.
- Incluir siempre el `traceId` (MDC) en cada línea de log.
- Nivel INFO para flujos normales, WARN para situaciones recuperables, ERROR para fallos que requieren atención.

---

## 4. Modelo de Datos Completo (JPA)

### 4.1 Auditoría base
Crear una clase abstracta `BaseAuditEntity` que todas las entidades extiendan:

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseAuditEntity {
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt; // Soft delete obligatorio
}
```

**Soft delete obligatorio en todas las entidades financieras.** Usar `@Where(clause = "deleted_at IS NULL")` para filtrado automático. Nunca hacer `DELETE` físico en tablas de facturas o transacciones.

### 4.2 Entidades

**User**
```
id                  UUID (PK, generado automáticamente)
nombre              VARCHAR(150) NOT NULL
email               VARCHAR(255) UNIQUE NOT NULL
password_hash       VARCHAR(255) NOT NULL
rol                 ENUM(USER, ADMIN) NOT NULL DEFAULT 'USER'
activo              BOOLEAN NOT NULL DEFAULT TRUE
ultimo_login        TIMESTAMP
telefono            VARCHAR(20) -- para notificaciones push/SMS
+ campos BaseAuditEntity
```

**Property** *(nuevo — permite multi-hogar)*
```
id                  UUID (PK)
user_id             UUID (FK → User) NOT NULL
nombre              VARCHAR(100) NOT NULL  -- "Casa principal", "Apartamento arriendo"
direccion           VARCHAR(255)
ciudad              VARCHAR(100)
es_principal        BOOLEAN NOT NULL DEFAULT TRUE
+ campos BaseAuditEntity
```
> Relación: un User tiene muchas Properties. Las facturas pertenecen a una Property, no directamente al User.

**ProviderCompany**
```
id                      UUID (PK)
nombre                  VARCHAR(150) NOT NULL
nit                     VARCHAR(20) UNIQUE NOT NULL
tipo_servicio           ENUM(AGUA, ENERGIA, GAS, INTERNET, TELEFONIA) NOT NULL
codigo_convenio_recaudo VARCHAR(50) NOT NULL  -- esencial para pasarela
url_portal              VARCHAR(255)          -- portal web del proveedor
telefono_soporte        VARCHAR(20)
ciudad_cobertura        VARCHAR(100)
ciclo_facturacion_dias  INT DEFAULT 30
activo                  BOOLEAN NOT NULL DEFAULT TRUE
+ campos BaseAuditEntity
```

**Invoice** *(Factura)*
```
id                  UUID (PK)
property_id         UUID (FK → Property) NOT NULL
proveedor_id        UUID (FK → ProviderCompany) NOT NULL
numero_referencia   VARCHAR(100) NOT NULL  -- CIFRAR en reposo
fecha_emision       DATE NOT NULL
fecha_vencimiento   DATE NOT NULL
monto_total         NUMERIC(19,4) NOT NULL
consumo_unidad      NUMERIC(19,4)          -- kWh, m³, GB, etc.
unidad_medida       VARCHAR(20)            -- "kWh", "m3", "GB"
periodo_facturado   VARCHAR(20)            -- "2025-01", "2025-02"
estado              ENUM(PENDIENTE, PROCESANDO_OCR, PROCESANDO_PAGO, PAGADA, VENCIDA, ERROR_OCR) NOT NULL DEFAULT 'PROCESANDO_OCR'
url_foto_factura    VARCHAR(500)           -- URL pre-firmada S3/MinIO
ocr_datos_raw       TEXT                   -- JSON crudo devuelto por el servicio OCR (para auditoría)
ocr_confianza       DECIMAL(5,2)           -- 0.00 a 100.00, % de confianza del OCR
ingreso_manual      BOOLEAN DEFAULT FALSE  -- true si el usuario corrigió datos del OCR
+ campos BaseAuditEntity (con soft delete)
```

Índices obligatorios:
- `(property_id, estado)` — para listar facturas pendientes de un hogar
- `(fecha_vencimiento, estado)` — para el job de vencimientos
- `(proveedor_id, periodo_facturado)` — para análisis de consumo

**PaymentTransaction**
```
id                          UUID (PK)
factura_id                  UUID (FK → Invoice) NOT NULL
monto_transaccion           NUMERIC(19,4) NOT NULL
metodo_pago                 ENUM(TARJETA_CREDITO, NEQUI, PSE) NOT NULL
banco_origen                VARCHAR(100)   -- requerido si metodo_pago = PSE
id_transaccion_pasarela     VARCHAR(255)   -- ID externo de Wompi/ePayco — CIFRAR
estado_transaccion          ENUM(INICIADA, APROBADA, RECHAZADA, PENDIENTE_PSE, EXPIRADA, REEMBOLSADA) NOT NULL DEFAULT 'INICIADA'
url_redireccion_pse         VARCHAR(500)   -- URL bancaria para PSE
fecha_confirmacion          TIMESTAMP      -- cuándo la pasarela confirmó
intentos_webhook            INT DEFAULT 0  -- cuántas veces llegó el webhook
idempotency_key             VARCHAR(255) UNIQUE -- para prevenir pagos duplicados
+ campos BaseAuditEntity (con soft delete)
```

**AiAnalysis**
```
id                  UUID (PK)
property_id         UUID (FK → Property) NOT NULL
tipo_analisis       ENUM(ANOMALIA, RECOMENDACION, PREDICCION, COMPARATIVA) NOT NULL
tipo_servicio       ENUM(AGUA, ENERGIA, GAS, INTERNET, TODOS) NOT NULL
descripcion         TEXT NOT NULL
impacto_estimado    VARCHAR(255)      -- "Ahorro estimado: $45.000/mes"
periodo_analizado   VARCHAR(20)       -- "2025-01 a 2025-03"
datos_entrada       TEXT              -- JSON del historial enviado a la IA (para auditoría)
estado              ENUM(PROCESANDO, COMPLETADO, FALLIDO) NOT NULL DEFAULT 'PROCESANDO'
calificacion_usuario INT              -- 1 a 5, feedback del usuario (puede ser null)
+ campos BaseAuditEntity
```

**AutoPayRule** *(nuevo — pagos automáticos programados)*
```
id                  UUID (PK)
property_id         UUID (FK → Property) NOT NULL
proveedor_id        UUID (FK → ProviderCompany) NOT NULL
metodo_pago         ENUM(TARJETA_CREDITO, NEQUI, PSE) NOT NULL
dias_antes_vencimiento INT NOT NULL DEFAULT 2  -- ejecutar N días antes del vencimiento
monto_maximo        NUMERIC(19,4)              -- no pagar si la factura supera este valor (protección)
activo              BOOLEAN NOT NULL DEFAULT TRUE
ultima_ejecucion    TIMESTAMP
+ campos BaseAuditEntity
```

**NotificationLog** *(nuevo)*
```
id                  UUID (PK)
user_id             UUID (FK → User) NOT NULL
tipo                ENUM(FACTURA_POR_VENCER, PAGO_CONFIRMADO, ANOMALIA_DETECTADA, ANALISIS_LISTO, AUTOPAGO_EJECUTADO, AUTOPAGO_FALLIDO) NOT NULL
canal               ENUM(EMAIL, PUSH, SMS) NOT NULL
estado              ENUM(PENDIENTE, ENVIADA, FALLIDA) NOT NULL DEFAULT 'PENDIENTE'
asunto              VARCHAR(255)
cuerpo_resumen      VARCHAR(500)       -- resumen del contenido, nunca datos sensibles
referencia_id       UUID               -- ID de la factura, análisis, etc. relacionado
intentos            INT DEFAULT 0
+ campos BaseAuditEntity
```

**UserPreferences** *(nuevo)*
```
id                              UUID (PK)
user_id                         UUID (FK → User) UNIQUE NOT NULL
dias_anticipacion_alerta        INT DEFAULT 5       -- alertar N días antes del vencimiento
metodo_pago_default             ENUM(TARJETA_CREDITO, NEQUI, PSE)
presupuesto_mensual_agua        NUMERIC(19,4)
presupuesto_mensual_energia     NUMERIC(19,4)
presupuesto_mensual_gas         NUMERIC(19,4)
presupuesto_mensual_internet    NUMERIC(19,4)
notificaciones_email            BOOLEAN DEFAULT TRUE
notificaciones_push             BOOLEAN DEFAULT TRUE
moneda                          VARCHAR(3) DEFAULT 'COP'
+ campos BaseAuditEntity
```

**JobQueue** *(nuevo — persistencia de tareas async)*
```
id                  UUID (PK)
tipo_job            ENUM(OCR_FACTURA, ANALISIS_IA, AUTOPAGO, NOTIFICACION, CONCILIACION) NOT NULL
payload             TEXT NOT NULL          -- JSON con los datos necesarios para ejecutar el job
estado              ENUM(PENDIENTE, EN_PROCESO, COMPLETADO, FALLIDO, EN_DLQ) NOT NULL DEFAULT 'PENDIENTE'
intentos            INT DEFAULT 0
max_intentos        INT DEFAULT 3
proximo_intento     TIMESTAMP
error_detalle       TEXT                   -- último error registrado
worker_id           VARCHAR(100)           -- instancia que tomó el job (para multi-instancia)
+ campos BaseAuditEntity
```

**AuditLog** *(nuevo — trazabilidad de seguridad)*
```
id                  UUID (PK)
user_id             UUID               -- puede ser null (acciones anónimas)
accion              VARCHAR(100) NOT NULL  -- "INVOICE_CREATED", "PAYMENT_INITIATED", etc.
entidad             VARCHAR(50)        -- "Invoice", "PaymentTransaction"
entidad_id          UUID
ip_origen           VARCHAR(45)        -- IPv4 o IPv6
user_agent          VARCHAR(255)
resultado           ENUM(EXITO, FALLO) NOT NULL
detalle             VARCHAR(500)       -- descripción legible, sin datos sensibles
created_at          TIMESTAMP NOT NULL DEFAULT NOW()
```
> La tabla `audit_log` **nunca** tiene soft delete ni `updated_at`. Es append-only.

---

## 5. Seguridad — Implementación Obligatoria

### 5.1 JWT
- Access token: expiración **15 minutos**.
- Refresh token: expiración **7 días**, almacenado en la BD con estado (ACTIVO, REVOCADO, EXPIRADO).
- Al hacer login o refresh, generar nuevos tokens y revocar el anterior (rotation).
- Endpoint `POST /api/v1/auth/logout`: revocar el refresh token activo.
- Guardar el JTI (JWT ID) del access token en Redis con TTL igual a su expiración, para poder revocar tokens antes de que expiren si el usuario hace logout.

### 5.2 Webhook de pasarela — verificación HMAC
El endpoint `POST /api/v1/payments/webhook/gateway` es el punto más crítico de seguridad del sistema.

Implementación obligatoria:
1. La pasarela envía un header `X-Gateway-Signature` con valor `HMAC-SHA256(secret, body)`.
2. Antes de procesar cualquier payload, verificar la firma:

```java
@Component
public class WebhookSignatureValidator {
    
    @Value("${payment.gateway.webhook-secret}")
    private String webhookSecret;
    
    public boolean isValid(String payload, String receivedSignature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expectedSignature = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            // Comparación en tiempo constante — previene timing attacks
            return MessageDigest.isEqual(expectedSignature.getBytes(), receivedSignature.getBytes());
        } catch (Exception e) {
            return false;
        }
    }
}
```

3. Si la firma no coincide: devolver HTTP 401 y registrar en `AuditLog` con `resultado=FALLO`.
4. Implementar idempotencia: guardar el `id_transaccion_pasarela` recibido en Redis por 24h. Si llega duplicado, devolver HTTP 200 sin reprocesar.

### 5.3 Rate limiting
Implementar con **Bucket4j** (sin dependencia de infraestructura adicional):

| Endpoint | Límite |
|---|---|
| `POST /api/v1/auth/login` | 5 intentos por IP por minuto |
| `POST /api/v1/auth/register` | 3 registros por IP por hora |
| `POST /api/v1/invoices/upload` | 10 uploads por usuario por hora |
| `POST /api/v1/ai-insights/analyze` | 3 análisis por usuario por día |
| Resto de endpoints autenticados | 100 req por usuario por minuto |

Al superar el límite, devolver HTTP 429 con header `Retry-After`.

### 5.4 Autorización por ownership
Nunca confiar solo en el rol. Verificar que el recurso pertenece al usuario autenticado:

```java
@Service
public class InvoiceOwnershipValidator {
    public void validateOwnership(UUID invoiceId, UUID authenticatedUserId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Factura no encontrada"));
        
        if (!invoice.getProperty().getUser().getId().equals(authenticatedUserId)) {
            // Loguear intento de acceso no autorizado en AuditLog
            auditService.log(authenticatedUserId, "UNAUTHORIZED_ACCESS_ATTEMPT", "Invoice", invoiceId, "FALLO");
            throw new AccessDeniedException("No tienes permiso para acceder a esta factura");
        }
    }
}
```

Aplicar este patrón en: Invoices, PaymentTransactions, AiAnalysis, AutoPayRules, Properties.

### 5.5 Cifrado de datos sensibles en reposo
Implementar un `AttributeConverter` de JPA para cifrar campos sensibles con AES-256-GCM:

```java
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {
    // Cifrar al guardar en BD, descifrar al leer
    // Usar clave de cifrado inyectada por variable de entorno, nunca hardcodeada
    // Los campos a cifrar son: numero_referencia (Invoice) e id_transaccion_pasarela (PaymentTransaction)
}
```

La clave de cifrado debe estar en la variable de entorno `FIELD_ENCRYPTION_KEY` (base64, 256 bits).

### 5.6 Validación de archivos subidos
Antes de procesar cualquier imagen de factura:

```java
@Service
public class FileValidationService {
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB
    
    public void validate(MultipartFile file) {
        // 1. Verificar tamaño
        if (file.getSize() > MAX_FILE_SIZE_BYTES) throw new InvalidFileException("Archivo demasiado grande");
        
        // 2. Verificar MIME real (no solo extensión) usando Apache Tika o leyendo los magic bytes
        String detectedMimeType = detectRealMimeType(file.getBytes());
        if (!ALLOWED_MIME_TYPES.contains(detectedMimeType)) {
            throw new InvalidFileException("Tipo de archivo no permitido");
        }
        
        // 3. Nunca confiar en file.getContentType() — es el valor que el cliente envía, fácilmente falsificable
    }
}
```

---

## 6. Almacenamiento de Archivos

Las imágenes de facturas **nunca** se sirven directamente desde el servidor de aplicación. Usar almacenamiento de objetos:

- **Producción:** AWS S3 o compatible S3.
- **Desarrollo/local:** MinIO (dockerizado).

Flujo obligatorio:
1. Recibir el archivo en el endpoint.
2. Validar (tipo MIME real, tamaño).
3. Subir a S3/MinIO en el bucket `facturas-{env}` con key `{userId}/{propertyId}/{año}/{mes}/{uuid}.jpg`.
4. Guardar en la BD **solo la key** (no la URL completa).
5. Al devolver la factura al cliente, generar una URL **pre-firmada** con expiración de 15 minutos.

```java
public interface FileStoragePort {
    String upload(String objectKey, byte[] content, String contentType);
    String generatePresignedUrl(String objectKey, Duration expiry);
    void delete(String objectKey);
}

// Implementaciones: S3FileStorageAdapter, MinIOFileStorageAdapter
// Selección por perfil de Spring: @Profile("production") / @Profile("local")
```

---

## 7. Endpoints Completos

### A. Autenticación — `/api/v1/auth`

| Método | Ruta | Descripción | Auth |
|---|---|---|---|
| POST | `/register` | Registro con BCrypt (cost 12) | No |
| POST | `/login` | Retorna accessToken + refreshToken | No |
| POST | `/refresh` | Renueva tokens usando refreshToken | No (lleva el refresh token en el body) |
| POST | `/logout` | Revoca refresh token activo | Sí |

### B. Propiedades — `/api/v1/properties`

| Método | Ruta | Descripción | Auth |
|---|---|---|---|
| POST | `/` | Crear nuevo hogar/inmueble | Sí |
| GET | `/` | Listar propiedades del usuario | Sí |
| PUT | `/{id}` | Actualizar datos de una propiedad | Sí |
| DELETE | `/{id}` | Soft delete de una propiedad | Sí |

### C. Facturas — `/api/v1/invoices`

| Método | Ruta | Descripción | Auth |
|---|---|---|---|
| POST | `/upload` | Subir foto → 202 Accepted → OCR async | Sí |
| GET | `/` | Listar facturas del usuario (paginadas, filtros por estado/servicio/fecha) | Sí |
| GET | `/{id}` | Detalle de una factura con URL pre-firmada | Sí |
| PUT | `/{id}/correct` | El usuario corrige datos del OCR (marcar `ingreso_manual=true`) | Sí |
| GET | `/export` | Exportar historial CSV o PDF (async, notifica por email) | Sí |

**Flujo de upload detallado:**
1. Validar archivo (tipo MIME real, tamaño).
2. Subir a S3/MinIO.
3. Crear entidad `Invoice` con `estado=PROCESANDO_OCR`.
4. Crear registro en `JobQueue` con `tipo_job=OCR_FACTURA` y `payload={invoiceId, objectKey}`.
5. Responder HTTP 202 `{ "invoiceId": "...", "message": "Factura recibida. Procesando..." }`.
6. El worker de `JobQueue` procesa el OCR de forma asíncrona.
7. Al completarse, actualizar `Invoice` con los datos extraídos y `estado=PENDIENTE`.
8. Crear una `Notification` de tipo `FACTURA_PROCESADA` para el usuario.

### D. Pagos — `/api/v1/payments`

| Método | Ruta | Descripción | Auth |
|---|---|---|---|
| POST | `/process` | Iniciar pago de una factura | Sí |
| GET | `/{transactionId}/status` | Consultar estado de una transacción | Sí |
| POST | `/webhook/gateway` | Webhook de pasarela (PÚBLICO — verificar HMAC) | No JWT |

**Flujo de pago con idempotencia:**
1. El cliente envía `{ invoiceId, metodoPago, idempotencyKey }`.
2. Buscar en Redis si `idempotencyKey` ya existe. Si existe, devolver el resultado anterior (HTTP 200).
3. Verificar ownership de la factura.
4. Verificar que la factura está en estado `PENDIENTE`.
5. Crear `PaymentTransaction` con `estado=INICIADA`.
6. Llamar a `PaymentGatewayPort.initiatePayment()`.
7. Para Nequi/Tarjeta: respuesta inmediata → actualizar estado a APROBADA o RECHAZADA.
8. Para PSE: respuesta con URL de redirección → estado `PENDIENTE_PSE`.
9. Guardar `idempotencyKey` en Redis con TTL 24h.
10. Actualizar `Invoice.estado` según resultado.

**Flujo del webhook:**
1. Verificar firma HMAC (ver sección 5.2). Si falla: HTTP 401.
2. Verificar idempotencia del evento por `id_transaccion_pasarela`.
3. Buscar `PaymentTransaction` por `id_transaccion_pasarela`.
4. Actualizar `estado_transaccion` según el payload.
5. Si APROBADA: marcar `Invoice.estado = PAGADA`.
6. Si RECHAZADA: marcar `Invoice.estado = PENDIENTE` (para permitir reintento).
7. Crear `Notification` correspondiente.
8. Devolver HTTP 200 inmediatamente — el procesamiento posterior es async.

### E. Inteligencia Artificial — `/api/v1/ai-insights`

| Método | Ruta | Descripción | Auth |
|---|---|---|---|
| POST | `/analyze` | Disparar análisis async del historial | Sí |
| GET | `/recommendations` | Listar análisis previos (paginado) | Sí |
| POST | `/{id}/feedback` | El usuario califica una recomendación (1-5) | Sí |

**Flujo de análisis:**
1. Verificar si ya existe un análisis reciente (mismo `property_id`, misma semana). Si existe, devolver el caché.
2. Si no: crear `AiAnalysis` con `estado=PROCESANDO`.
3. Crear job en `JobQueue` con `tipo_job=ANALISIS_IA`.
4. Responder HTTP 202 `{ "analysisId": "...", "message": "Análisis en proceso..." }`.
5. El worker construye el contexto, llama a la IA, guarda resultados.
6. Al terminar, notificar al usuario vía `NotificationLog`.

### F. Auto-pagos — `/api/v1/autopay`

| Método | Ruta | Descripción | Auth |
|---|---|---|---|
| POST | `/rules` | Crear regla de autopago para un proveedor | Sí |
| GET | `/rules` | Listar reglas activas del usuario | Sí |
| PUT | `/rules/{id}` | Modificar una regla | Sí |
| DELETE | `/rules/{id}` | Desactivar una regla (soft delete) | Sí |

### G. Notificaciones — `/api/v1/notifications`

| Método | Ruta | Descripción | Auth |
|---|---|---|---|
| GET | `/` | Listar notificaciones del usuario (paginadas) | Sí |
| PUT | `/{id}/read` | Marcar como leída | Sí |
| GET | `/preferences` | Obtener preferencias de notificación | Sí |
| PUT | `/preferences` | Actualizar preferencias | Sí |

### H. Preferencias — `/api/v1/preferences`

| Método | Ruta | Descripción | Auth |
|---|---|---|---|
| GET | `/` | Obtener preferencias del usuario | Sí |
| PUT | `/` | Actualizar preferencias (presupuestos, método de pago default) | Sí |

### I. Admin — `/api/v1/admin` (rol ADMIN requerido)

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/providers` | Listar todos los proveedores |
| POST | `/providers` | Crear proveedor |
| PUT | `/providers/{id}` | Actualizar proveedor |
| GET | `/users` | Listar usuarios (paginado) |
| GET | `/audit-logs` | Ver log de auditoría (paginado, filtros) |
| POST | `/reconciliation/run` | Disparar conciliación manual |

---

## 8. Interfaces de Integración Externa (Ports & Adapters)

### 8.1 PaymentGatewayPort
```java
public interface PaymentGatewayPort {
    PaymentInitiationResult initiatePayment(PaymentRequest request);
    TransactionStatusResult getTransactionStatus(String gatewayTransactionId);
    RefundResult refund(String gatewayTransactionId, BigDecimal amount);
    boolean verifyWebhookSignature(String payload, String signature);
}

// Implementaciones:
// - MockPaymentGatewayAdapter  (activa siempre en tests y perfil 'local')
// - WompiPaymentGatewayAdapter (perfil 'production')

// MockPaymentGatewayAdapter debe:
// - Aprobar automáticamente pagos con Nequi y Tarjeta.
// - Para PSE: devolver una URL de redirección simulada y dejar la transacción en PENDIENTE_PSE.
// - Simular un 10% de rechazos aleatorios para pruebas de manejo de errores.
// - Tener un endpoint interno de testing: POST /api/v1/test/mock-gateway/confirm-pse/{transactionId}
```

### 8.2 OcrServicePort
```java
public interface OcrServicePort {
    OcrExtractionResult extractInvoiceData(byte[] imageBytes, String mimeType);
}

// OcrExtractionResult debe contener:
// - empresa (String)
// - numeroReferencia (String)
// - fechaEmision (LocalDate)
// - fechaVencimiento (LocalDate)
// - montoTotal (BigDecimal)
// - consumoUnidad (BigDecimal)
// - unidadMedida (String)
// - confianza (BigDecimal) — porcentaje de confianza del OCR
// - datosRaw (String) — JSON crudo para auditoría

// Implementaciones:
// - MockOcrServiceAdapter (perfil 'local') — devuelve datos fijos con variación aleatoria para pruebas
// - ClaudeOcrServiceAdapter (perfil 'production') — usa Claude vision API
// - GoogleVisionOcrServiceAdapter (alternativa)
```

### 8.3 AiAnalysisPort
```java
public interface AiAnalysisPort {
    AiAnalysisResult analyzeConsumption(ConsumptionHistoryContext context);
}

// ConsumptionHistoryContext debe incluir:
// - propertyId, ciudad, tipoServicio
// - Lista de {periodo, consumoUnidad, montoTotal} (últimos 12 meses como máximo)
// - presupuestoMensual (si el usuario lo configuró)
// - promedioVecinos (si está disponible — ver sección de ideas de alto impacto)

// AiAnalysisResult debe incluir:
// - List<AnomalyDetection> anomalias
// - List<Recommendation> recomendaciones
// - ConsumptionPrediction prediccionProximaFactura
// - String resumenEjecutivo

// Implementaciones:
// - MockAiAnalysisAdapter (perfil 'local')
// - ClaudeAiAnalysisAdapter (perfil 'production') — construye el prompt y llama a la API de Claude
```

**Diseño del prompt para ClaudeAiAnalysisAdapter:**
```java
// El prompt debe construirse así (en AiPromptBuilderService):
// 1. Instrucción de sistema: "Eres un asesor de eficiencia energética..."
// 2. Datos estructurados: historial de consumo en JSON
// 3. Contexto del hogar: ciudad, servicio, presupuesto
// 4. Instrucción de formato: "Responde ÚNICAMENTE con un JSON válido con esta estructura: {...}"
// 5. El JSON de respuesta debe ser parseado con manejo de error. Si el parsing falla,
//    loguear el texto raw y marcar el análisis como FALLIDO.
```

### 8.4 NotificationPort
```java
public interface NotificationPort {
    void sendEmail(EmailNotificationRequest request);
    void sendPushNotification(PushNotificationRequest request);
}

// Implementaciones:
// - MockNotificationAdapter (perfil 'local') — solo loguea
// - SendgridEmailAdapter (perfil 'production')
// - FirebasePushAdapter (perfil 'production')
```

---

## 9. JobQueue — Procesamiento Resiliente

El procesador de jobs es el corazón de la resiliencia del sistema. Implementar `JobQueueProcessor`:

```java
@Component
public class JobQueueProcessor {

    // Ejecutar cada 10 segundos
    @Scheduled(fixedDelay = 10_000)
    @Transactional
    public void processPendingJobs() {
        // 1. Seleccionar hasta 5 jobs en estado PENDIENTE con proximo_intento <= NOW()
        //    Usar SELECT ... FOR UPDATE SKIP LOCKED (para multi-instancia)
        // 2. Marcar como EN_PROCESO con worker_id = instancia actual
        // 3. Para cada job, despachar al handler correspondiente según tipo_job
        // 4. Si el handler lanza excepción:
        //    - Incrementar intentos
        //    - Si intentos < max_intentos: calcular proximo_intento con backoff exponencial (1min, 5min, 30min)
        //    - Si intentos >= max_intentos: mover a estado EN_DLQ y enviar alerta al admin
        // 5. Si el handler termina bien: marcar como COMPLETADO
    }

    // Dead Letter Queue — revisar jobs fallidos
    @Scheduled(cron = "0 0 6 * * *") // cada día a las 6am
    public void alertDeadLetterJobs() {
        // Contar jobs en EN_DLQ del último día y enviar resumen al admin
    }
}
```

Handlers por tipo de job:
- `OcrJobHandler` — llama a `OcrServicePort`, actualiza `Invoice`
- `AiAnalysisJobHandler` — llama a `AiAnalysisPort`, guarda `AiAnalysis`
- `AutoPayJobHandler` — ejecuta `PaymentGatewayPort.initiatePayment()` automáticamente
- `NotificationJobHandler` — llama a `NotificationPort`, actualiza `NotificationLog`
- `ReconciliationJobHandler` — cruza `PaymentTransaction` con estado real de la pasarela

---

## 10. Resiliencia con Resilience4j

Envolver todas las llamadas a servicios externos con Resilience4j:

```java
// Configuración en application.yml:
resilience4j:
  retry:
    instances:
      ocrService:
        maxAttempts: 3
        waitDuration: 2s
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - java.io.IOException
          - org.springframework.web.client.ResourceAccessException
      paymentGateway:
        maxAttempts: 2
        waitDuration: 1s
  circuitbreaker:
    instances:
      ocrService:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
      paymentGateway:
        slidingWindowSize: 10
        failureRateThreshold: 30
        waitDurationInOpenState: 60s
  ratelimiter:
    instances:
      aiAnalysis:
        limitForPeriod: 10
        limitRefreshPeriod: 1s
```

---

## 11. Ideas de Alto Impacto — Implementación Detallada

### 11.1 Predicción de la Próxima Factura

**Objetivo:** Con 3 o más meses de historial, predecir el monto y consumo de la próxima factura.

**Implementación:**

1. Agregar a `AiAnalysis` el tipo `PREDICCION`.
2. El `AiAnalysisJobHandler` debe, cuando tenga al menos 3 facturas históricas del mismo proveedor y propiedad, incluir en el contexto enviado a la IA:
   - Historial de consumo mes a mes (cantidad y monto).
   - Mes del año (para capturar estacionalidad: verano/temporada de lluvia).
   - Precio promedio por unidad calculado de los últimos 3 meses.
3. La IA devuelve: `{ "monto_estimado": 85000, "consumo_estimado": 12.5, "rango_bajo": 75000, "rango_alto": 95000, "factores": ["Época de lluvia reduce consumo", "Tendencia decreciente últimos 2 meses"] }`.
4. Guardar como `AiAnalysis` con `tipo_analisis=PREDICCION`.
5. Endpoint de consulta: `GET /api/v1/ai-insights/predictions?propertyId=&serviceType=`.
6. Si la predicción supera el `presupuesto_mensual` configurado en `UserPreferences`, disparar una notificación proactiva.

### 11.2 Pago Automático Programado (AutoPay)

**Objetivo:** El sistema detecta facturas próximas a vencer y las paga automáticamente según las reglas configuradas.

**Job diario `AutoPayJobHandler`:**

```java
// Ejecutar cada día a las 9am
@Scheduled(cron = "0 0 9 * * *")
public void evaluateAutoPayRules() {
    // 1. Obtener todas las AutoPayRule activas
    // 2. Para cada regla:
    //    a. Buscar facturas del proveedor, en estado PENDIENTE
    //    b. Filtrar las que vencen en <= regla.diasAntesVencimiento días
    //    c. Si monto_total <= regla.montoMaximo (o montoMaximo es null):
    //       - Crear job de AUTOPAGO en JobQueue
    //       - Crear NotificationLog de tipo AUTOPAGO_EJECUTADO
    //    d. Si monto_total > regla.montoMaximo:
    //       - Crear NotificationLog de tipo AUTOPAGO_FALLIDO con motivo "Monto excede límite configurado"
    //       - NO pagar — notificar al usuario para que pague manualmente
}
```

**Consideraciones de seguridad del AutoPay:**
- El `monto_maximo` en `AutoPayRule` es una protección obligatoria: si la factura supera el límite, NO pagar y notificar.
- Registrar cada autopago en `AuditLog`.
- El usuario puede desactivar el autopago en cualquier momento desde `DELETE /api/v1/autopay/rules/{id}`.

### 11.3 Comparativa vs. Hogares Similares

**Objetivo:** Comparar el consumo de un hogar contra el promedio anonimizado de hogares similares en la misma ciudad con el mismo proveedor.

**Implementación:**

1. Crear una vista materializada (o tabla de agregados actualizada nightly) `consumption_benchmarks`:
```sql
CREATE MATERIALIZED VIEW consumption_benchmarks AS
SELECT
    p.ciudad,
    i.proveedor_id,
    DATE_TRUNC('month', i.fecha_emision) AS periodo,
    AVG(i.consumo_unidad) AS consumo_promedio,
    PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY i.consumo_unidad) AS percentil_25,
    PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY i.consumo_unidad) AS percentil_75,
    COUNT(DISTINCT p.user_id) AS numero_hogares
FROM invoices i
JOIN properties p ON i.property_id = p.id
WHERE i.deleted_at IS NULL
  AND i.consumo_unidad IS NOT NULL
  AND i.estado = 'PAGADA'
GROUP BY p.ciudad, i.proveedor_id, DATE_TRUNC('month', i.fecha_emision)
HAVING COUNT(DISTINCT p.user_id) >= 5; -- mínimo 5 hogares para anonimato
```

2. Agregar al tipo de análisis IA el campo `tipo_analisis=COMPARATIVA`.
3. El `AiPromptBuilderService` incluye en el contexto el benchmark de la ciudad si está disponible.
4. La IA devuelve: `{ "posicion_relativa": "BAJO_PROMEDIO|PROMEDIO|SOBRE_PROMEDIO", "diferencia_porcentual": -15.3, "mensaje": "Tu consumo de agua está 15% por debajo del promedio en Pasto. ¡Excelente!" }`.
5. Endpoint: `GET /api/v1/ai-insights/benchmark?propertyId=&serviceType=&period=`.

**Privacidad:** El benchmark solo se usa internamente para construir el contexto de la IA. Nunca exponer datos individuales de otros usuarios. El campo `numero_hogares` mínimo de 5 garantiza anonimato estadístico básico.

---

## 12. Conciliación de Pagos

**Problema:** La pasarela puede confirmar un pago, pero la empresa proveedora no lo registra. Esto genera disputas de "pagué pero me cortaron el servicio".

**Implementación del `ReconciliationJobHandler`:**

```java
// Ejecutar cada noche a las 2am
@Scheduled(cron = "0 0 2 * * *")
public void reconcilePayments() {
    // 1. Buscar PaymentTransactions en estado PENDIENTE_PSE
    //    con fecha_creacion > 24 horas atrás y < 72 horas atrás
    // 2. Para cada una: llamar a PaymentGatewayPort.getTransactionStatus()
    // 3. Si el estado externo difiere del estado interno: actualizar y notificar
    // 4. Buscar PaymentTransactions en estado APROBADA
    //    donde Invoice.estado != PAGADA (inconsistencia)
    // 5. Corregir la inconsistencia y registrar en AuditLog
    // 6. PSE pendiente > 72 horas: marcar como EXPIRADA y Invoice vuelve a PENDIENTE
    // 7. Generar reporte de conciliación diario en AuditLog
}
```

---

## 13. Variables de Entorno Requeridas

```bash
# Base de datos
DB_URL=jdbc:postgresql://localhost:5432/servicios_db
DB_USERNAME=...
DB_PASSWORD=...

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=...

# Seguridad
JWT_SECRET=...               # mínimo 256 bits, base64
JWT_ACCESS_EXPIRATION_MS=900000   # 15 minutos
JWT_REFRESH_EXPIRATION_MS=604800000 # 7 días
FIELD_ENCRYPTION_KEY=...     # AES-256, 256 bits, base64

# Pasarela de pagos
PAYMENT_GATEWAY_URL=...
PAYMENT_GATEWAY_API_KEY=...
PAYMENT_GATEWAY_WEBHOOK_SECRET=...   # clave HMAC para verificar webhooks

# Almacenamiento
STORAGE_ENDPOINT=...         # URL de S3 o MinIO
STORAGE_ACCESS_KEY=...
STORAGE_SECRET_KEY=...
STORAGE_BUCKET_NAME=facturas-prod
STORAGE_REGION=us-east-1

# IA
AI_SERVICE_API_KEY=...       # Claude API key
AI_SERVICE_MODEL=claude-opus-4-20250514

# Notificaciones
SENDGRID_API_KEY=...
EMAIL_FROM=noreply@tuapp.com
FIREBASE_CREDENTIALS_JSON=...

# App
APP_ENV=production           # local | staging | production
APP_INSTANCE_ID=instance-1   # para el campo worker_id en JobQueue
```

---

## 14. Migraciones con Flyway

Crear los scripts en `src/main/resources/db/migration/` con el patrón `V{version}__{descripcion}.sql`:

```
V1__create_users_table.sql
V2__create_properties_table.sql
V3__create_provider_companies_table.sql
V4__create_invoices_table.sql
V5__create_payment_transactions_table.sql
V6__create_ai_analysis_table.sql
V7__create_auto_pay_rules_table.sql
V8__create_notification_logs_table.sql
V9__create_user_preferences_table.sql
V10__create_job_queue_table.sql
V11__create_audit_log_table.sql
V12__create_refresh_tokens_table.sql
V13__create_consumption_benchmarks_view.sql
V14__create_indexes.sql
```

Cada script debe ser idempotente donde sea posible (`CREATE TABLE IF NOT EXISTS`, `CREATE INDEX IF NOT EXISTS`).

---

## 15. Testing — Cobertura Mínima

| Tipo | Herramienta | Cobertura mínima |
|---|---|---|
| Unitarios (Services) | JUnit 5 + Mockito | 80% de líneas en capa Service |
| Integración (Repositories) | Testcontainers (PostgreSQL real) | Todos los queries custom |
| Integración (Controllers) | MockMvc + Spring Security Test | Todos los endpoints |
| Seguridad | Casos con token inválido, expirado, ownership violado | 100% de casos límite |
| Webhook | Firma válida, firma inválida, payload duplicado | Obligatorio |

---

## 16. Documentación API

Configurar SpringDoc para generar Swagger UI en `/swagger-ui.html`:

```java
@Bean
public OpenAPI customOpenAPI() {
    return new OpenAPI()
        .info(new Info()
            .title("Gestión de Servicios Públicos API")
            .version("1.0.0")
            .description("API para gestión centralizada de facturas y pagos de servicios públicos"))
        .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
        .components(new Components()
            .addSecuritySchemes("Bearer Authentication",
                new SecurityScheme().type(SecurityScheme.Type.HTTP)
                    .scheme("bearer").bearerFormat("JWT")));
}
```

Todo endpoint debe tener anotaciones `@Operation`, `@ApiResponse` con los posibles códigos HTTP y los DTOs de respuesta bien documentados con `@Schema`.

---

## 17. Estructura de Paquetes

```
com.tuapp.servicios
├── config/
│   ├── SecurityConfig.java
│   ├── AsyncConfig.java
│   ├── RedisConfig.java
│   ├── OpenApiConfig.java
│   └── FlywayConfig.java
├── domain/
│   ├── model/           ← Entidades JPA
│   ├── enums/           ← Todos los enums del modelo
│   └── repository/      ← Interfaces Spring Data JPA
├── application/
│   ├── service/         ← Lógica de negocio
│   ├── dto/
│   │   ├── request/     ← DTOs de entrada
│   │   └── response/    ← DTOs de salida
│   ├── mapper/          ← MapStruct mappers
│   └── port/            ← Interfaces de integración externa
├── infrastructure/
│   ├── adapter/
│   │   ├── payment/     ← MockPaymentGatewayAdapter, WompiPaymentGatewayAdapter
│   │   ├── ocr/         ← MockOcrServiceAdapter, ClaudeOcrServiceAdapter
│   │   ├── ai/          ← MockAiAnalysisAdapter, ClaudeAiAnalysisAdapter
│   │   ├── storage/     ← S3FileStorageAdapter, MinIOFileStorageAdapter
│   │   └── notification/← MockNotificationAdapter, SendgridEmailAdapter
│   ├── scheduler/       ← JobQueueProcessor, AutoPayScheduler, ReconciliationScheduler
│   └── security/        ← JwtTokenProvider, WebhookSignatureValidator, EncryptedStringConverter
├── web/
│   ├── controller/      ← REST Controllers
│   ├── advice/          ← GlobalExceptionHandler
│   └── filter/          ← RateLimitingFilter, MdcLoggingFilter
└── ServiceApplication.java
```

---

## 18. Orden de Implementación Sugerido

Seguir este orden garantiza que cada capa tenga sus dependencias listas:

1. Configuración base: `pom.xml`, `application.yml`, `SecurityConfig`, `AsyncConfig`.
2. Migraciones Flyway (todas las tablas).
3. Entidades JPA (`BaseAuditEntity` primero, luego las demás en orden de dependencia).
4. Repositorios Spring Data JPA.
5. DTOs y Mappers.
6. Interfaces de puertos (`PaymentGatewayPort`, `OcrServicePort`, etc.) + implementaciones Mock.
7. `GlobalExceptionHandler` y excepciones personalizadas.
8. `FileValidationService`, `EncryptedStringConverter`, `WebhookSignatureValidator`.
9. Servicios de autenticación (Auth, JWT, Refresh Token).
10. Servicio de `JobQueue` + `JobQueueProcessor` (base sin handlers).
11. Servicios de negocio: Properties → Invoices → Payments → AiInsights → AutoPay → Notifications.
12. Controllers (en el mismo orden).
13. Handlers de jobs: `OcrJobHandler`, `AiAnalysisJobHandler`, `AutoPayJobHandler`, etc.
14. Schedulers: `AutoPayScheduler`, `ReconciliationJobHandler`.
15. Tests (unitarios e integración por cada capa).
16. Documentación SpringDoc.

---


