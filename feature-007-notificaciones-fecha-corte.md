# FEATURE-007: Notificaciones de fecha de corte

## Contexto
El proyecto tiene una tabla de notificaciones y un `MockNotificationAdapter` en el backend. Se necesita implementar notificaciones reales que avisen al usuario cuando una factura está próxima a vencer.

## Reglas de negocio
- Notificar cuando faltan **7 días** para la fecha de vencimiento
- Notificar cuando faltan **3 días** para la fecha de vencimiento
- Notificar cuando la factura **venció hoy**
- No enviar la misma notificación dos veces para la misma factura y mismo tipo

---

## BACKEND

### 1. Verificar entidad Notification
Busca `Notification.java` y verifica que tenga los campos:
- `usuarioId` (UUID)
- `facturaId` (UUID)
- `tipo` (String) — valores: `VENCE_EN_7_DIAS`, `VENCE_EN_3_DIAS`, `VENCIDA_HOY`
- `titulo` (String)
- `mensaje` (String)
- `leida` (boolean, default false)
- `createdAt` (LocalDateTime)

Si faltan campos agrégalos y crea la migración Flyway correspondiente con el número siguiente al último existente.

### 2. Crear scheduler de notificaciones
Crea `src/main/java/com/tuapp/servicios/infrastructure/scheduler/handler/NotificationScheduler.java`:

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationScheduler {

    private final InvoiceRepository invoiceRepository;
    private final NotificationRepository notificationRepository;

    @Scheduled(cron = "0 0 8 * * *") // Todos los días a las 8am
    public void generarNotificacionesVencimiento() {
        log.info("Ejecutando scheduler de notificaciones de vencimiento");

        LocalDate hoy = LocalDate.now();
        LocalDate en3Dias = hoy.plusDays(3);
        LocalDate en7Dias = hoy.plusDays(7);

        procesarNotificaciones(en7Dias, "VENCE_EN_7_DIAS",
            "Factura próxima a vencer",
            "Tu factura vence en 7 días. Recuerda pagarla a tiempo.");

        procesarNotificaciones(en3Dias, "VENCE_EN_3_DIAS",
            "¡Factura vence pronto!",
            "Tu factura vence en 3 días. Evita recargos pagando hoy.");

        procesarNotificaciones(hoy, "VENCIDA_HOY",
            "Factura vencida hoy",
            "Tu factura venció hoy. Paga ahora para evitar suspensión del servicio.");
    }

    private void procesarNotificaciones(LocalDate fecha, String tipo, String titulo, String mensaje) {
        List<Invoice> facturas = invoiceRepository
            .findByFechaVencimientoAndEstado(fecha, "PENDIENTE");

        facturas.forEach(factura -> {
            boolean yaExiste = notificationRepository
                .existsByFacturaIdAndTipo(factura.getId(), tipo);

            if (!yaExiste) {
                Notification notif = new Notification();
                notif.setUsuarioId(factura.getUsuarioId());
                notif.setFacturaId(factura.getId());
                notif.setTipo(tipo);
                notif.setTitulo(titulo);
                notif.setMensaje(mensaje + " Monto: $" + factura.getMontoTotal());
                notif.setLeida(false);
                notif.setCreatedAt(LocalDateTime.now());
                notificationRepository.save(notif);
                log.info("Notificación creada: {} para factura {}", tipo, factura.getId());
            }
        });
    }
}
```

### 3. Agregar queries al repositorio
En `InvoiceRepository.java` agrega si no existe:
```java
List<Invoice> findByFechaVencimientoAndEstado(LocalDate fecha, String estado);
```

En `NotificationRepository.java` agrega si no existe:
```java
boolean existsByFacturaIdAndTipo(UUID facturaId, String tipo);
Page<Notification> findByUsuarioIdAndLeidaFalseOrderByCreatedAtDesc(UUID usuarioId, Pageable pageable);
Page<Notification> findByUsuarioIdOrderByCreatedAtDesc(UUID usuarioId, Pageable pageable);
```

### 4. Endpoint para obtener notificaciones
En `NotificationController.java` verifica que exista o crea el endpoint:
```java
@GetMapping
@PreAuthorize("hasRole('USER')")
public ResponseEntity<Page<NotificationDTO>> getNotifications(
    @AuthenticationPrincipal UserDetails userDetails,
    @RequestParam(defaultValue = "false") boolean soloNoLeidas,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size
) {
    return ResponseEntity.ok(
        notificationService.getByUsuario(userDetails.getUsername(), soloNoLeidas, PageRequest.of(page, size))
    );
}

@PutMapping("/{id}/leer")
@PreAuthorize("hasRole('USER')")
public ResponseEntity<Void> marcarLeida(@PathVariable UUID id) {
    notificationService.marcarLeida(id);
    return ResponseEntity.noContent().build();
}

@PutMapping("/leer-todas")
@PreAuthorize("hasRole('USER')")
public ResponseEntity<Void> marcarTodasLeidas(@AuthenticationPrincipal UserDetails userDetails) {
    notificationService.marcarTodasLeidas(userDetails.getUsername());
    return ResponseEntity.noContent().build();
}
```

### 5. Verificar @EnableScheduling
En `ServiceApplication.java` verifica que tenga `@EnableScheduling`. Si no lo tiene, agrégalo.

---

## FRONTEND

### 6. Actualizar página de Notificaciones
En `src/pages/Notifications.jsx` reemplaza cualquier dato mock por datos reales:

```javascript
const { data, isLoading } = useQuery({
  queryKey: ['notifications'],
  queryFn: () => apiClient.get('/notifications').then(r => r.data),
  retry: false,
  throwOnError: false,
})

const notifications = data?.content || data || []
```

### 7. Mostrar badge con conteo en el ícono de notificaciones
En `src/components/Header.jsx` (o donde esté el ícono de la campana), muestra el conteo de notificaciones no leídas:

```javascript
const { data: unreadData } = useQuery({
  queryKey: ['notifications-unread-count'],
  queryFn: () => apiClient.get('/notifications?soloNoLeidas=true').then(r => r.data),
  refetchInterval: 60000, // refresca cada minuto
  retry: false,
  throwOnError: false,
})

const unreadCount = unreadData?.totalElements || 0

// En el JSX del botón de notificaciones:
{unreadCount > 0 && (
  <span className="absolute -top-1 -right-1 bg-red-500 text-white text-xs rounded-full w-5 h-5 flex items-center justify-center font-bold">
    {unreadCount > 9 ? '9+' : unreadCount}
  </span>
)}
```

### 8. Botón "Marcar todas como leídas"
En `src/pages/Notifications.jsx` agrega el botón:

```javascript
const queryClient = useQueryClient()

const marcarTodasMutation = useMutation({
  mutationFn: () => apiClient.put('/notifications/leer-todas'),
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: ['notifications'] })
    queryClient.invalidateQueries({ queryKey: ['notifications-unread-count'] })
    toast.success('Todas las notificaciones marcadas como leídas')
  }
})
```

---

## Archivos backend a modificar
- `Notification.java` — verificar/agregar campos
- `NotificationScheduler.java` — CREAR
- `InvoiceRepository.java` — agregar query por fecha y estado
- `NotificationRepository.java` — agregar queries
- `NotificationController.java` — verificar/agregar endpoints
- `ServiceApplication.java` — verificar @EnableScheduling
- Migración Flyway si faltan columnas en la tabla notifications

## Archivos frontend a modificar
- `src/pages/Notifications.jsx` — usar datos reales
- `src/components/Header.jsx` — badge con conteo no leídas

## NO modificar
- Lógica de pagos
- Lógica de facturas
- Autenticación
