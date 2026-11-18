package com.barberya.agenda.dto;

import java.time.Instant;

import com.barberya.agenda.BloqueoAgenda;

public record BloqueoResponse(Long id, Instant inicio, Instant fin, String motivo) {

	public static BloqueoResponse de(BloqueoAgenda b) {
		return new BloqueoResponse(b.getId(), b.getInicio(), b.getFin(), b.getMotivo());
	}

}
