package com.barberya.agenda.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import com.barberya.agenda.BloqueoAgenda;
import com.barberya.turno.dto.TurnoAgendaResponse;

/** La agenda de un rango de días locales: están todos los días, aunque no tengan turnos ni bloqueos. */
public record AgendaResponse(String zonaHoraria, List<DiaAgenda> dias) {

	public record DiaAgenda(LocalDate fecha, List<TurnoAgendaResponse> turnos, List<BloqueoDia> bloqueos) {
	}

	/** Un bloqueo de varios días aparece en cada día que toca, siempre con su inicio y fin completos. */
	public record BloqueoDia(Long id, OffsetDateTime inicio, OffsetDateTime fin, String motivo) {

		public static BloqueoDia de(BloqueoAgenda b, ZoneId zona) {
			return new BloqueoDia(b.getId(), b.getInicio().atZone(zona).toOffsetDateTime(),
					b.getFin().atZone(zona).toOffsetDateTime(), b.getMotivo());
		}

	}

}
