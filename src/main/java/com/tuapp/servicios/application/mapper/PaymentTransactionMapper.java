package com.tuapp.servicios.application.mapper;

import com.tuapp.servicios.application.dto.response.PaymentTransactionResponse;
import com.tuapp.servicios.domain.model.PaymentTransaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentTransactionMapper {
    @Mapping(source = "factura.id", target = "facturaId")
    PaymentTransactionResponse toResponse(PaymentTransaction transaction);
}
