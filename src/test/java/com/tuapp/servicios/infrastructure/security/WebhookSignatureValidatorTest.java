package com.tuapp.servicios.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureValidatorTest {

    private WebhookSignatureValidator validator;
    private static final String SECRET = "test-webhook-secret-2025";

    @BeforeEach
    void setUp() {
        validator = new WebhookSignatureValidator();
        ReflectionTestUtils.setField(validator, "webhookSecret", SECRET);
    }

    @Test
    void isValid_withCorrectSignature_returnsTrue() throws Exception {
        String payload = "{\"event\":\"PAYMENT_APPROVED\",\"transactionId\":\"TXN-123\"}";
        String signature = computeHmac(payload, SECRET);

        assertThat(validator.isValid(payload, signature)).isTrue();
    }

    @Test
    void isValid_withWrongSignature_returnsFalse() {
        String payload = "{\"event\":\"PAYMENT_APPROVED\"}";
        assertThat(validator.isValid(payload, "firma-incorrecta")).isFalse();
    }

    @Test
    void isValid_withNullPayload_returnsFalse() {
        assertThat(validator.isValid(null, "any-signature")).isFalse();
    }

    @Test
    void isValid_withNullSignature_returnsFalse() {
        assertThat(validator.isValid("{\"test\":true}", null)).isFalse();
    }

    @Test
    void isValid_withTamperedPayload_returnsFalse() throws Exception {
        String originalPayload = "{\"amount\":100}";
        String signature = computeHmac(originalPayload, SECRET);
        String tamperedPayload = "{\"amount\":99999}";

        assertThat(validator.isValid(tamperedPayload, signature)).isFalse();
    }

    private String computeHmac(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }
}
