package com.barberya.agenda.dto;

import java.time.Instant;

import com.barberya.agenda.BloqueoAgenda;

/** {@code turnosAfectados}: turnos CONFIRMADO del miembro que se pisan con el bloqueo. No se cancelan; el front avisa. */
public record BloqueoCreadoResponse(Long id, Instant inicio, Instant fin, String motivo, long turnosAfectados) {

	public static BloqueoCreadoResponse de(BloqueoAgenda b, long turnosAfectados) {
		return new BloqueoCreadoResponse(b.getId(), b.getInicio(), b.getFin(), b.getMotivo(), turnosAfectados);
	}

}
