package com.barberya.turno;

import java.util.Locale;
import java.util.Optional;

/** Qué parte de "Mis turnos" se pide: {@code ?alcance=proximos} o {@code ?alcance=pasados}. */
public enum AlcanceMisTurnos {

	/** CONFIRMADO desde ahora, orden ascendente. */
	PROXIMOS,
	/** El resto (ya empezaron, o cancelados, completados o ausentes), orden descendente. */
	PASADOS;

	public static Optional<AlcanceMisTurnos> desde(String valor) {
		if (valor == null) {
			return Optional.empty();
		}
		return switch (valor.trim().toLowerCase(Locale.ROOT)) {
			case "proximos" -> Optional.of(PROXIMOS);
			case "pasados" -> Optional.of(PASADOS);
			default -> Optional.empty();
		};
	}

}
