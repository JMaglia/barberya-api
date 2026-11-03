# BarberYA API

Backend de BarberYA: turnos online para barberos en Argentina. Una sola web para
todas las barberías; los clientes reservan desde el link de cada barbería
(`barberya.app/{slug}`). Lo desarrollan Joaco y Manu.

Detalle del modelo de datos: @docs/base-de-datos.md
Decisiones de producto y su porqué: @docs/decisiones.md

## Alcance
- **Versión 1 (MVP):** barbero independiente (barbero y dueño a la vez) y sus clientes.
  Login, barbería y link, servicios, horarios y bloqueos, reglas de reserva, horarios libres,
  turnos, fichas de clientes, cobros automáticos, gastos fijos/variables, estadísticas,
  suscripción con Mercado Pago.
- **Fuera de la v1 (no implementar aunque el modelo lo permita):** varios barberos por barbería,
  comisiones y pagos a barberos, invitaciones, recepcionista, señas, WhatsApp automático,
  proveedores, productos.

## Stack
- Java 21, Spring Boot 4, Maven
- PostgreSQL 16 (Docker Compose en desarrollo)
- Flyway (`src/main/resources/db/migration`), `spring.jpa.hibernate.ddl-auto=validate`
- Spring Data JPA, Spring Security con JWT de acceso + refresh token en cookie httpOnly,
  rotado en cada uso. Login con Google y con mail.
- Tests: JUnit 5 + Testcontainers con Postgres real (nunca H2)
- springdoc-openapi (`/swagger-ui`)

## Comandos
- Base local: `docker compose up -d postgres`
- App: `./mvnw spring-boot:run`
- Tests: `./mvnw test` · uno solo: `./mvnw test -Dtest=NombreDelTest`
- En Windows, reemplazar `./mvnw` por `.\mvnw.cmd` (o `.\mvnw` en PowerShell).

## Nombres e idioma
- Dominio en español, igual que la base: tablas, columnas, entidades y campos
  (`Turno`, `Barberia`, `MiembroBarberia`, `horaInicio`). Sin tildes ni ñ en el código
  (`Barberia`, `Dueno`).
- Sufijos técnicos en inglés: `TurnoController`, `TurnoService`, `TurnoRepository`, `CrearTurnoRequest`.
- Paquetes por funcionalidad: `com.barberya.<modulo>` (auth, barberia, servicio, agenda,
  disponibilidad, turno, cliente, cobro, gasto, estadistica, suscripcion).
- Mensajes al usuario y commits en español.

## API
- Prefijo `/api/v1`. Lo público (sin login) va bajo `/api/v1/public/barberias/{slug}/...`
  (datos de la barbería, servicios, horarios libres).
- Entrada y salida siempre con DTOs (records); nunca exponer entidades.
- Validación con Bean Validation. Errores con `@RestControllerAdvice` en formato ProblemDetail
  (RFC 7807), mensaje en español. Turno superpuesto (`DataIntegrityViolationException` de la
  restricción `turnos_sin_superposicion`) → 409 "Ese horario ya no está disponible".
- Listados paginados (`page`, `size`), ordenados por fecha descendente salvo la agenda.

## Reglas del dominio (no romper)
- Fechas en UTC (`timestamptz` / `Instant`); se muestran en la `zona_horaria` de la barbería
  (por defecto `America/Argentina/Buenos_Aires`). Horario semanal en `TIME` + `dia_semana`
  (0 = domingo).
- Plata: `NUMERIC(12,2)` / `BigDecimal`. Nunca double.
- Multi-tenant: el `barberia_id` sale del usuario autenticado (token), **nunca** de un parámetro
  del front. Toda búsqueda por id filtra también por `barberia_id`
  (`findByIdAndBarberiaId`). Cada endpoint nuevo lleva un test que pruebe que otra barbería
  no puede ver ni tocar esos datos.
- Turnos: la base impide la superposición (restricción de exclusión). El servicio valida antes
  igual, para dar un mensaje claro. El fin del turno = inicio + duración del servicio; la pausa
  entre turnos la respeta el cálculo de horarios libres.
- El precio del turno es una copia del precio del servicio al reservar.
- Al marcar un turno COMPLETADO se crea su cobro automáticamente (sin medio de pago).
- Baja lógica (`activo = false`) para servicios, miembros y clientes con historial: no se borran.
- El rol sale de la base (`miembros_barberia.rol`), nunca del front.

## Seguridad
- Secretos (JWT, Google, Mercado Pago, base) solo en variables de entorno; `.env` está en
  `.gitignore`. Nunca commitear credenciales.
- No loguear datos personales (mail, teléfono, notas de clientes) ni tokens.
- Webhooks de Mercado Pago: verificar firma `x-signature`, responder en < 5 s y ser idempotentes
  (`referencia_externa` única).

## Forma de trabajar
- Para cambios que tocan varios archivos o la base: proponer un plan y esperar el OK.
- Cambios enfocados en lo pedido; no refactorizar ni agregar cosas fuera del alcance.
- Cada cambio de base = migración Flyway nueva (`V2__...`). Nunca editar una ya aplicada.
- Cada endpoint: test de integración del caso feliz, de validación y de aislamiento entre barberías.
- Correr `./mvnw test` antes de dar algo por terminado.
- Git: una rama por funcionalidad (`feat/reservar-turno`), PR a `main`, lo revisa el otro.
  Commits cortos: `feat: reservar turno`, `fix: ...`, `test: ...`.
