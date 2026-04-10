# Tarea: Corregir configuración de perfiles Spring para despliegue en Railway

## Contexto
El proyecto es un backend Spring Boot 3.2.5 con Java 21. Al desplegarse en Railway 
con el perfil "production" activo, la aplicación falla al iniciar porque varios 
adaptadores Mock solo están configurados para el perfil "local", dejando sin 
implementación varias interfaces de puerto requeridas por Spring.

## Error actual en Railway
"No qualifying bean of type 'com.tuapp.servicios.application.port.NotificationPort' 
available: expected at least 1 bean which qualifies as autowire candidate."

## Causa raíz
Los adaptadores Mock tienen @Profile("local") pero en Railway el perfil activo es 
"production". No existen adaptadores reales para production en estas interfaces, 
por lo que los Mock deben funcionar en ambos perfiles temporalmente.

## Archivos a modificar

### 1. MockNotificationAdapter.java
Ruta: src/main/java/com/tuapp/servicios/infrastructure/adapter/notification/MockNotificationAdapter.java
Cambio: Reemplazar @Profile("local") por @Profile({"local", "production"})

### 2. MockAiAnalysisAdapter.java
Ruta: src/main/java/com/tuapp/servicios/infrastructure/adapter/ai/MockAiAnalysisAdapter.java
Cambio: Reemplazar @Profile("local") por @Profile({"local", "production"})

### 3. MockOcrServiceAdapter.java
Ruta: src/main/java/com/tuapp/servicios/infrastructure/adapter/ocr/MockOcrServiceAdapter.java
Cambio: Reemplazar @Profile("local") por @Profile({"local", "production"})

### 4. MockPaymentGatewayAdapter.java
Ruta: src/main/java/com/tuapp/servicios/infrastructure/adapter/payment/MockPaymentGatewayAdapter.java
Cambio: Reemplazar @Profile("local") por @Profile({"local", "production"})

### 5. MockGatewayTestController.java
Ruta: src/main/java/com/tuapp/servicios/web/controller/MockGatewayTestController.java
Cambio: Reemplazar @Profile("local") por @Profile({"local", "production"})

### 6. MinIOFileStorageAdapter.java
Ruta: src/main/java/com/tuapp/servicios/infrastructure/adapter/storage/MinIOFileStorageAdapter.java
NO modificar el @Profile. Este archivo está correcto tal como está.
NOTA: El S3FileStorageAdapter.java ya tiene @Profile("production") y es el que 
debe usarse en producción para el FileStoragePort.

### 7. Dockerfile (en la raíz del proyecto)
Cambio: Reemplazar el contenido completo con el siguiente:

FROM eclipse-temurin:21-maven-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/servicios-backend-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", \
  "-XX:+UseSerialGC", \
  "-XX:MaxRAMPercentage=70.0", \
  "-XX:InitialRAMPercentage=40.0", \
  "-XX:MaxMetaspaceSize=128m", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]

## Instrucciones importantes
- Realiza ÚNICAMENTE los cambios descritos arriba, nada más
- NO ejecutes git add, git commit, git push ni ningún comando de git
- NO modifiques ningún otro archivo fuera de los listados
- NO cambies lógica de negocio, solo anotaciones de perfil y el Dockerfile
- Después de cada cambio, confirma qué archivo fue modificado y muestra 
  el fragmento exacto que cambió (antes y después)
- Si algún archivo no existe o ya tiene el valor correcto, indícalo 
  explícitamente en lugar de marcarlo como modificado

## Verificación final esperada
Al terminar, muestra un resumen con:
1. Lista de archivos modificados
2. Lista de archivos que ya estaban correctos (sin cambios)
3. Confirmación de que no se ejecutó ningún comando git
