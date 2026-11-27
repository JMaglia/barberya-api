# Decisiones de producto

Registro de decisiones cerradas. Si algo cambia, se agrega una línea nueva con fecha; no se borra la anterior.

## Alcance
- **Versión 1 = barbero independiente** (barbero y dueño a la vez, una barbería con un solo miembro).
  Dueño con empleados, comisiones, pagos a barberos, invitaciones y recepcionista quedan para la v2.
- Una sola web para todas las barberías. Cada barbería tiene su link público por slug.

## Clientes y reserva
- El cliente necesita cuenta para reservar, pero liviana: Google o mail con código, más celular.
  La contraseña no es obligatoria.
- Entrando por el link de una barbería, el cliente puede mirar sin cuenta; el login se pide recién al confirmar.
- Entrando por el link general, se pide login primero y se redirige según el rol del mail
  (barbero → su inicio; sin rol → Mis turnos).
- Flujo de reserva: servicio → barbero → horario. El barbero se saltea si hay uno solo.
- El turno queda confirmado al reservar (no hay aprobación manual del barbero).
- El cliente puede cancelar hasta X horas antes (regla configurable por barbería, por defecto 24 h).
- 2026-11-03: en el login por código, el usuario se crea recién al validar el código; hasta entonces
  el código se asocia al mail (`tokens_verificacion.usuario_id` es nullable).
- 2026-11-17: `usuarios.nombre` pasa a nullable: quien entra por código no tiene nombre hasta completar el perfil.
  El front pide nombre y celular (`PATCH /me`, `perfilCompleto`) antes de dejar reservar.
- 2026-11-17: al entrar con Google o con código en una cuenta con el mail sin verificar, se borra su contraseña y se
  cierran sus sesiones, para que nadie pueda registrar un mail ajeno y seguir entrando cuando el dueño real lo usa.
- 2026-11-24: anticipación mínima para reservar, configurable por barbería (`reserva_min_minutos`, por defecto 60):
  no se ofrecen ni se aceptan inicios a menos de ese tiempo de ahora.
- 2026-11-24: un cliente puede tener como máximo 4 turnos futuros `CONFIRMADO` en una misma barbería.
- 2026-11-24: el dueño no reserva como cliente en su propia barbería; los turnos de su agenda los carga desde la agenda.

## Turnos y plata
- Al marcar un turno como completado se registra el cobro automáticamente, sin medio de pago.
  No se pregunta efectivo / Mercado Pago corte por corte.
- Gastos: fijos (alquiler, luz, internet) y variables (insumos, herramientas).
- Estadística clave del independiente: con cuántos cortes cubrió sus gastos fijos del mes.
- 2026-11-03: los gastos fijos tienen su propia tabla (`gastos_fijos`). Un fijo está pendiente en un mes
  si no tiene un gasto asociado en ese mes.
- 2026-11-12: los proveedores pasan a V1 para tener historial de compras por proveedor, sin tablas de productos
  ni detalle de compra: el gasto guarda proveedor y cantidad (opcionales) y el precio unitario se calcula
  (`monto / cantidad`). Proveedores y categorías con gastos no se borran; los proveedores tienen baja lógica.
- 2026-11-27: un turno solo se puede marcar como completado o "no vino" (`AUSENTE`) desde que empezó (inicio <= ahora).
- 2026-11-27: revertir devuelve un turno `COMPLETADO` o `AUSENTE` a `CONFIRMADO`; si era `COMPLETADO`, se borra su cobro.
  Un `AUSENTE` vuelve a ocupar su horario: si mientras tanto se reservó otro turno ahí, no se puede revertir.
- 2026-11-27: el barbero puede cargar turnos a mano en su agenda fuera de su horario, fuera de la grilla de 15 min,
  sin anticipación mínima y más allá de `reserva_max_dias`. Solo se impide la superposición con otro turno; quedar fuera
  de horario o pisar un bloqueo se avisa, pero no impide cargarlo.
- 2026-11-27: el barbero puede cargar un turno que empezó hasta 24 h antes, para registrar a quien vino sin turno y
  después completarlo.
- 2026-11-27: un cliente cargado a mano por el barbero tiene ficha sin usuario (`clientes.usuario_id` NULL).

## Notificaciones
- MVP: mail de confirmación y recordatorio + botón que abre WhatsApp con el mensaje armado (wa.me).
- WhatsApp automático por API: extra pago, más adelante (cuesta USD por mensaje).
- 2026-11-24: no se mandan mails de confirmación ni de recordatorio de turnos; los mails son solo de cuenta (código de
  login, recuperar contraseña). El cliente puede agregar el turno a su calendario (.ics). El barbero tiene un botón
  opcional de WhatsApp (wa.me) en el detalle del turno, que arma el front, para usar si quiere (no es una tarea diaria).
  WhatsApp automático por API queda como extra pago a futuro; `turnos.recordatorio_enviado_en` se mantiene para eso.

## Negocio
- Suscripción mensual en pesos, cobrada con suscripciones de Mercado Pago (preapproval).
- Precio inicial sugerido del plan Independiente: $9.900/mes, prueba gratis de 30 días.
- Factura C electrónica automática por cada pago de la suscripción (ARCA, WSFE).
- 2026-11-03: la factura C es la que BarberYA le emite al barbero por el pago de la suscripción, no por los
  cobros de los cortes. No va en V1; queda para la fase de cobro.
- 2026-11-03: el acceso lo decide la suscripción; `barberias.activa` es solo una suspensión manual del admin.

## Técnicas
- Fechas en UTC; la app corre con `-Duser.timezone=UTC` (Windows manda el alias viejo
  `America/Buenos_Aires`, que Postgres 16 rechaza).
- PostgreSQL 16, Flyway, Testcontainers. Docker Compose para la base local.
- 2026-11-17: el horario semanal no admite `24:00` ni franjas que crucen la medianoche (`LocalTime` no tiene `24:00`):
  la última hora de cierre posible es `23:55`. Alcanza para las barberías del piloto; si alguna cierra más tarde, se revisa.
