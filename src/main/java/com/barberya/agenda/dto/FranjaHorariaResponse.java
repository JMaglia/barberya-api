package com.barberya.agenda.dto;

import java.time.LocalTime;

import com.barberya.agenda.HorarioAtencion;
import com.fasterxml.jackson.annotation.JsonFormat;

/** Misma forma en el GET privado y en el público: sin ids de franja ni de miembro. */
public record FranjaHorariaResponse(
		int diaSemana,
		@JsonFormat(pattern = "HH:mm") LocalTime horaInicio,
		@JsonFormat(pattern = "HH:mm") LocalTime horaFin) {

	public static FranjaHorariaResponse de(HorarioAtencion h) {
		return new FranjaHorariaResponse(h.getDiaSemana(), h.getHoraInicio(), h.getHoraFin());
	}

}
