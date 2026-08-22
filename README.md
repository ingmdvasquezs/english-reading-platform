# English Reading Platform — backend

Backend SOAP contract-first basado en Spring Boot, PostgreSQL y Flyway. El stack HTTP es servlet
y blocking; los proveedores externos también son blocking con clientes reutilizados, timeouts y
límites de respuesta. Esta decisión es intencional para el MVP.

## Ejecución local

Requisitos: JDK 26 y Docker.

```shell
docker compose up -d
./mvnw spring-boot:run
```

El perfil por defecto es `local`. Usa PostgreSQL en `localhost:5432`, LibreTranslate en
`localhost:5000` y un secreto JWT exclusivamente de desarrollo. El servicio SOAP se publica en
`/ws` y el WSDL en `/ws/readings.wsdl`.

## Producción

Activar con `SPRING_PROFILES_ACTIVE=prod`. Variables obligatorias:

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `JWT_SECRET` (mínimo 32 bytes UTF-8)
- `DICTIONARY_FREE_BASE_URL`
- `LIBRETRANSLATE_BASE_URL`

Variables operativas opcionales:

- `LIBRETRANSLATE_API_KEY`, `JWT_EXPIRATION`
- `DB_MAXIMUM_POOL_SIZE` (10), `DB_MINIMUM_IDLE` (2)
- `DB_CONNECTION_TIMEOUT` (30000 ms), `DB_IDLE_TIMEOUT` (600000 ms),
  `DB_MAX_LIFETIME` (1800000 ms)
- `SHUTDOWN_TIMEOUT` (30 s)
- `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` (`health,info` en producción)
- timeouts, rate limits, tamaños SOAP y límites externos definidos en `application.yaml`

El pool debe dimensionarse con el límite total de conexiones de PostgreSQL y el número de
instancias; aumentarlo no implica más capacidad automáticamente. Flyway migra al iniciar y JPA
valida el schema.

## Operación

- Health: `/actuator/health`
- Liveness: `/actuator/health/liveness`
- Readiness: `/actuator/health/readiness`
- Prometheus local: `/actuator/prometheus`

Readiness incluye el estado de la aplicación y PostgreSQL. Los proveedores externos no bloquean
readiness: sus fallos, timeouts y latencias se observan mediante logs y métricas.

Cada respuesta devuelve `X-Correlation-ID`. Se reutiliza un valor seguro enviado por el cliente o
se genera un UUID. Los logs de producción usan JSON Logstash en stdout/stderr, preparado para ser
ingerido posteriormente por CloudWatch sin AWS SDK. No deben registrarse JWT, Authorization,
passwords, hashes, API keys, cuerpos SOAP/readings ni respuestas completas de proveedores.

El apagado es graceful: al recibir SIGTERM se dejan terminar solicitudes activas hasta
`SHUTDOWN_TIMEOUT`; Spring cierra Hikari y los recursos administrados.

El rate limiting por IP usa actualmente `HttpServletRequest.getRemoteAddr()`. En un despliegue
detrás de reverse proxy o load balancer debe configurarse y validarse explícitamente la política de
forwarded headers antes de confiar en la IP original del cliente; no se aceptan `X-Forwarded-For`
arbitrarios en esta fase.

## Verificación

```shell
./mvnw clean compile
./mvnw verify
./mvnw spotless:check
git diff --check
```

Docker Compose es únicamente para desarrollo local.
