# Arquitectura de Carga Directa a Amazon S3 — Fase 17.2

## 1. Visión General de la Arquitectura
La Fase 17.2 implementa el pipeline de almacenamiento directo a Amazon S3 privado para la ingesta de documentos (EPUB y PDF) de la plataforma, evitando que los bytes de archivos grandes transiten a través de los servidores de aplicación Spring Boot.

### Flujo de Tres Pasos:
1. **Intención de Carga (`POST /api/v1/documents/uploads`):**
   - El cliente envía metadatos (`fileName`, `contentType`, `sizeBytes`, `checksumSha256`).
   - El backend valida formato (EPUB/PDF), tamaño (hasta 50 MiB = 52,428,800 bytes) y sintaxis de SHA-256.
   - Se crea un registro `document_uploads` en estado `PENDING`.
   - Se genera una URL prefirmada HTTP PUT con expiración y cabeceras estrictas.
2. **Subida Directa Cliente $\rightarrow$ S3:**
   - El navegador o cliente HTTP realiza un `PUT` directo a la URL prefirmada enviando los `requiredHeaders`.
   - Amazon S3 valida las cabeceras firmadas y almacena el archivo de forma privada.
3. **Confirmación e Inspección Atómica (`POST /api/v1/documents/uploads/{uploadId}/confirm`):**
   - El backend inspecciona el objeto real en S3 fuera de la transacción de base de datos (`GetObjectAttributes` con fallback a `HeadObject`).
   - Si no hay checksum verificado disponible en storage, lanza `StorageChecksumUnavailableException` (HTTP 503) manteniendo el upload recuperable.
   - Si existe discrepancia de tamaño o checksum, aborta el upload a `ABORTED`, purga el objeto huérfano de S3 y rechaza con `UploadIntegrityMismatchException` (HTTP 422).
   - Si es íntegro, ejecuta la transacción atómica: deduplicación de usuario por SHA-256 verificado, creación de `imported_documents` en `PROCESSING`, `import_jobs` en `PENDING`, `outbox_events` en `PENDING` y actualización de `document_uploads` a `CONFIRMED`.

> **Alcance Estricto:** La Fase 17.2 concluye con el archivo físicamente verificado en S3 y los registros en base de datos listos para procesamiento. NO incluye SQS, ni worker consumidor, ni dispatcher del transactional outbox.

---

## 2. Almacenamiento y Claves Server-Owned
* **Bucket Privado:** El bucket de S3 es completamente privado (Block Public Access 100% activado, sin bucket policy pública ni ACLs públicas).
* **Estrategia de Clave Determinista:**
  ```
  documents/{userId}/{documentId}/source.epub|pdf
  ```
  - La clave es calculada y gobernada exclusivamente por el backend a partir del `userId` autenticado y un `UUID documentId` generado por el servidor.
  - Elimina riesgos de Path Traversal y colisiones entre usuarios.
* **Protección contra Sobreescritura (`If-None-Match: *`):**
  - La URL prefirmada firma obligatoriamente la cabecera `If-None-Match: *`.
  - Si se intenta reutilizar una URL prefirmada vigente para sobreescribir un archivo ya cargado, S3 rechaza la petición con **HTTP 412 Precondition Failed**.

---

## 3. Integridad y Checksum SHA-256
* El cliente suministra el SHA-256 en 64 caracteres hexadecimales en minúsculas.
* El backend convierte este valor a RFC 4648 Base64 (32 bytes $\rightarrow$ 44 caracteres) para firmar la cabecera estándar de S3 `x-amz-checksum-sha256`.
* Durante la inspección, el backend decodifica el hash retornado por S3 a hexadecimal y lo compara estrictamente contra el valor esperado.
* **Regla de Integridad:** El backend **jamás** confía en el checksum declarado por el cliente ni en el `ETag` de S3 como sustituto de un SHA-256 no verificado.

---

