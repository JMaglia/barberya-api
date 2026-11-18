package com.barberya.disponibilidad;

import java.time.Instant;

/** Intervalo semiabierto {@code [inicio, fin)}, el mismo criterio {@code '[)'} que usa la base. */
public record Intervalo(Instant inicio, Instant fin) {

	public Intervalo {
		if (!inicio.isBefore(fin)) {
			throw new IllegalArgumentException("El intervalo tiene que terminar después de empezar");
		}
	}

	/** Dos intervalos contiguos no se superponen. */
	public boolean seSuperponeCon(Instant otroInicio, Instant otroFin) {
		return inicio.isBefore(otroFin) && otroInicio.isBefore(fin);
	}

}
