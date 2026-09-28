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
2. `s3:GetObjectAttributes`: Permite consultar de forma eficiente el tamaño del objeto (`OBJECT_SIZE`) y su checksum SHA-256 (`CHECKSUM`). **Nota IAM:** AWS IAM exige tanto `s3:GetObject` como `s3:GetObjectAttributes` para invocar la operación `GetObjectAttributes`.
3. `s3:GetObject`: Requerido por AWS IAM para ejecutar `HeadObject` con `ChecksumMode.ENABLED` como fallback de inspección (la API de AWS no posee una acción IAM `s3:HeadObject`, sino que mapea dicha operación al permiso `s3:GetObject`) y necesario en conjunto con `s3:GetObjectAttributes`.
4. `s3:DeleteObject`: Permite eliminar de forma inmediata objetos huérfanos cuando se detecta una discrepancia de integridad durante la confirmación.

*Restricciones:* No se concede acceso a nivel de bucket (`s3:ListBucket`), ni comodines globales (`s3:*`), ni permisos de mensajería (SQS).

---

## 9. Nota de Configuración Futura: Cifrado con SSE-KMS (Opcional)
Si en producción se adopta cifrado del lado del servidor utilizando AWS KMS con clave administrada por el cliente (SSE-KMS con Customer Managed Key):
- Las llamadas de inspección (`HeadObject` o `GetObjectAttributes`) que acceden a metadatos de objetos cifrados con KMS pueden requerir permisos adicionales para invocar `kms:Decrypt` sobre la ARN de la clave.
- Esta configuración es puramente opcional para entornos que elijan KMS y no forma parte de la política mínima base (la cual utiliza SSE-S3 AES-256 gestionado por S3 sin costo ni configuración KMS adicional).

---

## 10. Despachador Outbox a Amazon SQS — Fase 17.3

### 10.1 Pipeline de Despacho Asíncrono
```
Confirm Upload
      ↓
PostgreSQL Transaction (Atómica)
   • document_uploads (CONFIRMED)
   • imported_documents (PROCESSING)
   • import_jobs (PENDING)
   • outbox_events (PENDING)
      ↓
Outbox Dispatcher (Scheduled / dispatchOnce)
   1. Claim Batch (FOR UPDATE SKIP LOCKED) → Estado SENDING con lock leasing
   2. Commit Claim Transaction
   3. SQS SendMessage (Llamada de red AWS SQS Standard fuera de transacción DB)
   4. Mark Published / Failed Transaction (Condicionada por lease vigente / fencing)
      ↓
Amazon SQS Standard
      ↓
[STOP: Worker consumidor diferido a Fase 17.4]
```

### 10.2 Contrato del Mensaje en Cola (Envelope Minimalista)
El mensaje enviado a SQS Standard es un JSON determinista, versionado y liviano que contiene únicamente la identidad necesaria para que el futuro Worker recupere el estado durable desde PostgreSQL:
```json
{
  "schemaVersion": 1,
  "eventId": "11111111-1111-1111-1111-111111111111",
  "eventType": "DOCUMENT_IMPORT_REQUESTED",
  "importJobId": "22222222-2222-2222-2222-222222222222"
}
```
* **No incluye:** Tokens, credenciales, URLs prefirmadas, metadatos pesados ni el contenido del documento.

### 10.3 Garantías de Entrega: AT-LEAST-ONCE e Idempotencia
- **Semántica At-Least-Once:** SQS Standard junto al Transactional Outbox garantizan entrega *al menos una vez*. Si el despachador publica exitosamente a SQS pero el proceso se interrumpe antes de ejecutar `markPublished`, el lease del outbox expirará y otro despachador reclamará y republicará el mensaje.
- **Idempotencia Obligatoria del Worker:** El futuro Worker consumidor (Fase 17.4) **debe ser estrictamente idempotente**, utilizando `eventId` o `importJobId` junto con el estado del `import_job` en PostgreSQL para ignorar entregas duplicadas.

