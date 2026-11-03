-- Esquema inicial de BarberYA (V1). Modelo completo en docs/base-de-datos.md.

-- Necesaria para combinar miembro_id (=) y el rango de fechas (&&) en turnos_sin_superposicion.
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ---------------------------------------------------------------------------
-- Plataforma y suscripción
-- ---------------------------------------------------------------------------

CREATE TABLE planes (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre         VARCHAR(50)   NOT NULL UNIQUE,
    precio_mensual NUMERIC(12,2) NOT NULL CHECK (precio_mensual >= 0),
    max_barberos   INT           NOT NULL CHECK (max_barberos > 0),
    activo         BOOLEAN       NOT NULL DEFAULT TRUE
);

CREATE TABLE barberias (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre                 VARCHAR(120) NOT NULL,
    slug                   VARCHAR(60)  NOT NULL UNIQUE,
    descripcion            TEXT,
    direccion              VARCHAR(200),
    telefono               VARCHAR(30),
    instagram              VARCHAR(60),
    logo_url               TEXT,
    foto_portada_url       TEXT,
    zona_horaria           VARCHAR(50)  NOT NULL DEFAULT 'America/Argentina/Buenos_Aires',
    cancelar_hasta_horas   INT          NOT NULL DEFAULT 24 CHECK (cancelar_hasta_horas >= 0),
    reserva_max_dias       INT          NOT NULL DEFAULT 30 CHECK (reserva_max_dias > 0),
    pausa_entre_turnos_min INT          NOT NULL DEFAULT 0 CHECK (pausa_entre_turnos_min >= 0),
    activa                 BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en              TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE suscripciones (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    barberia_id       BIGINT      NOT NULL REFERENCES barberias (id) ON DELETE CASCADE,
    plan_id           BIGINT      NOT NULL REFERENCES planes (id),
    estado            VARCHAR(20) NOT NULL DEFAULT 'PRUEBA'
                      CHECK (estado IN ('PRUEBA', 'ACTIVA', 'VENCIDA', 'CANCELADA')),
    fecha_inicio      DATE        NOT NULL,
    proximo_cobro     DATE,
    fecha_fin         DATE,
    mp_preapproval_id VARCHAR(100) UNIQUE,
    creado_en         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_suscripcion_viva ON suscripciones (barberia_id)
    WHERE estado IN ('PRUEBA', 'ACTIVA');

CREATE TABLE pagos_suscripcion (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    suscripcion_id     BIGINT        NOT NULL REFERENCES suscripciones (id) ON DELETE CASCADE,
    monto              NUMERIC(12,2) NOT NULL CHECK (monto >= 0),
    pagado_en          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    estado             VARCHAR(20)   NOT NULL CHECK (estado IN ('APROBADO', 'RECHAZADO', 'PENDIENTE')),
    referencia_externa VARCHAR(100)  NOT NULL UNIQUE
);

-- ---------------------------------------------------------------------------
-- Personas y acceso
-- ---------------------------------------------------------------------------

CREATE TABLE usuarios (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email            VARCHAR(255) NOT NULL UNIQUE CHECK (email = lower(email)),
    password_hash    VARCHAR(255),
    google_sub       VARCHAR(255) UNIQUE,
    email_verificado BOOLEAN      NOT NULL DEFAULT FALSE,
    es_admin         BOOLEAN      NOT NULL DEFAULT FALSE,
    nombre           VARCHAR(80)  NOT NULL,
    apellido         VARCHAR(80),
    telefono         VARCHAR(30),
    creado_en        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id      BIGINT       NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    token_hash      VARCHAR(255) NOT NULL UNIQUE,
    expira_en       TIMESTAMPTZ  NOT NULL,
    revocado        BOOLEAN      NOT NULL DEFAULT FALSE,
    reemplazado_por BIGINT       REFERENCES refresh_tokens (id) ON DELETE SET NULL,
    creado_en       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE tokens_verificacion (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id BIGINT       REFERENCES usuarios (id) ON DELETE CASCADE,
    email      VARCHAR(255) NOT NULL CHECK (email = lower(email)),
    tipo       VARCHAR(20)  NOT NULL CHECK (tipo IN ('RECUPERAR_CLAVE', 'CODIGO_LOGIN')),
    token_hash VARCHAR(255) NOT NULL,
    intentos   SMALLINT     NOT NULL DEFAULT 0,
    expira_en  TIMESTAMPTZ  NOT NULL,
    usado_en   TIMESTAMPTZ,
    creado_en  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE miembros_barberia (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    barberia_id    BIGINT      NOT NULL REFERENCES barberias (id) ON DELETE CASCADE,
    usuario_id     BIGINT      NOT NULL REFERENCES usuarios (id),
    rol            VARCHAR(20) NOT NULL CHECK (rol IN ('DUENO', 'BARBERO')),
    atiende_turnos BOOLEAN     NOT NULL DEFAULT TRUE,
    nombre_visible VARCHAR(80),
    foto_url       TEXT,
    activo         BOOLEAN     NOT NULL DEFAULT TRUE,
    UNIQUE (barberia_id, usuario_id),
    UNIQUE (id, barberia_id)
);

CREATE TABLE clientes (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    barberia_id BIGINT      NOT NULL REFERENCES barberias (id) ON DELETE CASCADE,
    usuario_id  BIGINT      REFERENCES usuarios (id),
    nombre      VARCHAR(80) NOT NULL,
    telefono    VARCHAR(30),
    email       VARCHAR(255),
    notas       TEXT,
    activo      BOOLEAN     NOT NULL DEFAULT TRUE,
    creado_en   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (barberia_id, usuario_id),
    UNIQUE (id, barberia_id)
);

-- ---------------------------------------------------------------------------
-- Agenda
-- ---------------------------------------------------------------------------

CREATE TABLE servicios (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    barberia_id  BIGINT        NOT NULL REFERENCES barberias (id) ON DELETE CASCADE,
    nombre       VARCHAR(80)   NOT NULL,
    descripcion  TEXT,
    duracion_min INT           NOT NULL CHECK (duracion_min > 0),
    precio       NUMERIC(12,2) NOT NULL CHECK (precio >= 0),
    activo       BOOLEAN       NOT NULL DEFAULT TRUE,
    UNIQUE (id, barberia_id)
);

CREATE TABLE horarios_atencion (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    miembro_id  BIGINT   NOT NULL REFERENCES miembros_barberia (id) ON DELETE CASCADE,
    dia_semana  SMALLINT NOT NULL CHECK (dia_semana BETWEEN 0 AND 6),
    hora_inicio TIME     NOT NULL,
    hora_fin    TIME     NOT NULL,
    CHECK (hora_fin > hora_inicio)
);

CREATE INDEX ix_horarios_atencion_miembro_dia ON horarios_atencion (miembro_id, dia_semana);

CREATE TABLE bloqueos_agenda (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    miembro_id BIGINT      NOT NULL REFERENCES miembros_barberia (id) ON DELETE CASCADE,
    inicio     TIMESTAMPTZ NOT NULL,
    fin        TIMESTAMPTZ NOT NULL,
    motivo     VARCHAR(120),
    CHECK (fin > inicio)
);

CREATE INDEX ix_bloqueos_agenda_miembro_inicio ON bloqueos_agenda (miembro_id, inicio);

CREATE TABLE turnos (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    barberia_id             BIGINT        NOT NULL REFERENCES barberias (id) ON DELETE CASCADE,
    miembro_id              BIGINT        NOT NULL,
    cliente_id              BIGINT        NOT NULL,
    servicio_id             BIGINT        NOT NULL,
    inicio                  TIMESTAMPTZ   NOT NULL,
    fin                     TIMESTAMPTZ   NOT NULL,
    estado                  VARCHAR(20)   NOT NULL DEFAULT 'CONFIRMADO'
                            CHECK (estado IN ('CONFIRMADO', 'COMPLETADO', 'CANCELADO', 'AUSENTE')),
    precio                  NUMERIC(12,2) NOT NULL CHECK (precio >= 0),
    notas                   TEXT,
    cancelado_en            TIMESTAMPTZ,
    cancelado_por           VARCHAR(20)   CHECK (cancelado_por IN ('CLIENTE', 'BARBERO')),
    recordatorio_enviado_en TIMESTAMPTZ,
    creado_en               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (id, barberia_id),
    FOREIGN KEY (miembro_id, barberia_id) REFERENCES miembros_barberia (id, barberia_id),
    FOREIGN KEY (cliente_id, barberia_id) REFERENCES clientes (id, barberia_id),
    FOREIGN KEY (servicio_id, barberia_id) REFERENCES servicios (id, barberia_id),
    CHECK (fin > inicio),
    CONSTRAINT turnos_cancelacion_completa CHECK (
        (estado = 'CANCELADO' AND cancelado_en IS NOT NULL AND cancelado_por IS NOT NULL)
        OR (estado <> 'CANCELADO' AND cancelado_en IS NULL AND cancelado_por IS NULL)
    ),
    CONSTRAINT turnos_sin_superposicion
        EXCLUDE USING gist (miembro_id WITH =, tstzrange(inicio, fin, '[)') WITH &&)
        WHERE (estado NOT IN ('CANCELADO', 'AUSENTE'))
);

CREATE INDEX ix_turnos_barberia_inicio ON turnos (barberia_id, inicio);
CREATE INDEX ix_turnos_cliente ON turnos (cliente_id);

-- ---------------------------------------------------------------------------
-- Plata
-- ---------------------------------------------------------------------------

CREATE TABLE cobros (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    barberia_id BIGINT        NOT NULL REFERENCES barberias (id) ON DELETE CASCADE,
    turno_id    BIGINT        UNIQUE,
    monto       NUMERIC(12,2) NOT NULL CHECK (monto >= 0),
    metodo      VARCHAR(20)   CHECK (metodo IN ('EFECTIVO', 'TRANSFERENCIA', 'MERCADO_PAGO', 'TARJETA', 'OTRO')),
    cobrado_en  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    concepto    VARCHAR(120),
    FOREIGN KEY (turno_id, barberia_id) REFERENCES turnos (id, barberia_id)
);

CREATE INDEX ix_cobros_barberia_cobrado_en ON cobros (barberia_id, cobrado_en);

CREATE TABLE categorias_gasto (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    barberia_id BIGINT      NOT NULL REFERENCES barberias (id) ON DELETE CASCADE,
    nombre      VARCHAR(60) NOT NULL,
    UNIQUE (barberia_id, nombre),
    UNIQUE (id, barberia_id)
);

CREATE TABLE gastos_fijos (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    barberia_id BIGINT        NOT NULL REFERENCES barberias (id) ON DELETE CASCADE,
    concepto    VARCHAR(200)  NOT NULL,
    monto       NUMERIC(12,2) NOT NULL CHECK (monto > 0),
    dia_del_mes SMALLINT      NOT NULL CHECK (dia_del_mes BETWEEN 1 AND 28),
    activo      BOOLEAN       NOT NULL DEFAULT TRUE,
    creado_en   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (id, barberia_id)
);

CREATE TABLE gastos (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    barberia_id     BIGINT        NOT NULL REFERENCES barberias (id) ON DELETE CASCADE,
    categoria_id    BIGINT,
    gasto_fijo_id   BIGINT,
    descripcion     VARCHAR(200)  NOT NULL,
    monto           NUMERIC(12,2) NOT NULL CHECK (monto > 0),
    fecha           DATE          NOT NULL,
    metodo          VARCHAR(20)   CHECK (metodo IN ('EFECTIVO', 'TRANSFERENCIA', 'MERCADO_PAGO', 'TARJETA', 'OTRO')),
    comprobante_url TEXT,
    es_fijo         BOOLEAN       NOT NULL DEFAULT FALSE,
    creado_en       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- SET NULL solo sobre categoria_id: barberia_id es NOT NULL y tiene que quedar intacto.
    FOREIGN KEY (categoria_id, barberia_id) REFERENCES categorias_gasto (id, barberia_id)
        ON DELETE SET NULL (categoria_id),
    FOREIGN KEY (gasto_fijo_id, barberia_id) REFERENCES gastos_fijos (id, barberia_id),
    CHECK (gasto_fijo_id IS NULL OR es_fijo)
);

CREATE INDEX ix_gastos_barberia_fecha ON gastos (barberia_id, fecha);
CREATE INDEX ix_gastos_gasto_fijo_fecha ON gastos (gasto_fijo_id, fecha);
