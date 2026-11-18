package com.barberya.disponibilidad;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * Calcula los inicios libres de un miembro, día por día. Pura: sin Spring, sin base y sin reloj propio
 * ("ahora" llega por parámetro).
 *
 * <p>Un inicio {@code t} es válido si {@code [t, t + duración)} entra entero en una franja del día, es posterior a
 * "ahora", no se superpone con ningún bloqueo ni con ningún turno expandido por la pausa
 * ({@code [turno.inicio − pausa, turno.fin + pausa)}). Contiguo no es superpuesto (criterio {@code '[)'}, igual que
 * {@code turnos_sin_superposicion}).
 *
 * <p>Candidatos: la grilla de cada franja (inicio de franja + k × paso) y, para no dejar huecos, el instante "justo
 * después" de cada ocupación (fin de turno + pausa, fin de bloqueo) si cae dentro de una franja.
 */
public final class CalculadoraDisponibilidad {

	private CalculadoraDisponibilidad() {
	}

	/**
	 * @return todos los días de {@code desde..hasta} en orden, cada uno con sus inicios ordenados y sin repetidos
	 *         (lista vacía si no hay ninguno), expresados en la zona de la barbería.
	 */
	public static Map<LocalDate, List<ZonedDateTime>> calcular(ParametrosDisponibilidad p) {
		List<Intervalo> ocupados = new ArrayList<>(p.bloqueos());
		List<Instant> justoDespues = new ArrayList<>();
		for (Intervalo turno : p.turnos()) {
			ocupados.add(new Intervalo(turno.inicio().minus(p.pausa()), turno.fin().plus(p.pausa())));
			justoDespues.add(turno.fin().plus(p.pausa()));
		}
		for (Intervalo bloqueo : p.bloqueos()) {
			justoDespues.add(bloqueo.fin());
		}

		Map<LocalDate, List<ZonedDateTime>> resultado = new LinkedHashMap<>();
		for (LocalDate fecha = p.desde(); !fecha.isAfter(p.hasta()); fecha = fecha.plusDays(1)) {
			resultado.put(fecha, fecha.isAfter(p.fechaMaxima()) ? List.of() : inicios(p, fecha, ocupados, justoDespues));
		}
		return resultado;
	}

	private static List<ZonedDateTime> inicios(ParametrosDisponibilidad p, LocalDate fecha, List<Intervalo> ocupados,
			List<Instant> justoDespues) {
		int diaSemana = fecha.getDayOfWeek().getValue() % 7;
		NavigableSet<Instant> libres = new TreeSet<>();
		for (FranjaSemanal franja : p.franjas()) {
			if (franja.diaSemana() != diaSemana) {
				continue;
			}
			Instant inicioFranja = ZonedDateTime.of(fecha, franja.inicio(), p.zona()).toInstant();
			Instant finFranja = ZonedDateTime.of(fecha, franja.fin(), p.zona()).toInstant();
			Instant ultimoInicio = finFranja.minus(p.duracion());
			if (ultimoInicio.isBefore(inicioFranja)) {
				continue;
			}

			List<Instant> candidatos = new ArrayList<>();
			for (Instant t = inicioFranja; !t.isAfter(ultimoInicio); t = t.plus(p.paso())) {
				candidatos.add(t);
			}
			for (Instant t : justoDespues) {
				if (!t.isBefore(inicioFranja) && !t.isAfter(ultimoInicio)) {
					candidatos.add(t);
				}
			}
			for (Instant t : candidatos) {
				if (t.isAfter(p.ahora()) && libre(t, t.plus(p.duracion()), ocupados)) {
					libres.add(t);
				}
			}
		}
		return libres.stream().map(t -> t.atZone(p.zona())).toList();
	}

	private static boolean libre(Instant inicio, Instant fin, List<Intervalo> ocupados) {
		for (Intervalo ocupado : ocupados) {
			if (ocupado.seSuperponeCon(inicio, fin)) {
				return false;
			}
		}
		return true;
	}

}
