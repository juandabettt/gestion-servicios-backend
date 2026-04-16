# Tarea: Implementar endpoints GET /predictions y GET /benchmark en AiInsightsController

## Contexto
Backend Spring Boot 3.2.5. El frontend llama a estos dos endpoints que 
no existen en el backend causando error 500 "No static resource":
- GET /api/v1/ai-insights/predictions?propertyId=&serviceType=
- GET /api/v1/ai-insights/benchmark?propertyId=&serviceType=

## CAMBIO 1 — Agregar métodos en AiInsightsController.java

Archivo: src/main/java/com/tuapp/servicios/web/controller/AiInsightsController.java

Agregar estos dos endpoints al controller existente:

```java
@GetMapping("/predictions")
@Operation(summary = "Obtener predicción de próxima factura")
public ResponseEntity<AiAnalysisResponse> getPredictions(
        @RequestParam UUID propertyId,
        @RequestParam(defaultValue = "ENERGIA") TipoServicio serviceType,
        @AuthenticationPrincipal UserDetails userDetails) {
    UUID userId = resolveUserId(userDetails);
    return ResponseEntity.ok(aiInsightsService.getPrediction(propertyId, serviceType, userId));
}

@GetMapping("/benchmark")
@Operation(summary = "Obtener comparativa vs hogares similares")
public ResponseEntity<AiAnalysisResponse> getBenchmark(
        @RequestParam UUID propertyId,
        @RequestParam(defaultValue = "ENERGIA") TipoServicio serviceType,
        @AuthenticationPrincipal UserDetails userDetails) {
    UUID userId = resolveUserId(userDetails);
    return ResponseEntity.ok(aiInsightsService.getBenchmark(propertyId, serviceType, userId));
}
```

## CAMBIO 2 — Agregar métodos en AiInsightsService.java

Archivo: src/main/java/com/tuapp/servicios/application/service/AiInsightsService.java

Agregar estos dos métodos al service existente:

```java
@Transactional(readOnly = true)
public AiAnalysisResponse getPrediction(UUID propertyId, TipoServicio tipoServicio, UUID userId) {
    propertyService.validateOwnership(propertyId, userId);
    
    // Buscar análisis de tipo PREDICCION completado más reciente
    Optional<AiAnalysis> prediction = aiAnalysisRepository
        .findFirstByPropertyIdAndTipoServicioAndTipoAnalisisAndEstadoOrderByCreatedAtDesc(
            propertyId, tipoServicio, TipoAnalisis.PREDICCION, EstadoAnalisis.COMPLETADO);
    
    if (prediction.isPresent()) {
        return aiAnalysisMapper.toResponse(prediction.get());
    }
    
    // Si no hay predicción, devolver respuesta vacía con estado PROCESANDO
    // para que el frontend sepa que aún no hay datos
    AiAnalysis empty = AiAnalysis.builder()
        .property(propertyRepository.findById(propertyId)
            .orElseThrow(() -> new ResourceNotFoundException("Propiedad", propertyId)))
        .tipoAnalisis(TipoAnalisis.PREDICCION)
        .tipoServicio(tipoServicio)
        .descripcion("Sin datos suficientes para generar una predicción. " +
                    "Necesitas al menos 3 facturas históricas.")
        .estado(EstadoAnalisis.COMPLETADO)
        .build();
    
    return aiAnalysisMapper.toResponse(aiAnalysisRepository.save(empty));
}

@Transactional(readOnly = true)
public AiAnalysisResponse getBenchmark(UUID propertyId, TipoServicio tipoServicio, UUID userId) {
    propertyService.validateOwnership(propertyId, userId);
    
    // Buscar análisis de tipo COMPARATIVA completado más reciente
    Optional<AiAnalysis> benchmark = aiAnalysisRepository
        .findFirstByPropertyIdAndTipoServicioAndTipoAnalisisAndEstadoOrderByCreatedAtDesc(
            propertyId, tipoServicio, TipoAnalisis.COMPARATIVA, EstadoAnalisis.COMPLETADO);
    
    if (benchmark.isPresent()) {
        return aiAnalysisMapper.toResponse(benchmark.get());
    }
    
    // Si no hay benchmark, devolver respuesta vacía
    AiAnalysis empty = AiAnalysis.builder()
        .property(propertyRepository.findById(propertyId)
            .orElseThrow(() -> new ResourceNotFoundException("Propiedad", propertyId)))
        .tipoAnalisis(TipoAnalisis.COMPARATIVA)
        .tipoServicio(tipoServicio)
        .descripcion("Sin datos suficientes para comparativa. " +
                    "Se necesitan al menos 5 hogares en tu zona.")
        .estado(EstadoAnalisis.COMPLETADO)
        .build();
    
    return aiAnalysisMapper.toResponse(aiAnalysisRepository.save(empty));
}
```

## CAMBIO 3 — Agregar método en AiAnalysisRepository.java

Archivo: src/main/java/com/tuapp/servicios/domain/repository/AiAnalysisRepository.java

Agregar este método al repositorio existente:

```java
Optional<AiAnalysis> findFirstByPropertyIdAndTipoServicioAndTipoAnalisisAndEstadoOrderByCreatedAtDesc(
    UUID propertyId, 
    TipoServicio tipoServicio, 
    TipoAnalisis tipoAnalisis,
    EstadoAnalisis estado
);
```

## CAMBIO 4 — Agregar import en AiInsightsService.java

Verificar que estos imports existen en AiInsightsService.java,
agregar los que falten:

```java
import com.tuapp.servicios.domain.enums.TipoAnalisis;
import com.tuapp.servicios.domain.repository.PropertyRepository;
import com.tuapp.servicios.application.exception.ResourceNotFoundException;
import java.util.Optional;
```

También verificar que PropertyRepository está inyectado en el 
constructor de AiInsightsService. Si no está, agregarlo:

```java
private final PropertyRepository propertyRepository;
```

## Archivos a modificar
1. src/main/java/com/tuapp/servicios/web/controller/AiInsightsController.java
2. src/main/java/com/tuapp/servicios/application/service/AiInsightsService.java
3. src/main/java/com/tuapp/servicios/domain/repository/AiAnalysisRepository.java

## Instrucciones importantes
- NO ejecutes git add, git commit, git push ni ningún comando de git
- NO modifiques ningún otro archivo fuera de los listados
- NO cambies los endpoints ni métodos existentes
- Si algún import ya existe no lo dupliques
- Si PropertyRepository ya está inyectado no lo dupliques
- Después de cada cambio confirma qué fue modificado

## Verificación esperada
Al terminar el deploy en Railway:
1. GET /api/v1/ai-insights/predictions?propertyId=X&serviceType=ENERGIA 
   debe responder 200 con datos o mensaje de sin datos suficientes
2. GET /api/v1/ai-insights/benchmark?propertyId=X&serviceType=ENERGIA 
   debe responder 200 con datos o mensaje de sin datos suficientes
3. No debe aparecer "No static resource" en los logs
4. El Dashboard no debe mostrar errores 500 relacionados con AI