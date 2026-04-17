# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Start all infrastructure (PostgreSQL on 5433, Redis 6379, MinIO 9000/9001)
docker-compose up -d

# Build (skip tests)
./mvnw clean package -DskipTests

# Run locally (uses APP_ENV=local profile by default)
./mvnw spring-boot:run

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=PaymentServiceTest

# Run a single test method
./mvnw test -Dtest=PaymentServiceTest#shouldRejectDuplicateIdempotencyKey
```

Swagger UI is available at `http://localhost:8080/swagger-ui.html` when running locally.

## Architecture

Hexagonal / Ports & Adapters with strict layering:

```
web/controller  →  application/service  →  domain/repository
                         ↓
              application/port (interfaces)
                         ↓
         infrastructure/adapter (implementations)
```

- **Controllers** (`web/controller/`) receive and return DTOs only — never JPA entities.
- **Services** (`application/service/`) hold all business logic. Never expose entities outward.
- **Ports** (`application/port/`) are pure Java interfaces for external integrations.
- **Adapters** (`infrastructure/adapter/`) implement ports; selected by Spring `@Profile`.
- **Mappers** (`application/mapper/`) use MapStruct; never map inline in services or controllers.
- **Domain** (`domain/model/`, `domain/enums/`, `domain/repository/`) holds JPA entities, enums, and Spring Data repository interfaces.

### Profile-based adapter selection

| Adapter type | `local` profile | `production` profile | `test` profile |
|---|---|---|---|
| Payment gateway | `MockPaymentGatewayAdapter` | `WompiPaymentGatewayAdapter` | `MockPaymentGatewayAdapter` |
| OCR | `MockOcrServiceAdapter` | `ClaudeOcrServiceAdapter` | `MockOcrServiceAdapter` |
| AI analysis | `MockAiAnalysisAdapter` | `ClaudeAiAnalysisAdapter` | `MockAiAnalysisAdapter` |
| File storage | `MinIOFileStorageAdapter` | `CloudinaryFileStorageAdapter` / `S3FileStorageAdapter` | — |
| Notifications | `MockNotificationAdapter` | `SendgridEmailAdapter` | `MockNotificationAdapter` |

Active profile is driven by `APP_ENV` env var (defaults to `local`).

### Async / Job system

Critical tasks (OCR, payments, AI analysis, notifications) go through a `job_queue` PostgreSQL table instead of raw `@Async`. `JobQueueProcessor` polls every 10 seconds with `SELECT ... FOR UPDATE SKIP LOCKED` for multi-instance safety. Handlers: `OcrJobHandler`, `AiAnalysisJobHandler`, `AutoPayJobHandler`, `NotificationJobHandler`, `ReconciliationJobHandler`.

### Security

- JWT access tokens (15 min) + refresh tokens (7 days, rotated on use, stored in DB).
- JTI of active access token stored in Redis for immediate revocation on logout.
- HMAC-SHA256 webhook signature verification via `WebhookSignatureValidator` — constant-time comparison.
- Idempotency keys stored in Redis (24h TTL) to prevent duplicate payments.
- `InvoiceOwnershipValidator` (and equivalent for other entities) — ownership checked in service layer, not just roles.
- Sensitive fields (`numero_referencia`, `id_transaccion_pasarela`) encrypted at rest with AES-256-GCM via `EncryptedStringConverter` JPA `AttributeConverter`. Key from env var `FIELD_ENCRYPTION_KEY`.
- Rate limiting via Bucket4j on login, register, upload, AI analysis endpoints.

## Critical Conventions

### Money
All monetary fields are `BigDecimal`. `Double`/`Float`/`double` are forbidden for money. DB column: `NUMERIC(19,4)`. JPA: `@Column(precision=19, scale=4)`.

### Soft delete
All financial entities use soft delete (`deleted_at` column) via `BaseAuditEntity`. Never run physical `DELETE` on `invoices` or `payment_transactions`. Use `@Where(clause = "deleted_at IS NULL")` for automatic filtering.

### Logging
SLF4J + Logback with MDC `traceId` on every log line. Never log: transaction amounts, invoice reference numbers, JWT tokens, passwords, or card data. Log pattern includes `[%X{traceId}]`.

### Exception handling
Single `GlobalExceptionHandler` (`@RestControllerAdvice`) returns RFC 7807 Problem Details JSON. Never expose Hibernate/SQL messages or stack traces to clients.

### Pagination
Every collection endpoint accepts `Pageable` and returns `Page<T>` — no exceptions.

## Database Migrations

Flyway scripts in `src/main/resources/db/migration/` following `V{n}__{description}.sql` naming. Tests use Testcontainers (PostgreSQL via JDBC URL `jdbc:tc:postgresql:15-alpine:///`) with `ddl-auto: create-drop` and Flyway disabled.

## Key Environment Variables

```
DB_URL / DB_USERNAME / DB_PASSWORD
REDIS_HOST / REDIS_PORT / REDIS_PASSWORD
JWT_SECRET / FIELD_ENCRYPTION_KEY
PAYMENT_GATEWAY_URL / PAYMENT_GATEWAY_API_KEY / PAYMENT_GATEWAY_WEBHOOK_SECRET
STORAGE_ENDPOINT / STORAGE_ACCESS_KEY / STORAGE_SECRET_KEY / STORAGE_BUCKET_NAME
AI_SERVICE_API_KEY / ANTHROPIC_API_KEY
APP_ENV (local | staging | production) / APP_INSTANCE_ID
```

Local defaults are hardcoded in `application.yml` so the app starts without any env vars set.