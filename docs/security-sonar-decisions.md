# Decisiones de Seguridad Auditadas en SonarQube

Este documento registra formalmente las decisiones técnicas de seguridad y la postura frente a findings reportados por SonarQube en el backend de la plataforma.

---

## 1. CSRF Deshabilitado (`java:S4502`)

- **Regla Sonar:** `java:S4502` ("Make sure disabling CSRF protection is safe here.")
- **Ubicación:** `src/main/java/com/soap/soap/infrastructure/security/SecurityConfiguration.java` (`http.csrf(csrf -> csrf.disable())`)
- **Decisión:** **ACCEPTED / SECURITY DECISION REVIEWED** (No se habilita CSRF ni se introducen tokens artificiales).

### Threat Model
Cross-Site Request Forgery (CSRF) ocurre cuando un sitio web malicioso induce al navegador de una víctima autenticada a ejecutar peticiones no deseadas hacia una aplicación vulnerable. Esta vulnerabilidad depende exclusivamente de **credenciales ambientales** que el navegador web adjunta automáticamente en peticiones cross-site (como cookies de sesión `Set-Cookie`, credenciales HTTP Basic autenticadas en el browser, o certificados de cliente).

### Justificación Técnica y Controles Compensatorios
1. **Arquitectura Completamente Stateless:**
   - La aplicación configura explícitamente `SessionCreationPolicy.STATELESS` en `SecurityFilterChain`.
   - No se utiliza ni se crea `HttpSession` en ningún punto de la aplicación.
2. **Autenticación Exclusiva por Cabecera Bearer:**
   - Tanto la API REST (`/api/v1/**`) como las operaciones protegidas SOAP (`/ws/**`) validan credenciales exclusivamente a través de la cabecera HTTP estándar `Authorization: Bearer <jwt>` (`JwtAuthenticationFilter`).
   - Los navegadores web **nunca** adjuntan cabeceras `Authorization` personalizadas automáticamente en peticiones transversales originadas por formularios HTML (`<form>`), etiquetas `<img>`, `<iframe>` o scripts entre orígenes no autorizados.
3. **Inexistencia de Cookies de Autenticación:**
   - El backend no emite cookies (`Set-Cookie`) ni consume cookies para autenticar peticiones de usuario.
4. **Conclusión:**
   - La superficie de ataque de CSRF es nula para este modelo de autenticación. Habilitar la protección CSRF tradicional añadiría complejidad de sincronización de tokens sin aportar ninguna ganancia de seguridad efectiva.

### Condiciones que Invalidarían esta Decisión
- Introducción futura de autenticación basada en cookies (`Set-Cookie`, `HttpOnly`).
- Migración a sesiones de servidor con estado (`HttpSession`).
- Habilitación de autenticación basada en credenciales implícitas del navegador para peticiones de mutación de estado.

---

## 2. Archivo Temporal en Staging Privado (`java:S5443`)

- **Regla Sonar:** `java:S5443` ("Using File.createTempFile, Files.createTempFile, or File.createTempDirectory without specifying a directory creates files in the default system temporary directory.")
- **Ubicación:** `src/main/java/com/soap/soap/infrastructure/rest/DocumentRestController.java`
- **Decisión:** **FIXED / HARDENED** (Migrado a directorio de staging privado gestionado por la aplicación).

### Threat Model
La creación de archivos temporales en el directorio temporal compartido del sistema operativo (`/tmp` o `%TEMP%`) sin especificar un directorio propio puede exponer la aplicación a riesgos en entornos Unix multiusuario o compartidos:
- Snooping / enumeración de nombres por otros usuarios del mismo host.
- Ataques de symlink o colisión previa de archivos si el umask o sticky-bit no están rigurosamente confinados.
- Procesos externos de limpieza del SO (`tmpwatch`, `systemd-tmpfiles`) que puedan eliminar archivos mientras la petición está en vuelo.
- Agotamiento de almacenamiento en montajes `tmpfs` en memoria dentro de contenedores si los archivos cargados son de gran volumen.

### Implementación y Controles
1. **Directorio Privado de Staging:**
   - El endpoint multipart legacy (`POST /api/v1/documents`) ya no invoca `Files.createTempFile` sobre el directorio público del sistema.
   - En su lugar, inyecta `Path documentStorageRoot` (configurado en `app.documents.storage-root`, por defecto `.private-assets`) y genera los archivos transitorios en un subdirectorio privado `staging/` gestionado por el proceso:
     ```java
     Files.createDirectories(stagingDirectory);
     var staged = Files.createTempFile(stagingDirectory, "document-upload-", format == DocumentFormat.PDF ? ".pdf" : ".epub");
     ```
2. **Aislamiento de Nombres y Extensiones:**
   - El prefijo `"document-upload-"` es estático y controlado por el servidor.
   - La extensión (`.pdf` o `.epub`) proviene estrictamente del enum `DocumentFormat` validado por contenido y firma mágica (`%PDF-` / `PK`).
   - El `originalFilename` enviado por el usuario **no** interviene en la construcción de la ruta física en disco.
3. **Ciclo de Vida Transitorio y Limpieza Garantizada:**
   - El archivo temporal se elimina de inmediato en bloque `finally`:
     ```java
     try {
       ...
     } finally {
       Files.deleteIfExists(staged);
     }
     ```
   - Tanto ante éxito como ante cualquier excepción en la ingesta, el archivo es purgado.
4. **Independencia de S3 Direct Upload:**
   - La arquitectura moderna de subida directa a S3 (`DocumentUploadRestController`) no utiliza almacenamiento temporal en disco y se mantiene intacta.
