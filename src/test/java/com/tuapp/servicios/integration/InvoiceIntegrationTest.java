package com.tuapp.servicios.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de integración de seguridad para el endpoint de facturas.
 *
 * Valida que cualquier petición sin token JWT al endpoint /api/v1/invoices/upload
 * sea rechazada con 401 Unauthorized antes de llegar al controlador,
 * previniendo regresiones en la configuración de seguridad.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class InvoiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("servicios_test")
            .withUsername("test")
            .withPassword("test");

    /**
     * Sobreescribe las propiedades de datasource en tiempo de ejecución para apuntar
     * al contenedor levantado por Testcontainers. También reemplaza el
     * driver-class-name definido en application-test.yml (ContainerDatabaseDriver)
     * para que sea compatible con la URL estándar JDBC que devuelve el contenedor.
     */
    @DynamicPropertySource
    static void configureDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private MockMvc mockMvc;

    /**
     * Escenario: POST a /api/v1/invoices/upload sin token JWT.
     * Resultado esperado: 401 Unauthorized.
     * La petición debe ser rechazada por el JwtAuthenticationFilter
     * sin llegar al InvoiceController.
     */
    @Test
    void shouldRejectInvoiceUploadWhenNotAuthenticated() throws Exception {
        mockMvc.perform(
                post("/api/v1/invoices/upload")
                        .param("propertyId", "649c8bb7-6768-4ce4-997d-d0cff1a13d85")
        ).andExpect(status().isUnauthorized());
    }
}
