package com.barberya.disponibilidad;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Todo lo que necesita {@link CalculadoraDisponibilidad} para un miembro.
 *
 * @param zona        zona horaria de la barbería
 * @param desde       primer día (fecha local), inclusive
 * @param hasta       último día (fecha local), inclusive
 * @param franjas     horario semanal del miembro
 * @param turnos      turnos que ocupan (sin expandir por la pausa)
 * @param bloqueos    bloqueos de agenda
 * @param duracion    duración del servicio
 * @param pausa       pausa entre turnos, antes y después de cada turno
 * @param paso        paso de la grilla de inicios
 * @param ahora       los inicios tienen que ser posteriores
 * @param fechaMaxima último día reservable (fecha local), inclusive
 */
public record ParametrosDisponibilidad(ZoneId zona, LocalDate desde, LocalDate hasta, List<FranjaSemanal> franjas,
		List<Intervalo> turnos, List<Intervalo> bloqueos, Duration duracion, Duration pausa, Duration paso, Instant ahora,
		LocalDate fechaMaxima) {

	public ParametrosDisponibilidad {
		if (hasta.isBefore(desde)) {
			throw new IllegalArgumentException("hasta no puede ser anterior a desde");
		}
		if (duracion.isNegative() || duracion.isZero() || paso.isNegative() || paso.isZero()) {
			throw new IllegalArgumentException("La duración y el paso tienen que ser positivos");
		}
		if (pausa.isNegative()) {
			throw new IllegalArgumentException("La pausa no puede ser negativa");
		}
		franjas = List.copyOf(franjas);
		turnos = List.copyOf(turnos);
		bloqueos = List.copyOf(bloqueos);
	}

}