## 4. Cabeceras Requeridas (`requiredHeaders`)
El backend entrega al cliente un mapa de cabeceras que deben ser enviadas obligatoriamente en el PUT:
```json
{
  "Content-Type": "application/epub+zip",
  "If-None-Match": "*",
  "x-amz-checksum-sha256": "FSrMjewAiPH/Q2ClxCrSaxdy7G6LI4S16EOLxVi2wLY="
}
```
* **Exclusiones Intencionales:**
  - `Host`: Derivado automáticamente por el browser/transporte HTTP.
  - `Authorization`: Las credenciales viajan firmadas como query parameters en la URL prefirmada.
  - `Content-Length`: Calculado y gestionado por la capa de red/browser; no forma parte de la firma de S3.

---

## 5. Configuración CORS de S3
Para permitir la subida directa desde el navegador web (Angular):
```json
[
  {
    "AllowedOrigins": [
      "https://app.englishreading.com",
      "http://localhost:4200"
    ],
    "AllowedMethods": [
      "PUT"
    ],
    "AllowedHeaders": [
      "Content-Type",
      "If-None-Match",
      "x-amz-checksum-sha256"
    ],
    "ExposeHeaders": [
      "ETag",
      "x-amz-checksum-sha256"
    ],
    "MaxAgeSeconds": 3600
  }
]
```

---

## 6. Configuración de Aplicación (Spring Boot)
En `application.yaml` o variables de entorno:
```yaml
app:
  document-storage:
    provider: S3
    upload-intent-duration: 24h
    presign-duration: 15m
    s3:
      bucket: english-reading-documents
      region: us-east-1
      endpoint-override: ${AWS_S3_ENDPOINT_OVERRIDE:}
      path-style-access: false
```

---

## 7. Pruebas Locales con LocalStack
Para pruebas automatizadas e integración continua se utiliza Testcontainers LocalStack 3.8:
- Contenedor: `localstack/localstack:3.8` con servicio `S3`.
- `path-style-access: true` activado para emuladores locales.
- Creación de bucket de prueba al inicio de los tests.
- Validación end-to-end de generación de URL prefirmada, subida HTTP real, inspección de metadatos, deduplicación concurrente y rechazo condicional de sobreescritura.

---

## 8. Política IAM Mínima Requerida (Fase 17.2)

### Política IAM para el Backend (Bucket Privado General no Versionado)
El backend requiere únicamente los permisos para autorizar subidas, inspeccionar atributos y purgar objetos huérfanos ante fallos de integridad:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowDocumentDirectUploadAndVerification",
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:GetObjectAttributes",
        "s3:DeleteObject"
      ],
      "Resource": "arn:aws:s3:::english-reading-documents/documents/*"
    }
  ]
}
```

#### Justificación de Acciones:
1. `s3:PutObject`: Permite al backend generar URLs prefirmadas para que el cliente cargue objetos bajo el prefijo `documents/*`.
2. `s3:GetObjectAttributes`: Permite consultar de forma eficiente el tamaño del objeto (`OBJECT_SIZE`) y su checksum SHA-256 (`CHECKSUM`).
3. `s3:GetObject`: Requerido por AWS IAM para ejecutar `HeadObject` con `ChecksumMode.ENABLED` como fallback de inspección (la API de AWS no posee una acción `s3:HeadObject`, sino que mapea dicha operación al permiso `s3:GetObject`).
4. `s3:DeleteObject`: Permite eliminar de forma inmediata objetos huérfanos cuando se detecta una discrepancia de integridad durante la confirmación.

*Restricciones:* No se concede acceso a nivel de bucket (`s3:ListBucket`), ni comodines globales (`s3:*`), ni permisos de mensajería (SQS).

---

## 9. Nota de Configuración Futura: Cifrado con SSE-KMS (Opcional)
Si en producción se adopta cifrado del lado del servidor utilizando AWS KMS con clave administrada por el cliente (SSE-KMS con Customer Managed Key):
- Las llamadas de inspección (`HeadObject` o `GetObjectAttributes`) que acceden a metadatos de objetos cifrados con KMS pueden requerir permisos adicionales para invocar `kms:Decrypt` sobre la ARN de la clave.
- Esta configuración es puramente opcional para entornos que elijan KMS y no forma parte de la política mínima base (la cual utiliza SSE-S3 AES-256 gestionado por S3 sin costo ni configuración KMS adicional).