### 10.4 Fencing y Control de Concurrencia
- La coordinación entre múltiples instancias de despachador es durable en PostgreSQL mediante `FOR UPDATE SKIP LOCKED`.
- Cada reclamo asigna un `locked_by` (identificador del despachador) y un `locked_until` (duración del lease).
- Las operaciones de cierre (`markPublished`, `markFailedAttempt`) verifican obligatoriamente que el lease continúe vigente (`locked_by = :dispatcherId AND locked_until >= :now`). Un despachador cuyo lease haya expirado no puede alterar el estado de un evento recuperado por otra instancia.

### 10.5 Separación por Rol de Proceso (`APP_ROLE`)
- `APP_ROLE=api` (o `all`): Ejecuta el API REST/SOAP y el `ScheduledOutboxDispatcher`.
- `APP_ROLE=worker`: Desactiva el scheduler del outbox dispatcher para evitar competencia accidental de recursos en procesos dedicados exclusivamente al procesamiento de fondo.

### 10.6 Política IAM Mínima para el Despachador (Fase 17.3)
El API/Despachador requiere únicamente permiso de envío sobre la cola SQS de importación:
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowDocumentImportQueuePublishing",
      "Effect": "Allow",
      "Action": [
        "sqs:SendMessage"
      ],
      "Resource": "arn:aws:sqs:us-east-1:*:english-reading-document-imports"
    }
  ]
}
```

### 10.7 Configuración Recomendada de SQS en AWS
- **Tipo de Cola:** Standard Queue.
- **Nombre sugerido:** `english-reading-document-imports`
- **Dead-Letter Queue (DLQ):** `english-reading-document-imports-dlq`
- **Redrive Policy:**
  ```json
  {
    "deadLetterTargetArn": "arn:aws:sqs:us-east-1:*:english-reading-document-imports-dlq",
    "maxReceiveCount": 5
  }
  ```
- **Visibility Timeout:** 300 segundos (5 minutos), alineado al tiempo máximo de procesamiento por documento.

---

## 11. Consumidor Worker SQS y Ejecución Durable de Importaciones — Fase 17.4

### 11.1 Flujo Completo End-to-End
```
Client (Web/App)
    ↓  1. POST /uploads & PUT direct to S3
Amazon S3 (Private Bucket)
    ↓  2. POST /uploads/{id}/confirm
PostgreSQL (Transacción atómica: document PROCESSING + import_job PENDING + outbox_event PENDING)
    ↓  3. ScheduledOutboxDispatcher (FOR UPDATE SKIP LOCKED)
SQS Standard (At-Least-Once Delivery)
    ↓  4. SqsDocumentImportConsumer (Long Polling, waitTime=20s)
Worker (DocumentImportProcessor)
    ↓  5. Claim atómico en PostgreSQL con fencing (leaseToken, leaseUntil, attemptCount++)
    ↓  6. Descarga privada desde S3 (GetObject) a staging temporal controlado por la aplicación
    ↓  7. Parser existente (EpubDocumentParserAdapter / PdfDocumentParserAdapter)
    ↓  8. Extensión periódica coordinada (Heartbeat DB renewLease + SQS ChangeMessageVisibility)
    ↓  9. Resolución de idioma y validación con DocumentLanguagePolicy
    ↓ 10. DocumentChunker V5 (VERSION=5, dense mode, 80 KiB limits — estrictamente sin modificar)
    ↓ 11. Persistencia atómica (CompleteDocumentImportUseCase en TX: sections + units + READY doc + COMPLETED job)
    ↓ 12. DeleteMessage en SQS Standard (únicamente tras el commit confirmado en DB)
