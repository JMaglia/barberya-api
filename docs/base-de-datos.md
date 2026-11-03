# Base de datos

Modelo de datos de BarberYA (PostgreSQL 16). Parte de `docs/Informacion_BD.pdf`; donde difieran, manda este archivo.

**19 tablas:** 17 van en V1 y 2 quedan para v2 (ver [v2, no va en V1](#v2-no-va-en-v1)).

## Convenciones

- **Multi-tenant:** una sola base. Cada fila pertenece a una barbería por `barberia_id`. `horarios_atencion`, `bloqueos_agenda` y `pagos_suscripcion` no tienen esa columna y se filtran por join con su tabla padre.
- **Tipos:** ids `BIGINT GENERATED ALWAYS AS IDENTITY`; plata `NUMERIC(12,2)`; instantes `TIMESTAMPTZ`; horario semanal `TIME` + `dia_semana` `SMALLINT`; fechas sin hora `DATE`.
- **Estados:** `VARCHAR` + `CHECK`, sin `CREATE TYPE`. En JPA, `@Enumerated(EnumType.STRING)`.
- **Fechas locales:** `DATE` no usa default `CURRENT_DATE`, porque la base corre en UTC. La fecha local la manda el backend.
- **`ON DELETE`:** `NO ACTION` (el default de Postgres) salvo que se indique otra cosa. No usar `RESTRICT`, porque puede trabar el borrado en cascada de una barbería.
- **FK compuesta:** `(x_id, barberia_id)` apunta a `UNIQUE (id, barberia_id)` en la tabla destino, para que ambas filas sean de la misma barbería. Si `x_id` es NULL, no se verifica.

**Abreviaturas:** PK = primary key, FK = foreign key, NN = `NOT NULL`, UQ = `UNIQUE`, def. = valor por defecto.

## Plataforma y suscripción

### planes

| Columna | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` | PK |
| `nombre` | `VARCHAR(50)` | NN, UQ |
| `precio_mensual` | `NUMERIC(12,2)` | NN, `>= 0` |
| `max_barberos` | `INT` | NN, `> 0` |
| `activo` | `BOOLEAN` | NN, def. `TRUE` |

### barberias

| Columna | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` | PK |
| `nombre` | `VARCHAR(120)` | NN |
| `slug` | `VARCHAR(60)` | NN, UQ. Link público: `barberya.app/{slug}` |
| `descripcion` | `TEXT` | |
| `direccion` | `VARCHAR(200)` | |
| `telefono` | `VARCHAR(30)` | |
| `instagram` | `VARCHAR(60)` | |
| `logo_url` | `TEXT` | |
| `foto_portada_url` | `TEXT` | |
| `zona_horaria` | `VARCHAR(50)` | NN, def. `'America/Argentina/Buenos_Aires'` |
| `cancelar_hasta_horas` | `INT` | NN, def. `24`, `>= 0` |
| `reserva_max_dias` | `INT` | NN, def. `30`, `> 0` |
| `pausa_entre_turnos_min` | `INT` | NN, def. `0`, `>= 0` |
| `activa` | `BOOLEAN` | NN, def. `TRUE`. Solo suspensión manual del admin; el acceso lo decide la suscripción |
| `creado_en` | `TIMESTAMPTZ` | NN, def. `now()` |

### suscripciones

| Columna | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` | PK |
| `barberia_id` | `BIGINT` | NN, FK → `barberias`, `ON DELETE CASCADE` |
| `plan_id` | `BIGINT` | NN, FK → `planes` |
| `estado` | `VARCHAR(20)` | NN, def. `'PRUEBA'`, `CHECK`: `PRUEBA`, `ACTIVA`, `VENCIDA`, `CANCELADA` |
| `fecha_inicio` | `DATE` | NN |
| `proximo_cobro` | `DATE` | |
| `fecha_fin` | `DATE` | |
| `mp_preapproval_id` | `VARCHAR(100)` | UQ |
| `creado_en` | `TIMESTAMPTZ` | NN, def. `now()` |

Índice único parcial `ux_suscripcion_viva`: `(barberia_id) WHERE estado IN ('PRUEBA', 'ACTIVA')`.

### pagos_suscripcion

| Columna | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` | PK |
| `suscripcion_id` | `BIGINT` | NN, FK → `suscripciones`, `ON DELETE CASCADE` |
| `monto` | `NUMERIC(12,2)` | NN, `>= 0` |
| `pagado_en` | `TIMESTAMPTZ` | NN, def. `now()` |
| `estado` | `VARCHAR(20)` | NN, `CHECK`: `APROBADO`, `RECHAZADO`, `PENDIENTE` |
| `referencia_externa` | `VARCHAR(100)` | NN, UQ. Id del pago en Mercado Pago; da la idempotencia del webhook |

## Personas y acceso

### usuarios

Toda persona que inicia sesión. Lo que puede hacer depende de su relación con cada barbería.

| Columna | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` | PK |
| `email` | `VARCHAR(255)` | NN, UQ, `CHECK (email = lower(email))` |
| `password_hash` | `VARCHAR(255)` | BCrypt. NULL si entra con Google o con código |
| `google_sub` | `VARCHAR(255)` | UQ |
| `email_verificado` | `BOOLEAN` | NN, def. `FALSE` |
| `es_admin` | `BOOLEAN` | NN, def. `FALSE`. Admin de la plataforma |
| `nombre` | `VARCHAR(80)` | NN |
| `apellido` | `VARCHAR(80)` | |
| `telefono` | `VARCHAR(30)` | |
| `creado_en` | `TIMESTAMPTZ` | NN, def. `now()` |

### refresh_tokens

| Columna | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` | PK |
| `usuario_id` | `BIGINT` | NN, FK → `usuarios`, `ON DELETE CASCADE` |
| `token_hash` | `VARCHAR(255)` | NN, UQ |
| `expira_en` | `TIMESTAMPTZ` | NN |
| `revocado` | `BOOLEAN` | NN, def. `FALSE` |
| `reemplazado_por` | `BIGINT` | FK → `refresh_tokens(id)`, `ON DELETE SET NULL` |
| `creado_en` | `TIMESTAMPTZ` | NN, def. `now()` |

### tokens_verificacion

El usuario se crea recién al validar un `CODIGO_LOGIN`; hasta entonces, el código se asocia al mail. `CODIGO_LOGIN` también verifica el mail de quien se registra con contraseña.

| Columna | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` | PK |
| `usuario_id` | `BIGINT` | FK → `usuarios`, `ON DELETE CASCADE`. NULL si todavía no hay cuenta |
| `email` | `VARCHAR(255)` | NN, `CHECK (email = lower(email))` |
| `tipo` | `VARCHAR(20)` | NN, `CHECK`: `RECUPERAR_CLAVE`, `CODIGO_LOGIN` |
| `token_hash` | `VARCHAR(255)` | NN. No es UQ: los códigos pueden repetirse |
| `intentos` | `SMALLINT` | NN, def. `0` |
| `expira_en` | `TIMESTAMPTZ` | NN |
| `usado_en` | `TIMESTAMPTZ` | |
| `creado_en` | `TIMESTAMPTZ` | NN, def. `now()` |

### miembros_barberia

Personal de cada barbería. En V1, una sola fila: `DUENO` con `atiende_turnos = true`.

| Columna | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` | PK |
| `barberia_id` | `BIGINT` | NN, FK → `barberias`, `ON DELETE CASCADE` |
| `usuario_id` | `BIGINT` | NN, FK → `usuarios` |
| `rol` | `VARCHAR(20)` | NN, `CHECK`: `DUENO`, `BARBERO` |
| `atiende_turnos` | `BOOLEAN` | NN, def. `TRUE` |
| `nombre_visible` | `VARCHAR(80)` | |
| `foto_url` | `TEXT` | |
| `activo` | `BOOLEAN` | NN, def. `TRUE` |

`UNIQUE (barberia_id, usuario_id)` y `UNIQUE (id, barberia_id)`.

### clientes

Ficha del cliente dentro de cada barbería. Una misma cuenta tiene una ficha por barbería.

| Columna | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` | PK |
| `barberia_id` | `BIGINT` | NN, FK → `barberias`, `ON DELETE CASCADE` |
| `usuario_id` | `BIGINT` | FK → `usuarios`. NULL si el barbero lo cargó a mano |
| `nombre` | `VARCHAR(80)` | NN |
| `telefono` | `VARCHAR(30)` | |
| `email` | `VARCHAR(255)` | |
| `notas` | `TEXT` | |
| `activo` | `BOOLEAN` | NN, def. `TRUE` |
| `creado_en` | `TIMESTAMPTZ` | NN, def. `now()` |

`UNIQUE (barberia_id, usuario_id)` (los NULL no cuentan) y `UNIQUE (id, barberia_id)`.

## Agenda

### servicios

| Columna | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` | PK |
| `barberia_id` | `BIGINT` | NN, FK → `barberias`, `ON DELETE CASCADE` |
| `nombre` | `VARCHAR(80)` | NN |
| `descripcion` | `TEXT` | |
| `duracion_min` | `INT` | NN, `> 0` |
| `precio` | `NUMERIC(12,2)` | NN, `>= 0` |
| `activo` | `BOOLEAN` | NN, def. `TRUE` |

`UNIQUE (id, barberia_id)`.

### horarios_atencion

Permite turno partido: dos filas el mismo día.

| Columna | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` | PK |
| `miembro_id` | `BIGINT` | NN, FK → `miembros_barberia`, `ON DELETE CASCADE` |
| `dia_semana` | `SMALLINT` | NN, `BETWEEN 0 AND 6` (0 = domingo) |
| `hora_inicio` | `TIME` | NN |
| `hora_fin` | `TIME` | NN, `> hora_inicio` |

Índice `(miembro_id, dia_semana)`.

### bloqueos_agenda

| Columna | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` | PK |
| `miembro_id` | `BIGINT` | NN, FK → `miembros_barberia`, `ON DELETE CASCADE` |
| `inicio` | `TIMESTAMPTZ` | NN |
| `fin` | `TIMESTAMPTZ` | NN, `> inicio` |
| `motivo` | `VARCHAR(120)` | |

Índice `(miembro_id, inicio)`.

### turnos

| Columna | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` | PK |
| `barberia_id` | `BIGINT` | NN, FK → `barberias`, `ON DELETE CASCADE` |
| `miembro_id` | `BIGINT` | NN, FK compuesta `(miembro_id, barberia_id)` → `miembros_barberia` |
| `cliente_id` | `BIGINT` | NN, FK compuesta `(cliente_id, barberia_id)` → `clientes` |
| `servicio_id` | `BIGINT` | NN, FK compuesta `(servicio_id, barberia_id)` → `servicios` |
| `inicio` | `TIMESTAMPTZ` | NN |
| `fin` | `TIMESTAMPTZ` | NN, `> inicio`. Inicio + duración del servicio |
| `estado` | `VARCHAR(20)` | NN, def. `'CONFIRMADO'`, `CHECK`: `CONFIRMADO`, `COMPLETADO`, `CANCELADO`, `AUSENTE` |
| `precio` | `NUMERIC(12,2)` | NN, `>= 0`. Copia del precio al reservar |
| `notas` | `TEXT` | |
| `cancelado_en` | `TIMESTAMPTZ` | Ver `CHECK` abajo |
| `cancelado_por` | `VARCHAR(20)` | `CHECK`: `CLIENTE`, `BARBERO` |
| `recordatorio_enviado_en` | `TIMESTAMPTZ` | |
| `creado_en` | `TIMESTAMPTZ` | NN, def. `now()` |

- `UNIQUE (id, barberia_id)`.
- `CHECK`: `cancelado_en` y `cancelado_por` están cargados si y solo si `estado = 'CANCELADO'`.
- Índices `(barberia_id, inicio)` y `(cliente_id)`.
- Restricción `turnos_sin_superposicion`:

```sql
EXCLUDE USING gist (miembro_id WITH =, tstzrange(inicio, fin, '[)') WITH &&)
  WHERE (estado NOT IN ('CANCELADO', 'AUSENTE'))
```

## Plata

Los valores de `metodo` son los mismos en `cobros` y `gastos`: `EFECTIVO`, `TRANSFERENCIA`, `MERCADO_PAGO`, `TARJETA`, `OTRO`.

### cobros

Se crea solo al completar el turno, sin medio de pago. En V1 todo cobro nace de un turno; `turno_id` NULL y `concepto` quedan para ventas sueltas futuras.

| Columna | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` | PK |
| `barberia_id` | `BIGINT` | NN, FK → `barberias`, `ON DELETE CASCADE` |
| `turno_id` | `BIGINT` | UQ, FK compuesta `(turno_id, barberia_id)` → `turnos` |
| `monto` | `NUMERIC(12,2)` | NN, `>= 0` |
| `metodo` | `VARCHAR(20)` | `CHECK` con los valores de arriba |
| `cobrado_en` | `TIMESTAMPTZ` | NN, def. `now()` |
| `concepto` | `VARCHAR(120)` | |

Índice `(barberia_id, cobrado_en)`.

### categorias_gasto

| Columna | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` | PK |
| `barberia_id` | `BIGINT` | NN, FK → `barberias`, `ON DELETE CASCADE` |
| `nombre` | `VARCHAR(60)` | NN |

`UNIQUE (barberia_id, nombre)` y `UNIQUE (id, barberia_id)`.

### gastos_fijos

Plantilla de un gasto mensual. Está pendiente en un mes si no tiene un gasto con ese `gasto_fijo_id` en ese mes.

| Columna | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` | PK |
| `barberia_id` | `BIGINT` | NN, FK → `barberias`, `ON DELETE CASCADE` |
| `concepto` | `VARCHAR(200)` | NN |
| `monto` | `NUMERIC(12,2)` | NN, `> 0` |
| `dia_del_mes` | `SMALLINT` | NN, `BETWEEN 1 AND 28` |
| `activo` | `BOOLEAN` | NN, def. `TRUE` |
| `creado_en` | `TIMESTAMPTZ` | NN, def. `now()` |

`UNIQUE (id, barberia_id)`.

### gastos

| Columna | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` | PK |
| `barberia_id` | `BIGINT` | NN, FK → `barberias`, `ON DELETE CASCADE` |
| `categoria_id` | `BIGINT` | FK compuesta `(categoria_id, barberia_id)` → `categorias_gasto`, `ON DELETE SET NULL (categoria_id)` |
| `gasto_fijo_id` | `BIGINT` | FK compuesta `(gasto_fijo_id, barberia_id)` → `gastos_fijos` |
| `descripcion` | `VARCHAR(200)` | NN |
| `monto` | `NUMERIC(12,2)` | NN, `> 0` |
| `fecha` | `DATE` | NN |
| `metodo` | `VARCHAR(20)` | `CHECK` con los valores de arriba |
| `comprobante_url` | `TEXT` | |
| `es_fijo` | `BOOLEAN` | NN, def. `FALSE` |
| `creado_en` | `TIMESTAMPTZ` | NN, def. `now()` |

- `CHECK (gasto_fijo_id IS NULL OR es_fijo)`.
- Índices `(barberia_id, fecha)` y `(gasto_fijo_id, fecha)`.

## Reglas que garantiza la base

| Regla | Cómo |
|---|---|
| Un barbero no tiene dos turnos superpuestos (los contiguos sí se permiten) | `turnos_sin_superposicion`; requiere la extensión `btree_gist` |
| Turno, miembro, cliente y servicio son de la misma barbería | FK compuestas desde `turnos` |
| Un cobro y su turno son de la misma barbería | FK compuesta desde `cobros` |
| Un gasto, su categoría y su gasto fijo son de la misma barbería | FK compuestas desde `gastos` |
| Una sola suscripción viva por barbería | `ux_suscripcion_viva` |
| Un turno se cobra una sola vez | `cobros.turno_id` UQ |
| Un pago de Mercado Pago no se registra dos veces | `pagos_suscripcion.referencia_externa` NN, UQ |
| Sin montos negativos ni duraciones en cero | `CHECK` de montos, precios y `duracion_min` |
| Las franjas tienen sentido | `CHECK` de fin posterior al inicio en turnos, bloqueos y horarios |
| Un turno cancelado siempre registra cuándo y quién | `CHECK` de `cancelado_en` y `cancelado_por` |
| Solo valores válidos en estados, roles, tipos y métodos | `CHECK` en cada columna |
| Links y mails únicos, con mail en minúsculas | UQ en `barberias.slug` y `usuarios.email`; `CHECK` de minúsculas |
| Sin personas duplicadas en una barbería | `UNIQUE (barberia_id, usuario_id)` en `miembros_barberia` y `clientes` |
| Nada queda huérfano | FK en todas las relaciones |

## Lo que valida el backend

La base no impide estos casos:

- **Horarios libres:** respetar la pausa entre turnos, el horario de atención, los bloqueos y `reserva_max_dias`. Una fila de `turnos` puede pisar un bloqueo o quedar fuera de horario.
- **Horarios de atención:** que las franjas de un mismo día no se superpongan.
- **Cancelación:** respetar `cancelar_hasta_horas`.
- **Revertir un turno completado:** borrar su cobro automático.
- **Gastos fijos:** un solo gasto por fijo y mes.
- **Acceso:** lo decide la suscripción. `barberias.activa = false` es una suspensión manual del admin.
- **Planes:** respetar `max_barberos`.
- **Slugs:** reservar los que chocan con rutas de la web, como `api` o `login`.
- **Mails:** normalizarlos a minúsculas antes de guardar.
- **Códigos:** limitar `intentos` y exigir `usuario_id` en `RECUPERAR_CLAVE`.
- **Celular:** pedirlo al cliente antes de su primera reserva; en `usuarios.telefono` es nullable porque Google no lo informa.

## v2, no va en V1

No crear en la migración V1 ni implementar.

- **Rol `RECEPCION`:** sumarlo al `CHECK` de `miembros_barberia.rol` con una migración.
- **`gastos.proveedor_id`:** `BIGINT`, FK compuesta `(proveedor_id, barberia_id)` → `proveedores`, `ON DELETE SET NULL (proveedor_id)`.

### proveedores (v2, no va en V1)

| Columna | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` | PK |
| `barberia_id` | `BIGINT` | NN, FK → `barberias`, `ON DELETE CASCADE` |
| `nombre` | `VARCHAR(120)` | NN |
| `rubro` | `VARCHAR(80)` | |
| `telefono` | `VARCHAR(30)` | |
| `email` | `VARCHAR(255)` | |
| `notas` | `TEXT` | |
| `activo` | `BOOLEAN` | NN, def. `TRUE` |

`UNIQUE (id, barberia_id)`.

### barbero_servicios (v2, no va en V1)

| Columna | Tipo | Restricciones |
|---|---|---|
| `miembro_id` | `BIGINT` | PK, FK → `miembros_barberia`, `ON DELETE CASCADE` |
| `servicio_id` | `BIGINT` | PK, FK → `servicios`, `ON DELETE CASCADE` |

## Pendientes

- **Factura C (fase de cobro):** es la que BarberYA le emite al barbero por el pago de la suscripción. No va en V1. Columnas futuras en `pagos_suscripcion`: `punto_venta`, `nro_comprobante`, `cae`, `cae_vencimiento`.
