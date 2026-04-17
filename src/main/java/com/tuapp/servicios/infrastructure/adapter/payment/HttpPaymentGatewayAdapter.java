package com.tuapp.servicios.infrastructure.adapter.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.tuapp.servicios.application.port.PaymentGatewayPort;
import com.tuapp.servicios.application.port.dto.PaymentInitiationResult;
import com.tuapp.servicios.application.port.dto.PaymentRequest;
import com.tuapp.servicios.application.port.dto.RefundResult;
import com.tuapp.servicios.application.port.dto.TransactionStatusResult;
import com.tuapp.servicios.domain.enums.EstadoTransaccion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@Profile("local")
@Slf4j
public class HttpPaymentGatewayAdapter implements PaymentGatewayPort {

    private final WebClient webClient;

    public HttpPaymentGatewayAdapter(@Value("${payment.gateway.url}") String gatewayUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(gatewayUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public PaymentInitiationResult initiatePayment(PaymentRequest request) {
        log.info("HTTP Gateway: iniciando pago para factura {}, método {}",
                request.getInvoiceId(), request.getMetodoPago());

        Map<String, Object> body = new HashMap<>();
        body.put("invoiceId", request.getInvoiceId().toString());
        body.put("amount", request.getAmount());
        body.put("metodoPago", request.getMetodoPago().name());
        body.put("codigoConvenio", request.getCodigoConvenioRecaudo() != null ? request.getCodigoConvenioRecaudo() : "");
        body.put("idempotencyKey", request.getIdempotencyKey() != null ? request.getIdempotencyKey() : "");

        try {
            JsonNode response = webClient.post()
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .map(errorBody -> new WebClientResponseException(
                                            clientResponse.statusCode().value(),
                                            errorBody, null, null, null)))
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(3))
                    .block();

            return buildApprovedResult(response);

        } catch (WebClientResponseException ex) {
            return mapHttpErrorToResult(ex);
        } catch (Exception ex) {
            log.warn("HTTP Gateway: timeout o error de conexión: {}", ex.getMessage());
            return PaymentInitiationResult.builder()
                    .gatewayTransactionId("FALLIDO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                    .estado(EstadoTransaccion.RECHAZADA)
                    .mensaje("Pasarela no disponible, intente más tarde")
                    .exitoso(false)
                    .build();
        }
    }

    @Override
    public TransactionStatusResult getTransactionStatus(String gatewayTransactionId) {
        return TransactionStatusResult.builder()
                .gatewayTransactionId(gatewayTransactionId)
                .estado(EstadoTransaccion.INICIADA)
                .descripcion("Consulta de estado no soportada por el simulador Mountebank")
                .build();
    }

    @Override
    public RefundResult refund(String gatewayTransactionId, BigDecimal amount) {
        return RefundResult.builder()
                .refundId("REFUND-MB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .montoReembolsado(amount)
                .exitoso(true)
                .mensaje("Reembolso simulado por Mountebank")
                .build();
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signature) {
        return "mock-valid-signature".equals(signature);
    }

    private PaymentInitiationResult buildApprovedResult(JsonNode response) {
        String transactionId = (response != null && response.has("transactionId") && !response.get("transactionId").isNull())
                ? response.get("transactionId").asText()
                : "GW-MB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        String pseUrl = (response != null && response.has("pseUrl") && !response.get("pseUrl").isNull())
                ? response.get("pseUrl").asText()
                : null;

        EstadoTransaccion estado = (pseUrl != null && !pseUrl.isBlank())
                ? EstadoTransaccion.PENDIENTE_PSE
                : EstadoTransaccion.APROBADA;

        log.info("HTTP Gateway: pago aprobado, transactionId={}", transactionId);
        return PaymentInitiationResult.builder()
                .gatewayTransactionId(transactionId)
                .estado(estado)
                .urlRedireccionPse(pseUrl)
                .mensaje("Pago procesado exitosamente")
                .exitoso(true)
                .build();
    }

    private PaymentInitiationResult mapHttpErrorToResult(WebClientResponseException ex) {
        int status = ex.getStatusCode().value();
        log.warn("HTTP Gateway: error HTTP {} de la pasarela: {}", status, ex.getResponseBodyAsString());

        String mensaje = switch (status) {
            case 400 -> "Pago rechazado: saldo insuficiente";
            case 403 -> "Pago bloqueado: alerta de fraude detectada";
            case 502 -> "Error en banco origen, intente más tarde";
            case 504 -> "Timeout de pasarela, intente más tarde";
            default  -> "Error en procesamiento del pago (HTTP " + status + ")";
        };

        return PaymentInitiationResult.builder()
                .gatewayTransactionId("ERR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .estado(EstadoTransaccion.RECHAZADA)
                .mensaje(mensaje)
                .exitoso(false)
                .build();
    }
}