READY (Disponible para lectura y progreso)
```

### 11.2 Fencing y Concurrencia de Workers en Base de Datos
- **Fuente de Verdad Única:** PostgreSQL gobierna el estado y la propiedad del procesamiento; SQS actúa únicamente como disparador de eventos desacoplado.
- **Token de Lease Criptográfico:** Cada claim exitoso genera un `UUID leaseToken` nuevo e incrementa `attemptCount`.
- **Condición de Fencing Estricta:** Las operaciones de persistencia (`completeImport`) y resolución final (`failFinal`) comprueban:
  ```sql
  status = 'PROCESSING' AND worker_id = :workerId AND lease_token = :leaseToken AND lease_until >= :now
  ```
  Un worker cuyo lease haya expirado o haya sido robado/reclamado por otra instancia no puede corromper la estructura del documento.

### 11.3 Coordinación de Heartbeat y Extensión de Visibilidad SQS
- **Mecanismo Coordinado:** Procesos largos de parsing o chunking ejecutan periódicamente (`DocumentImportHeartbeatCoordinator`):
  1. Extensión del lease en DB (`importJobs.renewLease`).
  2. Extensión del timeout de visibilidad en SQS (`sqs:ChangeMessageVisibility`).
- **Pérdida de Lease:** Si la renovación en DB devuelve `false` (p. ej. lease robado tras interrupción prolongada), el worker marca `ownershipLost = true` y aborta inmediatamente antes de invocar la persistencia final, sin eliminar el mensaje de SQS.

### 11.4 Política de Éxito y Orden de Eliminación (`Success-Before-Delete`)
- **Regla Inmutable:** El mensaje de SQS se borra **exclusivamente después** del commit atómico en base de datos.
- **Resiliencia ante Caídas (Crash Simulation):**
  - Si el worker completa la transacción en DB marcando el job como `COMPLETED` pero se apaga o falla antes de invocar `deleteMessage`:
  - SQS reentrega el mensaje tras el visibility timeout.
  - La nueva entrega ejecuta `importJobs.claim`, el cual detecta inmediatamente `ALREADY_COMPLETED`.
  - El worker reconoce la idempotencia: **no vuelve a parsear ni a insertar unidades**, e invoca inmediatamente `deleteMessage` en SQS.

### 11.5 Matriz de Respuestas a Mensajes Duplicados
| Estado del Job en DB | Resultado del Claim | Acción del Worker | Elimina de SQS |
| :--- | :--- | :--- | :--- |
| `PENDING` / Leased vencido | `ACQUIRED` | Procesa importación completa | Sí, tras commit `COMPLETED` |
| `COMPLETED` | `ALREADY_COMPLETED` | No-op idempotente | **Sí**, inmediatamente |
| `FAILED` (terminal) / `ABORTED` | `FINAL_FAILED` / `ABORTED` | No-op idempotente | **Sí**, inmediatamente |
| `PROCESSING` por otro worker | `ACTIVE_BY_OTHER_WORKER` | No procesa; espera visibilidad | **No**, deja expirar timeout |
| Máximo de intentos superado | `RETRY_LIMIT_EXCEEDED` | Marca `failFinal` en DB | **Sí**, tras marcar FAILED |
| No encontrado en DB | `NOT_FOUND` | Log de advertencia; DLQ handling | **No**, delega a DLQ |

### 11.6 Modelo de Fallos: Permanente vs Transitorio
1. **Fallo de Dominio Permanente:** Formato inválido (`INVALID_EPUB`, `INVALID_PDF`), PDF con contraseña, PDF escaneado sin texto, idioma no soportado o archivo no encontrado en storage (`StorageObjectNotFoundException`).
   - *Comportamiento:* Marca job y documento como `FAILED` con su código de error correspondiente, confirma la transacción en DB y elimina el mensaje de SQS.
2. **Fallo Transitorio de Infraestructura:** Caída de red temporal de S3 (`TransientStorageException`), desconexión temporal de base de datos o timeout de red.
   - *Comportamiento:* Si no se ha superado `maxAttempts`, libera el job para reintento con backoff (`releaseForRetry`) y **no borra** el mensaje de SQS.
   - Si se supera `maxAttempts`, realiza transición terminal a `FAILED` y elimina el mensaje.

### 11.7 Manejo de Mensajes Malformados y Poison Pills (DLQ Policy)
- El worker valida estrictamente el envelope JSON:
  - `schemaVersion == 1`
  - `eventType == "DOCUMENT_IMPORT_REQUESTED"`
  - `importJobId` con formato UUID válido.
- Si un mensaje no cumple el contrato o contiene JSON inválido, el worker **no lo elimina**. Permite que el `visibilityTimeout` expire para que la política de redrive de AWS mueva el poison message a la Dead-Letter Queue (`maxReceiveCount = 5`).

### 11.8 Aislamiento de Transacciones vs Operaciones Pesadas
- **Fuera de Transacción DB:**
  - Recepción de SQS (`receiveMessage`)
  - Descarga de S3 a staging (`s3Client.getObject`)
  - Parsing de EPUB/PDF
  - Extracción y almacenamiento de portada en storage
  - Chunking léxico con `DocumentChunker` V5
- **Dentro de Transacción DB Atómica (`CompleteDocumentImportUseCase`):**
  - Reemplazo de estructura de documento (`replaceDocumentStructure`)
  - Actualización de documento a `READY`
  - Transición de `import_job` a `COMPLETED`

### 11.9 Limpieza de Archivos Temporales (Staging)
- Las fuentes descargadas se almacenan temporalmente en `documentStorageRoot/staging` con nombres generados por el servidor (`Files.createTempFile`).
- La limpieza en bloque `finally { Files.deleteIfExists(tempFile); }` está garantizada en todos los escenarios: éxito, error de parsing, fallo de persistencia o excepción no controlada.

### 11.10 Roles de Proceso (`APP_ROLE`)
- `APP_ROLE=api`:
  - Activa el API web y el despachador de Outbox (`ScheduledOutboxDispatcher`).
  - El consumidor worker (`SqsDocumentImportConsumer`) permanece completamente desactivado.
  - No requiere cola configurada si el worker está inactivo y el outbox no la necesita.
- `APP_ROLE=worker`:
  - Desactiva el despachador de Outbox.
  - Activa el consumidor worker SQS (`SqsDocumentImportConsumer`).
  - Falla en el arranque (`fail-fast` con `IllegalStateException`) si `app.document-import.queue.url` no está configurada.
- `APP_ROLE=all`:
  - Ambos componentes activos en el mismo proceso (modo monolítico para desarrollo local).

### 11.11 Permisos IAM Mínimos del Worker (Fase 17.4)
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowDocumentImportQueueConsumption",
      "Effect": "Allow",
      "Action": [
        "sqs:ReceiveMessage",
        "sqs:DeleteMessage",
        "sqs:ChangeMessageVisibility"
      ],
      "Resource": "arn:aws:sqs:us-east-1:*:english-reading-document-imports"
    },
    {
      "Sid": "AllowDocumentPrivateSourceRead",
      "Effect": "Allow",
      "Action": [
        "s3:GetObject"
      ],
      "Resource": "arn:aws:s3:::english-reading-documents/documents/*"
    }
  ]
}
```
> **Nota de Seguridad:** No se otorgan permisos con comodines (`sqs:*`, `s3:*`). El worker no genera URLs prefirmadas para su propia lectura; utiliza su rol IAM para interactuar directamente con la API de S3 y SQS.

