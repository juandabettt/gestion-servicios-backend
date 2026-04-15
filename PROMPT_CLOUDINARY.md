# Tarea: Implementar CloudinaryFileStorageAdapter en el backend

## Contexto
Backend Spring Boot 3.2.5 con Java 21 desplegado en Railway.
Actualmente el almacenamiento de imágenes falla porque las variables
apuntan a un MinIO local que no existe en producción.
Necesitamos implementar un adaptador de Cloudinary para el perfil
"production".

## Dependencia a agregar en pom.xml

Agregar dentro de <dependencies>:

```xml
<dependency>
    <groupId>com.cloudinary</groupId>
    <artifactId>cloudinary-http44</artifactId>
    <version>1.36.0</version>
</dependency>
```

## CAMBIO 1 — Crear CloudinaryFileStorageAdapter

Crear el archivo:
src/main/java/com/tuapp/servicios/infrastructure/adapter/storage/CloudinaryFileStorageAdapter.java

Con este contenido exacto:

```java
package com.tuapp.servicios.infrastructure.adapter.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.tuapp.servicios.application.port.FileStoragePort;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

@Component
@Profile("production")
@Slf4j
public class CloudinaryFileStorageAdapter implements FileStoragePort {

    @Value("${cloudinary.cloud-name}")
    private String cloudName;

    @Value("${cloudinary.api-key}")
    private String apiKey;

    @Value("${cloudinary.api-secret}")
    private String apiSecret;

    private Cloudinary cloudinary;

    @PostConstruct
    public void init() {
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true
        ));
        log.info("Cloudinary storage adapter inicializado correctamente");
    }

    @Override
    public String upload(String objectKey, byte[] content, String contentType) {
        try {
            String publicId = objectKey.replace("/", "_").replace(".", "_");
            Map uploadResult = cloudinary.uploader().upload(content,
                    ObjectUtils.asMap(
                            "public_id", publicId,
                            "resource_type", "image",
                            "folder", "facturas"
                    ));
            String url = (String) uploadResult.get("secure_url");
            log.info("Imagen subida a Cloudinary exitosamente");
            return url;
        } catch (IOException e) {
            log.error("Error subiendo imagen a Cloudinary");
            throw new RuntimeException("Error al subir imagen a Cloudinary", e);
        }
    }

    @Override
    public String generatePresignedUrl(String objectKey, Duration expiry) {
        // Cloudinary devuelve URLs publicas directamente
        // Si el objectKey ya es una URL de Cloudinary la retornamos tal cual
        if (objectKey != null && objectKey.startsWith("https://res.cloudinary.com")) {
            return objectKey;
        }
        // Si es un public_id generar la URL
        try {
            return cloudinary.url()
                    .secure(true)
                    .resourceType("image")
                    .generate(objectKey);
        } catch (Exception e) {
            log.error("Error generando URL de Cloudinary");
            return objectKey;
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            String publicId = objectKey;
            if (objectKey.startsWith("https://res.cloudinary.com")) {
                // Extraer public_id de la URL
                String[] parts = objectKey.split("/");
                publicId = parts[parts.length - 1].split("\\.")[0];
            }
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            log.info("Imagen eliminada de Cloudinary");
        } catch (IOException e) {
            log.error("Error eliminando imagen de Cloudinary");
        }
    }
}
```

## CAMBIO 2 — Agregar propiedades en application.yml

En el archivo src/main/resources/application.yml agregar
esta sección después de la sección "storage":

```yaml
cloudinary:
  cloud-name: ${CLOUDINARY_CLOUD_NAME:}
  api-key: ${CLOUDINARY_API_KEY:}
  api-secret: ${CLOUDINARY_API_SECRET:}
```

## CAMBIO 3 — Cambiar perfil de S3FileStorageAdapter

En el archivo:
src/main/java/com/tuapp/servicios/infrastructure/adapter/storage/S3FileStorageAdapter.java

Cambiar la anotación de perfil para que no entre en conflicto
con el nuevo adaptador de Cloudinary:

Cambiar:
@Profile("production")

Por:
@Profile("s3-production")

Esto evita que Spring intente registrar dos beans para el
mismo puerto FileStoragePort en el perfil production.

## CAMBIO 4 — Verificar MinIOFileStorageAdapter

En el archivo:
src/main/java/com/tuapp/servicios/infrastructure/adapter/storage/MinIOFileStorageAdapter.java

Verificar que tiene:
@Profile("local")

Si tiene @Profile({"local", "production"}) cambiarlo a solo:
@Profile("local")

Porque ahora production usará Cloudinary.

## Archivos a modificar
1. pom.xml (agregar dependencia cloudinary)
2. CREAR src/main/java/com/tuapp/servicios/infrastructure/adapter/storage/CloudinaryFileStorageAdapter.java
3. src/main/resources/application.yml (agregar sección cloudinary)
4. src/main/java/com/tuapp/servicios/infrastructure/adapter/storage/S3FileStorageAdapter.java (cambiar perfil)
5. src/main/java/com/tuapp/servicios/infrastructure/adapter/storage/MinIOFileStorageAdapter.java (verificar perfil)

## Instrucciones importantes
- NO ejecutes git add, git commit, git push ni ningún comando de git
- NO modifiques ningún otro archivo fuera de los listados
- NO cambies lógica de negocio
- Después de cada cambio confirma qué fue modificado
- Si la dependencia de cloudinary ya existe en pom.xml no la dupliques

## Verificación esperada
Al terminar el deploy en Railway:
1. El log debe mostrar "Cloudinary storage adapter inicializado correctamente"
2. Al subir una factura debe responder 202 sin error 500
3. La imagen debe aparecer en el dashboard de Cloudinary