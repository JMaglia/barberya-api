package com.barberya.turno.dto;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotNull;

/** {@code inicio}: uno de los horarios que devolvió la disponibilidad, con su offset. */
public record ReservarTurnoRequest(
		@NotNull(message = "Elegí un servicio")
		Long servicioId,

		@NotNull(message = "Elegí un horario")
		OffsetDateTime inicio) {
}
