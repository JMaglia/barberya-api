package com.barberya.agenda.dto;

import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Las reglas entre campos y entre franjas (inicio < fin, múltiplos de 5, superposición) están en ValidadorHorarios. */
public record FranjaHorariaRequest(
		@NotNull(message = "Ingresá el día")
		@Min(value = 0, message = "El día va de 0 (domingo) a 6 (sábado)")
		@Max(value = 6, message = "El día va de 0 (domingo) a 6 (sábado)")
		Integer diaSemana,

		@NotNull(message = "Ingresá la hora de inicio")
		@JsonFormat(pattern = "HH:mm")
		LocalTime horaInicio,

		@NotNull(message = "Ingresá la hora de fin")
		@JsonFormat(pattern = "HH:mm")
		LocalTime horaFin) {
}
