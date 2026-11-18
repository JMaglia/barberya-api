package com.barberya.agenda;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.barberya.agenda.dto.FranjaHorariaRequest;
import com.barberya.error.DatosInvalidosException;

/**
 * Reglas de la semana que la base no garantiza. El campo del error es la posición de la franja en la lista ({@code [i]}).
 * Sin 24:00 ni franjas que crucen la medianoche (ver docs/decisiones.md).
 */
final class ValidadorHorarios {

	static final int MAX_FRANJAS_POR_DIA = 4;
	private static final String[] DIAS = { "domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado" };

	private ValidadorHorarios() {
	}

	static void validar(List<FranjaHorariaRequest> franjas) {
		if (franjas == null) {
			throw new DatosInvalidosException("franjas", "Mandá la lista de franjas (vacía si cerrás todos los días)");
		}
		for (int i = 0; i < franjas.size(); i++) {
			FranjaHorariaRequest f = franjas.get(i);
			if (f == null) {
				throw error(i, "La franja está vacía");
			}
			if (!multiploDe5(f.horaInicio()) || !multiploDe5(f.horaFin())) {
				throw error(i, "El " + dia(f) + ", " + texto(f) + ": las horas tienen que ser múltiplos de 5 minutos");
			}
			if (!f.horaInicio().isBefore(f.horaFin())) {
				throw error(i, "El " + dia(f) + ", " + texto(f) + ": la hora de fin tiene que ser posterior a la de inicio");
			}
		}
		for (int dia = 0; dia <= 6; dia++) {
			List<Integer> delDia = new ArrayList<>();
			for (int i = 0; i < franjas.size(); i++) {
				if (franjas.get(i).diaSemana() == dia) {
					delDia.add(i);
				}
			}
			if (delDia.size() > MAX_FRANJAS_POR_DIA) {
				throw error(delDia.get(MAX_FRANJAS_POR_DIA),
						"El " + DIAS[dia] + " puede tener hasta " + MAX_FRANJAS_POR_DIA + " franjas");
			}
			delDia.sort(Comparator.comparing(i -> franjas.get(i).horaInicio()));
			for (int k = 1; k < delDia.size(); k++) {
				FranjaHorariaRequest anterior = franjas.get(delDia.get(k - 1));
				FranjaHorariaRequest actual = franjas.get(delDia.get(k));
				// Contiguas (09:00–13:00 y 13:00–17:00) valen.
				if (actual.horaInicio().isBefore(anterior.horaFin())) {
					throw error(delDia.get(k), "El " + DIAS[dia] + ", " + texto(actual) + " se superpone con " + texto(anterior));
				}
			}
		}
	}

	private static boolean multiploDe5(LocalTime hora) {
		return hora.getSecond() == 0 && hora.getNano() == 0 && hora.getMinute() % 5 == 0;
	}

	private static String dia(FranjaHorariaRequest f) {
		return DIAS[f.diaSemana()];
	}

	private static String texto(FranjaHorariaRequest f) {
		return f.horaInicio() + "–" + f.horaFin();
	}

	private static DatosInvalidosException error(int indice, String mensaje) {
		return new DatosInvalidosException("[" + indice + "]", mensaje);
	}

}
