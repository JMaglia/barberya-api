package com.barberya.disponibilidad;

import java.time.LocalTime;

/** Franja de atención semanal. {@code diaSemana}: 0 = domingo. Nunca cruza la medianoche. */
public record FranjaSemanal(int diaSemana, LocalTime inicio, LocalTime fin) {

	public FranjaSemanal {
		if (diaSemana < 0 || diaSemana > 6) {
			throw new IllegalArgumentException("diaSemana fuera de 0..6: " + diaSemana);
		}
		if (!inicio.isBefore(fin)) {
			throw new IllegalArgumentException("La franja tiene que terminar después de empezar");
		}
	}

}
