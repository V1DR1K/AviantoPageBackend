# Backend público de Avianto Motos

Spring Boot 3.5, Java 21, PostgreSQL 17 y Flyway. Incluye la API pública, el panel `/admin`, las imágenes y los datos de ejemplo.

## Ejecutar en local

1. Iniciá una base PostgreSQL local en el puerto 55438 con base y usuario `avianto_landing`. Los valores locales de `application.yml` se pueden sustituir con `AVIANTO_DB_URL`, `AVIANTO_DB_USER` y `AVIANTO_DB_PASSWORD`. Si conectás el backend ejecutado en el host a la base del compose Docker, usá `AVIANTO_DB_URL=jdbc:postgresql://localhost:15438/avianto_landing`.
2. Definí `AVIANTO_ACTIVATION_CODE` con un código aleatorio de al menos 24 caracteres. El primer acceso de `avril` pide cambiar su clave inicial.
3. Si querés servir también la landing desde Java, definí `SPRING_WEB_RESOURCES_STATIC_LOCATIONS=classpath:/static/,file:../AviantoVentaFrontend/`.
4. Ejecutá `mvn spring-boot:run` y abrí `http://localhost:8084/admin`.

`mvn verify` compila y prueba las variantes de imagen. Para probar también el codificador de producción, definí `AVIANTO_CWEBP` con la ruta de `cwebp`. El entorno local usa JPEG cuando esa variable está vacía. `mvn package` genera el JAR usado por el Dockerfile.

## Stack local con Docker

Desde `AviantoPageBackend`, ejecutá `docker compose -f ops/compose.local.yml up -d --build`. La landing queda en `http://localhost:8091/`, el panel en `http://localhost:8091/admin`, la API directa en `http://localhost:8084` y PostgreSQL en `localhost:15438`. El stack usa PostgreSQL 17 y conserva los datos en el volumen `avianto-page-local-postgres-data`.

Para detenerlo sin borrar la base, ejecutá `docker compose -f ops/compose.local.yml down`. Las claves y el código de activación tienen valores exclusivos para desarrollo local; se pueden cambiar con `AVIANTO_LOCAL_DB_PASSWORD`, `AVIANTO_LOCAL_ADMIN_INITIAL_PASSWORD` y `AVIANTO_LOCAL_ACTIVATION_CODE`.

## Producción

`ops/compose.yml` ejecuta la API y una base privada. Requiere `AVIANTO_API_IMAGE`, `AVIANTO_DB_PASSWORD` y `AVIANTO_ACTIVATION_CODE` fuera del repositorio. El despliegue de Nginx se configura en el repositorio frontend. Las rutas, permisos, datos y política de caché están documentados en `documentacion.md`.
