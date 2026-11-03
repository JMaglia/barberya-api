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

## Turnos y plata
- Al marcar un turno como completado se registra el cobro automáticamente, sin medio de pago.
  No se pregunta efectivo / Mercado Pago corte por corte.
- Gastos: fijos (alquiler, luz, internet) y variables (insumos, herramientas).
- Estadística clave del independiente: con cuántos cortes cubrió sus gastos fijos del mes.

## Notificaciones
- MVP: mail de confirmación y recordatorio + botón que abre WhatsApp con el mensaje armado (wa.me).
- WhatsApp automático por API: extra pago, más adelante (cuesta USD por mensaje).

## Negocio
- Suscripción mensual en pesos, cobrada con suscripciones de Mercado Pago (preapproval).
- Precio inicial sugerido del plan Independiente: $9.900/mes, prueba gratis de 30 días.
- Factura C electrónica automática por cada cobro (ARCA, WSFE).

## Técnicas
- Fechas en UTC; la app corre con `-Duser.timezone=UTC` (Windows manda el alias viejo
  `America/Buenos_Aires`, que Postgres 16 rechaza).
- PostgreSQL 16, Flyway, Testcontainers. Docker Compose para la base local.
