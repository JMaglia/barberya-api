package com.barberya.disponibilidad;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CalculadoraDisponibilidadTest {

	private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");
	private static final ZoneId NUEVA_YORK = ZoneId.of("America/New_York");
	private static final int DOMINGO = 0, LUNES = 1, MIERCOLES = 3, JUEVES = 4, SABADO = 6;

	/** Miércoles. */
	private static final LocalDate DIA = LocalDate.of(2026, 11, 18);

	@Test
	void unDiaSinFranjasApareceVacio() {
		Map<LocalDate, List<ZonedDateTime>> resultado = new Caso()
				.rango(DIA.minusDays(1), DIA.plusDays(1))
				.franja(MIERCOLES, "09:00", "10:00")
				.calcular();

		assertThat(resultado.keySet()).containsExactly(DIA.minusDays(1), DIA, DIA.plusDays(1));
		assertThat(resultado.get(DIA.minusDays(1))).isEmpty();
		assertThat(resultado.get(DIA.plusDays(1))).isEmpty();
		assertThat(horas(resultado.get(DIA))).containsExactly("09:00", "09:15", "09:30");
	}

	@Test
	void turnoPartidoNoOfreceElHuecoDelMedio() {
		List<String> horas = new Caso()
				.franja(MIERCOLES, "09:00", "13:00")
				.franja(MIERCOLES, "15:00", "19:00")
				.horasDelDia();

		assertThat(horas).hasSize(30).startsWith("09:00").endsWith("18:30")
				.contains("12:30", "15:00")
				.doesNotContain("12:45", "13:00", "14:00", "14:45", "18:45");
	}

	@Test
	void servicioMasLargoQueLaFranjaNoTieneLugar() {
		assertThat(new Caso().franja(MIERCOLES, "09:00", "10:00").duracion(90).horasDelDia()).isEmpty();
		assertThat(new Caso().franja(MIERCOLES, "09:00", "10:00").duracion(60).horasDelDia()).containsExactly("09:00");
	}

	@Test
	void turnoEnElMedioDeLaFranja() {
		List<String> horas = new Caso()
				.franja(MIERCOLES, "09:00", "12:00")
				.turno(DIA, "10:00", "10:30")
				.horasDelDia();

		assertThat(horas).containsExactly("09:00", "09:15", "09:30", "10:30", "10:45", "11:00", "11:15", "11:30");
	}

	@Test
	void turnoContiguoSinPausaEsValido() {
		List<String> horas = new Caso()
				.franja(MIERCOLES, "09:00", "11:00")
				.turno(DIA, "10:00", "10:30")
				.horasDelDia();

		// 09:30–10:00 termina justo cuando empieza el turno; 10:30 empieza justo cuando termina.
		assertThat(horas).contains("09:30", "10:30").doesNotContain("09:45", "10:00", "10:15");
	}

	@Test
	void laPausaSeRespetaAntesYDespuesDelTurno() {
		List<String> horas = new Caso()
				.franja(MIERCOLES, "09:00", "12:00")
				.turno(DIA, "10:00", "10:30")
				.pausa(10)
				.horasDelDia();

		// El turno ocupa [09:50, 10:40): 09:30 pisa la pausa de antes; 10:40 es "justo después".
		assertThat(horas).containsExactly("09:00", "09:15", "10:40", "10:45", "11:00", "11:15", "11:30");
	}

	@Test
	void bloqueoParcialSacaSoloEsaParte() {
		List<String> horas = new Caso()
				.franja(MIERCOLES, "09:00", "13:00")
				.bloqueo(DIA, "11:00", DIA, "12:00")
				.horasDelDia();

		assertThat(horas).containsExactly("09:00", "09:15", "09:30", "09:45", "10:00", "10:15", "10:30",
				"12:00", "12:15", "12:30");
	}

	@Test
	void bloqueoDeDiaCompletoDejaElDiaVacio() {
		List<String> horas = new Caso()
				.franja(MIERCOLES, "09:00", "13:00")
				.franja(MIERCOLES, "15:00", "19:00")
				.bloqueo(DIA, "00:00", DIA.plusDays(1), "00:00")
				.horasDelDia();

		assertThat(horas).isEmpty();
	}

	@Test
	void bloqueoQueCruzaDias() {
		LocalDate sabado = LocalDate.of(2026, 11, 21);
		LocalDate domingo = sabado.plusDays(1);
		LocalDate lunes = sabado.plusDays(2);

		Map<LocalDate, List<ZonedDateTime>> resultado = new Caso()
				.rango(sabado, lunes)
				.franja(SABADO, "09:00", "13:00")
				.franja(DOMINGO, "09:00", "13:00")
				.franja(LUNES, "09:00", "13:00")
				.bloqueo(sabado, "12:00", lunes, "10:00")
				.calcular();

		assertThat(horas(resultado.get(sabado))).endsWith("11:30").doesNotContain("11:45", "12:00");
		assertThat(resultado.get(domingo)).isEmpty();
		assertThat(horas(resultado.get(lunes))).startsWith("10:00").doesNotContain("09:45");
	}

	@Test
	void justoDespuesDeUnTurnoQueNoTerminaEnMultiploDe15() {
		List<String> horas = new Caso()
				.franja(MIERCOLES, "09:00", "12:00")
				.turno(DIA, "10:00", "10:40")
				.horasDelDia();

		assertThat(horas).containsExactly("09:00", "09:15", "09:30", "10:40", "10:45", "11:00", "11:15", "11:30");
	}

	@Test
	void justoDespuesDeUnBloqueo() {
		List<String> horas = new Caso()
				.franja(MIERCOLES, "09:00", "12:00")
				.bloqueo(DIA, "09:00", DIA, "11:10")
				.horasDelDia();

		assertThat(horas).containsExactly("11:10", "11:15", "11:30");
	}

	@Test
	void justoDespuesFueraDeLaFranjaNoSeOfrece() {
		List<String> horas = new Caso()
				.franja(MIERCOLES, "09:00", "11:00")
				.turno(DIA, "08:00", "08:50") // termina antes de que abra
				.bloqueo(DIA, "10:00", DIA, "10:50") // 10:50 + 30 min se pasa del cierre
				.horasDelDia();

		assertThat(horas).containsExactly("09:00", "09:15", "09:30");
	}

	@Test
	void sinRepetidosCuandoJustoDespuesCoincideConLaGrilla() {
		List<String> horas = new Caso()
				.franja(MIERCOLES, "09:00", "11:00")
				.turno(DIA, "09:00", "09:30")
				.bloqueo(DIA, "09:00", DIA, "09:30")
				.horasDelDia();

		assertThat(horas).containsExactly("09:30", "09:45", "10:00", "10:15", "10:30");
	}

	@Test
	void noOfreceIniciosAnterioresAlPrimerInicioPermitido() {
		assertThat(new Caso().franja(MIERCOLES, "09:00", "12:00").primerInicio(DIA, "11:05").horasDelDia())
				.containsExactly("11:15", "11:30");
		// El primer inicio permitido es inclusivo.
		assertThat(new Caso().franja(MIERCOLES, "09:00", "12:00").primerInicio(DIA, "11:00").horasDelDia())
				.containsExactly("11:00", "11:15", "11:30");
		assertThat(new Caso().franja(MIERCOLES, "09:00", "12:00").primerInicio(DIA, "11:31").horasDelDia()).isEmpty();
	}

	@Test
	void diasPosterioresALaFechaMaximaSalenVacios() {
		Map<LocalDate, List<ZonedDateTime>> resultado = new Caso()
				.rango(DIA, DIA.plusDays(1))
				.franja(MIERCOLES, "09:00", "10:00")
				.franja(JUEVES, "09:00", "10:00")
				.fechaMaxima(DIA)
				.calcular();

		assertThat(horas(resultado.get(DIA))).containsExactly("09:00", "09:15", "09:30");
		assertThat(resultado.get(DIA.plusDays(1))).isEmpty();
	}

	@Test
	void franjaQueNoEmpiezaEnPunto() {
		assertThat(new Caso().franja(MIERCOLES, "09:10", "10:30").horasDelDia())
				.containsExactly("09:10", "09:25", "09:40", "09:55");
	}

	@Test
	void cambioDeHorarioQueAdelantaElReloj() {
		// 2026-03-08, domingo: en Nueva York las 02:00 pasan a ser las 03:00.
		LocalDate dia = LocalDate.of(2026, 3, 8);
		List<ZonedDateTime> inicios = new Caso()
				.zona(NUEVA_YORK)
				.rango(dia, dia)
				.franja(DOMINGO, "00:00", "05:00")
				.primerInicio(Instant.parse("2026-03-01T00:00:00Z"))
				.calcular().get(dia);

		// La franja dura 4 horas reales: 16 inicios de 15 min, menos el último (no entra la media hora).
		assertThat(inicios).hasSize(15);
		assertThat(inicios).extracting(ZonedDateTime::toLocalTime).noneMatch(t -> t.getHour() == 2);
		assertThat(inicios.getFirst().toOffsetDateTime().toString()).isEqualTo("2026-03-08T00:00-05:00");
		assertThat(inicios.getLast().toOffsetDateTime().toString()).isEqualTo("2026-03-08T04:30-04:00");
		assertCreciente(inicios);
	}

	@Test
	void cambioDeHorarioQueAtrasaElReloj() {
		// 2026-11-01, domingo: en Nueva York la 01:00–02:00 pasa dos veces.
		LocalDate dia = LocalDate.of(2026, 11, 1);
		List<ZonedDateTime> inicios = new Caso()
				.zona(NUEVA_YORK)
				.rango(dia, dia)
				.franja(DOMINGO, "00:00", "03:00")
				.primerInicio(Instant.parse("2026-10-25T00:00:00Z"))
				.calcular().get(dia);

		assertThat(inicios).hasSize(15);
		assertThat(inicios).filteredOn(z -> z.toLocalTime().equals(LocalTime.of(1, 0)))
				.extracting(z -> z.getOffset())
				.containsExactly(ZoneOffset.ofHours(-4), ZoneOffset.ofHours(-5));
		assertCreciente(inicios);
	}

	@Test
	void devuelveLosIniciosEnLaZonaDeLaBarberia() {
		List<ZonedDateTime> inicios = new Caso().franja(MIERCOLES, "09:00", "09:30").calcular().get(DIA);

		assertThat(inicios).hasSize(1);
		assertThat(inicios.getFirst().toOffsetDateTime().toString()).isEqualTo("2026-11-18T09:00-03:00");
	}

	// ---------------------------------------------------------------------------------------------------------

	private static List<String> horas(List<ZonedDateTime> inicios) {
		return inicios.stream().map(z -> z.toLocalTime().toString()).toList();
	}

	private static void assertCreciente(List<ZonedDateTime> inicios) {
		for (int i = 1; i < inicios.size(); i++) {
			assertThat(inicios.get(i).toInstant()).isAfter(inicios.get(i - 1).toInstant());
		}
	}

	/** Parámetros con valores por defecto razonables; cada test cambia solo lo que prueba. */
	private static class Caso {

		private ZoneId zona = BUENOS_AIRES;
		private LocalDate desde = DIA;
		private LocalDate hasta = DIA;
		private final List<FranjaSemanal> franjas = new ArrayList<>();
		private final List<Intervalo> turnos = new ArrayList<>();
		private final List<Intervalo> bloqueos = new ArrayList<>();
		private int duracionMin = 30;
		private int pausaMin = 0;
		private Instant primerInicio;
		private LocalDate fechaMaxima = LocalDate.of(2027, 12, 31);

		Caso zona(ZoneId valor) {
			zona = valor;
			return this;
		}

		Caso rango(LocalDate d, LocalDate h) {
			desde = d;
			hasta = h;
			return this;
		}

		Caso franja(int diaSemana, String inicio, String fin) {
			franjas.add(new FranjaSemanal(diaSemana, LocalTime.parse(inicio), LocalTime.parse(fin)));
			return this;
		}

		Caso turno(LocalDate dia, String inicio, String fin) {
			turnos.add(new Intervalo(instante(dia, inicio), instante(dia, fin)));
			return this;
		}

		Caso bloqueo(LocalDate diaInicio, String inicio, LocalDate diaFin, String fin) {
			bloqueos.add(new Intervalo(instante(diaInicio, inicio), instante(diaFin, fin)));
			return this;
		}

		Caso duracion(int minutos) {
			duracionMin = minutos;
			return this;
		}

		Caso pausa(int minutos) {
			pausaMin = minutos;
			return this;
		}

		Caso primerInicio(LocalDate dia, String hora) {
			primerInicio = instante(dia, hora);
			return this;
		}

		Caso primerInicio(Instant valor) {
			primerInicio = valor;
			return this;
		}

		Caso fechaMaxima(LocalDate valor) {
			fechaMaxima = valor;
			return this;
		}

		Map<LocalDate, List<ZonedDateTime>> calcular() {
			Instant primerInicioEfectivo = primerInicio != null ? primerInicio
					: desde.minusDays(1).atStartOfDay(zona).toInstant();
			return CalculadoraDisponibilidad.calcular(new ParametrosDisponibilidad(zona, desde, hasta, franjas, turnos,
					bloqueos, Duration.ofMinutes(duracionMin), Duration.ofMinutes(pausaMin), Duration.ofMinutes(15),
					primerInicioEfectivo, fechaMaxima));
		}

		/** Inicios del día {@link #DIA} como "HH:mm". */
		List<String> horasDelDia() {
			return horas(calcular().get(DIA));
		}

		private Instant instante(LocalDate dia, String hora) {
			return ZonedDateTime.of(dia, LocalTime.parse(hora), zona).toInstant();
		}
	}
}
