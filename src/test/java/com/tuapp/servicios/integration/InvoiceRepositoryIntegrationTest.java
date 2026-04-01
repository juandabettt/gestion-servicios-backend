package com.tuapp.servicios.integration;

import com.tuapp.servicios.domain.enums.*;
import com.tuapp.servicios.domain.model.*;
import com.tuapp.servicios.domain.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class InvoiceRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("servicios_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PropertyRepository propertyRepository;
    @Autowired private ProviderCompanyRepository providerRepository;

    @Test
    void findByUserId_returnsInvoicesForUser() {
        User user = userRepository.save(User.builder()
                .nombre("Test User").email("test-inv@example.com")
                .passwordHash("hash").rol(RolUsuario.USER).activo(true).build());

        Property property = propertyRepository.save(Property.builder()
                .user(user).nombre("Casa Test").esPrincipal(true).build());

        ProviderCompany provider = providerRepository.save(ProviderCompany.builder()
                .nombre("Empresa Test").nit("900123456-1")
                .tipoServicio(TipoServicio.ENERGIA)
                .codigoConvenioRecaudo("COD-001").activo(true).build());

        invoiceRepository.save(Invoice.builder()
                .property(property).proveedor(provider)
                .numeroReferencia("REF-001")
                .fechaEmision(LocalDate.now().minusMonths(1))
                .fechaVencimiento(LocalDate.now().plusDays(10))
                .montoTotal(new BigDecimal("120000.00"))
                .estado(EstadoFactura.PENDIENTE).build());

        Page<Invoice> page = invoiceRepository.findByUserId(user.getId(), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getEstado()).isEqualTo(EstadoFactura.PENDIENTE);
    }

    @Test
    void findAllPendientesProximasVencer_returnsCorrectInvoices() {
        User user = userRepository.save(User.builder()
                .nombre("Test 2").email("test-venc@example.com")
                .passwordHash("hash").rol(RolUsuario.USER).activo(true).build());

        Property property = propertyRepository.save(Property.builder()
                .user(user).nombre("Apt Test").esPrincipal(false).build());

        ProviderCompany provider = providerRepository.save(ProviderCompany.builder()
                .nombre("Gas Test").nit("800456789-2")
                .tipoServicio(TipoServicio.GAS)
                .codigoConvenioRecaudo("GAS-001").activo(true).build());

        // Esta factura vence en 3 días — debe aparecer
        invoiceRepository.save(Invoice.builder()
                .property(property).proveedor(provider)
                .numeroReferencia("REF-VENC-001")
                .fechaEmision(LocalDate.now().minusMonths(1))
                .fechaVencimiento(LocalDate.now().plusDays(3))
                .montoTotal(new BigDecimal("85000.00"))
                .estado(EstadoFactura.PENDIENTE).build());

        // Esta factura vence en 10 días — NO debe aparecer con límite de 5 días
        invoiceRepository.save(Invoice.builder()
                .property(property).proveedor(provider)
                .numeroReferencia("REF-VENC-002")
                .fechaEmision(LocalDate.now().minusMonths(1))
                .fechaVencimiento(LocalDate.now().plusDays(10))
                .montoTotal(new BigDecimal("90000.00"))
                .estado(EstadoFactura.PENDIENTE).build());

        List<Invoice> result = invoiceRepository
                .findAllPendientesProximasVencer(LocalDate.now().plusDays(5));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNumeroReferencia()).isEqualTo("REF-VENC-001");
    }
}
