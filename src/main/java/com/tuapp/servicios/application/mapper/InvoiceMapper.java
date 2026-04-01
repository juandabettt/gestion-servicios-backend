package com.tuapp.servicios.application.mapper;

import com.tuapp.servicios.application.dto.response.InvoiceResponse;
import com.tuapp.servicios.domain.model.Invoice;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface InvoiceMapper {
    @Mapping(source = "property.id", target = "propertyId")
    @Mapping(source = "property.nombre", target = "propertyNombre")
    @Mapping(source = "proveedor.id", target = "proveedorId")
    @Mapping(source = "proveedor.nombre", target = "proveedorNombre")
    @Mapping(source = "proveedor.tipoServicio", target = "tipoServicio")
    InvoiceResponse toResponse(Invoice invoice);
}
