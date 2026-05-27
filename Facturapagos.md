# Fix NullPointerException en NotificationService

## Objetivo
Agregar null checks defensivos en `NotificationService` para evitar NullPointerException cuando `invoice.getProveedor()` es null. Usar el nombre de la propiedad como fallback.

## Contexto
Cuando se procesa un pago exitoso o se envía notificación de factura por vencer/autopago, la aplicación explota con:
```
Cannot invoke "ProviderCompany.getNombre()" because "Invoice.getProveedor()" is null
```

Solución: Si no hay proveedor, usar `invoice.getProperty().getNombre()` como fallback.

---

## Archivo a modificar
`src/main/java/com/tuapp/servicios/application/service/NotificationService.java`

---

## Cambio 1 — Método `notificarPagoConfirmado()`

**Ubicación:** Línea 50

**Encontrar:**
```java
    @Transactional
    public void notificarPagoConfirmado(Invoice invoice, PaymentTransaction transaction) {
        User user = invoice.getProperty().getUser();
        String monto = invoice.getMontoTotal() != null ? invoice.getMontoTotal().toPlainString() : "?";
        crearNotificacionInApp(user.getId(), invoice.getId(),
                TipoNotificacion.PAGO_EXITOSO.name(),
                "Pago realizado",
                "Tu pago de $" + monto + " fue procesado exitosamente.");
        NotificationLog n = createAndSave(user, TipoNotificacion.PAGO_CONFIRMADO,
                "Pago confirmado — " + invoice.getProveedor().getNombre(),
                "Tu pago fue procesado exitosamente", invoice.getId());
        sendEmailIfEnabled(user, n);
    }
```

**Reemplazar por:**
```java
    @Transactional
    public void notificarPagoConfirmado(Invoice invoice, PaymentTransaction transaction) {
        User user = invoice.getProperty().getUser();
        String monto = invoice.getMontoTotal() != null ? invoice.getMontoTotal().toPlainString() : "?";
        crearNotificacionInApp(user.getId(), invoice.getId(),
                TipoNotificacion.PAGO_EXITOSO.name(),
                "Pago realizado",
                "Tu pago de $" + monto + " fue procesado exitosamente.");
        String proveedorNombre = invoice.getProveedor() != null 
            ? invoice.getProveedor().getNombre() 
            : invoice.getProperty().getNombre();
        NotificationLog n = createAndSave(user, TipoNotificacion.PAGO_CONFIRMADO,
                "Pago confirmado — " + proveedorNombre,
                "Tu pago fue procesado exitosamente", invoice.getId());
        sendEmailIfEnabled(user, n);
    }
```

---

## Cambio 2 — Método `notificarFacturaPorVencer()`

**Ubicación:** Línea 64

**Encontrar:**
```java
    @Transactional
    public void notificarFacturaPorVencer(Invoice invoice, int diasRestantes) {
        User user = invoice.getProperty().getUser();
        NotificationLog n = createAndSave(user, TipoNotificacion.FACTURA_POR_VENCER,
                "Tu factura vence en " + diasRestantes + " días",
                "La factura de " + invoice.getProveedor().getNombre() + " vence pronto", invoice.getId());
        sendEmailIfEnabled(user, n);
    }
```

**Reemplazar por:**
```java
    @Transactional
    public void notificarFacturaPorVencer(Invoice invoice, int diasRestantes) {
        User user = invoice.getProperty().getUser();
        String proveedorNombre = invoice.getProveedor() != null 
            ? invoice.getProveedor().getNombre() 
            : invoice.getProperty().getNombre();
        NotificationLog n = createAndSave(user, TipoNotificacion.FACTURA_POR_VENCER,
                "Tu factura vence en " + diasRestantes + " días",
                "La factura de " + proveedorNombre + " vence pronto", invoice.getId());
        sendEmailIfEnabled(user, n);
    }
```

---

## Cambio 3 — Método `notificarAutoPagoEjecutado()`

**Ubicación:** Línea 90

**Encontrar:**
```java
    @Transactional
    public void notificarAutoPagoEjecutado(User user, Invoice invoice) {
        String monto = invoice.getMontoTotal() != null ? invoice.getMontoTotal().toPlainString() : "?";
        crearNotificacionInApp(user.getId(), invoice.getId(),
                TipoNotificacion.AUTOPAGO_EJECUTADO.name(),
                "Autopago ejecutado",
                "Se pagó automáticamente tu factura de $" + monto + ".");
        createAndSave(user, TipoNotificacion.AUTOPAGO_EJECUTADO,
                "Autopago ejecutado",
                "Se ejecutó un pago automático para " + invoice.getProveedor().getNombre(), invoice.getId());
    }
```

**Reemplazar por:**
```java
    @Transactional
    public void notificarAutoPagoEjecutado(User user, Invoice invoice) {
        String monto = invoice.getMontoTotal() != null ? invoice.getMontoTotal().toPlainString() : "?";
        crearNotificacionInApp(user.getId(), invoice.getId(),
                TipoNotificacion.AUTOPAGO_EJECUTADO.name(),
                "Autopago ejecutado",
                "Se pagó automáticamente tu factura de $" + monto + ".");
        String proveedorNombre = invoice.getProveedor() != null 
            ? invoice.getProveedor().getNombre() 
            : invoice.getProperty().getNombre();
        createAndSave(user, TipoNotificacion.AUTOPAGO_EJECUTADO,
                "Autopago ejecutado",
                "Se ejecutó un pago automático para " + proveedorNombre, invoice.getId());
    }
```

---

## Validación posterior

Una vez aplicado, el flujo de pago debería:
1. ✅ Seleccionar factura
2. ✅ Seleccionar método de pago (Nequi/PSE/Tarjeta)
3. ✅ Rellenar datos
4. ✅ Click "Confirmar pago"
5. ✅ Sin error 500 → Mostrar "Pago realizado correctamente"
6. ✅ Notificación creada sin NullPointerException

## Restricciones
- NO cambiar la lógica de validación de estado de factura
- NO cambiar nombres de métodos
- NO agregar nuevas dependencias