package com.barberya.disponibilidad;

import java.time.Instant;

/** Turno o bloqueo de un miembro, sin cargar la entidad (proyección de {@link DisponibilidadRepository}). */
public record OcupacionMiembro(Long miembroId, Instant inicio, Instant fin) {

	Intervalo intervalo() {
		return new Intervalo(inicio, fin);
	}

}
