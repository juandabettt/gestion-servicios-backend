# Feature: Integrar Stripe para pagos reales

## Dependencias a agregar

En `pom.xml`, agregar:
```xml
<dependency>
    <groupId>com.stripe</groupId>
    <artifactId>stripe-java</artifactId>
    <version>24.0.0</version>
</dependency>
```

## Configuración en application.yml

En `src/main/resources/application.yml` agregar:
```yaml
stripe:
  secret-key: ${STRIPE_SECRET_KEY}
  public-key: ${STRIPE_PUBLIC_KEY}
```

## Crear StripeService.java

Crear archivo: `src/main/java/com/tuapp/servicios/application/service/StripeService.java`

```java
package com.tuapp.servicios.application.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeService {

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    public Map<String, Object> createPaymentIntent(BigDecimal amount, String invoiceId) throws StripeException {
        Stripe.apiKey = stripeSecretKey;

        // Convertir a centavos (Stripe usa centavos)
        long amountInCents = amount.multiply(BigDecimal.valueOf(100)).longValue();

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency("cop")
                .setDescription("Pago de factura: " + invoiceId)
                .putMetadata("invoiceId", invoiceId)
                .build();

        PaymentIntent paymentIntent = PaymentIntent.create(params);

        Map<String, Object> response = new HashMap<>();
        response.put("clientSecret", paymentIntent.getClientSecret());
        response.put("paymentIntentId", paymentIntent.getId());
        response.put("status", paymentIntent.getStatus());

        log.info("Payment Intent creado: {} para factura {}", paymentIntent.getId(), invoiceId);
        return response;
    }

    public PaymentIntent retrievePaymentIntent(String paymentIntentId) throws StripeException {
        Stripe.apiKey = stripeSecretKey;
        return PaymentIntent.retrieve(paymentIntentId);
    }
}
```

## Modificar PaymentController.java

Agregar endpoint para crear payment intent:

```java
@PostMapping("/create-payment-intent")
@Operation(summary = "Crear Payment Intent de Stripe")
public ResponseEntity<Map<String, Object>> createPaymentIntent(
        @RequestBody CreatePaymentIntentRequest request,
        @AuthenticationPrincipal UserDetails userDetails) {
    try {
        // Validar factura existe y pertenece al usuario
        Invoice invoice = ownershipValidator.validateAndGet(request.getInvoiceId(), getUserId(userDetails));
        
        // Validar que está en estado correcto
        if (invoice.getEstado() != EstadoFactura.PENDIENTE && 
            invoice.getEstado() != EstadoFactura.VENCIDA) {
            throw new BusinessException("La factura no puede ser pagada en estado: " + invoice.getEstado(), HttpStatus.BAD_REQUEST);
        }
        
        // Crear payment intent
        Map<String, Object> paymentIntent = stripeService.createPaymentIntent(
            invoice.getMontoTotal(), 
            invoice.getId().toString()
        );
        
        return ResponseEntity.ok(paymentIntent);
    } catch (Exception e) {
        log.error("Error creando payment intent: {}", e.getMessage());
        throw new BusinessException("Error al procesar el pago", HttpStatus.INTERNAL_SERVER_ERROR);
    }
}

@PostMapping("/confirm-payment")
@Operation(summary = "Confirmar pago de Stripe")
public ResponseEntity<InvoiceResponse> confirmPayment(
        @RequestBody ConfirmPaymentRequest request,
        @AuthenticationPrincipal UserDetails userDetails) {
    try {
        UUID userId = getUserId(userDetails);
        Invoice invoice = ownershipValidator.validateAndGet(request.getInvoiceId(), userId);
        
        // Recuperar payment intent de Stripe
        PaymentIntent paymentIntent = stripeService.retrievePaymentIntent(request.getPaymentIntentId());
        
        if (!"succeeded".equals(paymentIntent.getStatus())) {
            throw new BusinessException("El pago no fue exitoso", HttpStatus.BAD_REQUEST);
        }
        
        // Marcar factura como pagada
        invoice.setEstado(EstadoFactura.PAGADA);
        invoice.setFechaPago(LocalDateTime.now());
        invoice.setMetodoPago("stripe");
        invoiceRepository.save(invoice);
        
        log.info("Factura {} pagada exitosamente via Stripe", invoice.getId());
        
        return ResponseEntity.ok(invoiceMapper.toResponse(invoice));
    } catch (Exception e) {
        log.error("Error confirmando pago: {}", e.getMessage());
        throw new BusinessException("Error al confirmar el pago", HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
```

## DTOs a crear

Crear: `src/main/java/com/tuapp/servicios/application/dto/request/CreatePaymentIntentRequest.java`

```java
package com.tuapp.servicios.application.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.UUID;

@Data
public class CreatePaymentIntentRequest {
    @NotNull
    private UUID invoiceId;
}
```

Crear: `src/main/java/com/tuapp/servicios/application/dto/request/ConfirmPaymentRequest.java`

```java
package com.tuapp.servicios.application.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.UUID;

@Data
public class ConfirmPaymentRequest {
    @NotNull
    private UUID invoiceId;
    
    @NotNull
    private String paymentIntentId;
}
```

## Modificar Invoice.java

Agregar campos:
```java
@Column(name = "fecha_pago")
private LocalDateTime fechaPago;

@Column(name = "metodo_pago")
private String metodoPago;
```

## Migración Flyway

Crear: `src/main/resources/db/migration/V18__add_payment_fields_to_invoices.sql`

```sql
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS fecha_pago TIMESTAMP;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS metodo_pago VARCHAR(50);
```

## Variables en Railway

Agregar en Dashboard → Variables:
- `STRIPE_SECRET_KEY=sk_test_...`
- `STRIPE_PUBLIC_KEY=pk_test_...`

## Comandos finales

```bash
./mvnw -DskipTests clean package
git add .
git commit -m "feat: integrate Stripe for real payments"
git push origin main
```