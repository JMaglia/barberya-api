package com.barberya.disponibilidad;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

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

	/** Día de la semana de una fecha con el criterio de la base: 0 = domingo. */
	public static int diaSemana(LocalDate fecha) {
		return fecha.getDayOfWeek().getValue() % 7;
	}

	/** Comienzo de la franja en esa fecha local, como instante de la zona. */
	public Instant inicioEn(LocalDate fecha, ZoneId zona) {
		return ZonedDateTime.of(fecha, inicio, zona).toInstant();
	}

	/** Cierre de la franja en esa fecha local, como instante de la zona. */
	public Instant finEn(LocalDate fecha, ZoneId zona) {
		return ZonedDateTime.of(fecha, fin, zona).toInstant();
	}

	/** Si [desde, hasta) entra entero en la franja de esa fecha local (puede tocar los bordes). */
	public boolean contiene(LocalDate fecha, ZoneId zona, Instant desde, Instant hasta) {
		return diaSemana(fecha) == diaSemana && !desde.isBefore(inicioEn(fecha, zona))
				&& !hasta.isAfter(finEn(fecha, zona));
	}

}