---

## 12. Endurecimiento de Runtime en Producción — Fase 17.5B

### 12.1 Validación Fail-Fast de Configuración del Worker
Para prevenir inconsistencias o degradación de runtime en despliegues distribuidos, `DocumentImportWorkerProperties` valida estrictamente sus parámetros en tiempo de arranque:
- **`maxMessages == 1` Obligatorio:** Dado que el consumidor actual procesa mensajes de manera secuencial en un solo hilo (`newSingleThreadExecutor`), recibir lotes mayores a 1 provocaría que los mensajes subsiguientes en la cola consuman su `visibilityTimeout` mientras esperan turno. Si se configura `maxMessages > 1`, el arranque falla de inmediato (`IllegalArgumentException`).
- **`waitTimeSeconds` (1 a 20s):** Obligatorio dentro de los límites válidos de SQS Long Polling.
- **`visibilityTimeoutSeconds` (1 a 43200s):** Alineado a los límites soportados por Amazon SQS (hasta 12 horas).
- **Invariante de Heartbeat:**
  - `heartbeatInterval * 2 <= leaseDuration`
  - `heartbeatInterval * 2 <= visibilityTimeoutSeconds`
  Esto asegura un margen de seguridad suficiente para tolerar latencias transitorias de red antes de que expire el lease en PostgreSQL o la visibilidad en SQS.

