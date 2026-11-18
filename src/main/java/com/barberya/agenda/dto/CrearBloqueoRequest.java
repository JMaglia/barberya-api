package com.barberya.agenda.dto;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Instantes ISO-8601 con offset. "Día completo" lo arma el front con 00:00 y 24:00 en la zona de la barbería.
 * Las reglas entre campos están en BloqueoService.
 */
public record CrearBloqueoRequest(
		@NotNull(message = "Ingresá el inicio del bloqueo")
		OffsetDateTime inicio,

		@NotNull(message = "Ingresá el fin del bloqueo")
		OffsetDateTime fin,

		@Size(max = 120, message = "El motivo puede tener hasta 120 caracteres")
		String motivo) {
}