### 12.2 Trazabilidad con MDC y Correlación de Logs
Durante el procesamiento de cada mensaje SQS, el consumidor inyecta las siguientes claves contextuales en el `MDC` de SLF4J:
- `correlationId`: Asignado prioritariamente al `eventId` del mensaje (o en su defecto al `importJobId`). Conectado con el patrón de logging `[correlationId=%X{correlationId:-none}]`.
- `eventId`: UUID del evento publicado desde el outbox transaccional.
- `importJobId`: UUID del trabajo durable en `import_jobs`.

Ambas claves se limpian de manera determinista en un bloque `finally` para evitar fugas contextuales en hilos reutilizados.

### 12.3 Limpieza Segura de Archivos Temporales de Staging
- Durante la inicialización del worker (`start()`), se invoca `cleanStaleStagingFiles()` sobre `${document.storage.root}/staging`.
- Se eliminan de forma segura aquellos archivos regulares que:
  1. Residan estrictamente dentro del subdirectorio `staging` (prevención de Path Traversal y enlaces simbólicos con `NOFOLLOW_LINKS`).
  2. Hayan superado la edad de retención configurada en `stagingCleanupAge` (por defecto 24 horas).
- Cualquier error de I/O en la eliminación se registra como advertencia (`WARN`) sin interrumpir el arranque del worker.

### 12.4 Especificación de Limpieza de Cargas Expiradas y Decisión de Diferimiento
- **Prohibición de S3 Lifecycle Rules en `documents/*`:**
  - Los archivos finales de documentos activos residen permanentemente bajo la clave `documents/{userId}/{documentId}/source.epub|pdf`.
  - Configurar una regla de ciclo de vida de S3 que elimine objetos basados en antigüedad sobre `documents/*` destruiría irreparablemente los documentos legítimos de los usuarios.
- **Limpieza de Cargas Abandonadas (`document_uploads` en `PENDING` expiradas):**
  - Un usuario puede solicitar una intención de carga (`POST /api/v1/documents/uploads`), subir el archivo a S3 y cerrar la pestaña sin invocar `/confirm`.
  - El diseño seguro para purgar estos objetos requiere un trabajo programado que:
    1. Reclame con lease registros en `document_uploads` donde `status = 'PENDING'` y `expires_at < :now`.
    2. Transicione el estado a `EXPIRED` de forma atómica evitando carreras con `/confirm` en vuelo.
    3. Elimine el objeto físico de S3 (`s3:DeleteObject`).
    4. Registre la confirmación de borrado en una columna de auditoría (p. ej. `storage_deleted_at TIMESTAMP`) para permitir reintentos idempotentes ante fallos de red.
  - **Decisión de Diferimiento:** Debido a la restricción estricta de **"NO crear migraciones"** en esta fase de endurecimiento de runtime, la introducción de columnas adicionales y nuevas tablas de control queda formalmente **diferida** para una fase posterior con migración de base de datos dedicada.
