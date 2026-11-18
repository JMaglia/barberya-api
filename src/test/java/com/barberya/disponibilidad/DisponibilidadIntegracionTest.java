package com.barberya.disponibilidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import com.barberya.ApiIntegracionTest;
import com.barberya.RelojDeTest;
import com.barberya.barberia.Barberia;
import com.barberya.barberia.BarberiaRepository;
import com.barberya.barberia.MiembroBarberia;
import com.barberya.servicio.Servicio;
import com.barberya.servicio.ServicioRepository;
import com.jayway.jsonpath.JsonPath;

class DisponibilidadIntegracionTest extends ApiIntegracionTest {

	private static final String URL = "/api/v1/public/barberias/";
	private static final ZoneOffset ARG = ZoneOffset.ofHours(-3);

	/** "Ahora" en todos los tests: lunes 16/11/2026, 09:00 en Buenos Aires. */
	private static final Instant AHORA = Instant.parse("2026-11-16T12:00:00Z");
	private static final LocalDate LUNES = LocalDate.of(2026, 11, 16);
	private static final LocalDate MIERCOLES = LUNES.plusDays(2);

	/** Lunes y miércoles de 09:00 a 11:00. */
	private static final String HORARIO = """
			[{"diaSemana":1,"horaInicio":"09:00","horaFin":"11:00"},
			 {"diaSemana":3,"horaInicio":"09:00","horaFin":"11:00"}]
			""";
	private static final List<String> MIERCOLES_COMPLETO = List.of("09:00", "09:15", "09:30", "09:45", "10:00", "10:15",
			"10:30");

	@Autowired
	private RelojDeTest reloj;
	@Autowired
	private DisponibilidadService disponibilidadService;
	@Autowired
	private BarberiaRepository barberias;
	@Autowired
	private ServicioRepository servicios;

	private Long servicioA;

	@BeforeEach
	void prepararBarberiaA() throws Exception {
		reloj.fijar(AHORA);
		String token = duenoConBarberia("a@mail.com", "Barbería A");
		putCon(token, "/api/v1/horarios", HORARIO).andExpect(status().isOk());
		servicioA = insertarServicio(barberiaIdDeSlug("barberia-a"), "Corte", 30, true);
	}

	@AfterEach
	void relojReal() {
		reloj.usarReal();
	}

	@Test
	void publicoSinTokenDevuelveTodosLosDiasDelRangoConElOffsetDeLaBarberia() throws Exception {
		disponibilidad("barberia-a", servicioA, LUNES, LUNES.plusDays(3))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.zonaHoraria").value("America/Argentina/Buenos_Aires"))
				.andExpect(jsonPath("$.duracionMin").value(30))
				.andExpect(jsonPath("$.dias[*].fecha", contains("2026-11-16", "2026-11-17", "2026-11-18", "2026-11-19")))
				// Lunes = hoy: 09:00 ya es "ahora", no se ofrece.
				.andExpect(jsonPath("$.dias[0].horarios[0]").value("2026-11-16T09:15:00-03:00"))
				.andExpect(jsonPath("$.dias[0].horarios", hasSize(6)))
				.andExpect(jsonPath("$.dias[1].horarios", hasSize(0)))
				.andExpect(jsonPath("$.dias[2].horarios[0]").value("2026-11-18T09:00:00-03:00"))
				.andExpect(jsonPath("$.dias[2].horarios[6]").value("2026-11-18T10:30:00-03:00"))
				.andExpect(jsonPath("$.dias[2].horarios", hasSize(7)))
				.andExpect(jsonPath("$.dias[3].horarios", hasSize(0)));
	}

	@Test
	void laRespuestaNoTraeIdsNiDatosDeMiembrosTurnosOBloqueos() throws Exception {
		insertarTurnoA(MIERCOLES, "10:00", "10:30", "CONFIRMADO");
		insertarBloqueo(miembroIdDeSlug("barberia-a"), MIERCOLES, "09:00", "09:30");

		String json = disponibilidad("barberia-a", servicioA, MIERCOLES, MIERCOLES)
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertThat(JsonPath.<List<String>>read(json, "$..id")).isEmpty();
		assertThat(json).doesNotContain("miembro", "turno", "bloqueo", "cliente", "\"id\"");
	}

	@Test
	void desdeEnElPasadoArrancaHoy() throws Exception {
		disponibilidad("barberia-a", servicioA, LUNES.minusDays(6), LUNES)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.dias[*].fecha", contains("2026-11-16")))
				.andExpect(jsonPath("$.dias[0].horarios[0]").value("2026-11-16T09:15:00-03:00"));

		disponibilidad("barberia-a", servicioA, LUNES.minusDays(10), LUNES.minusDays(1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.dias", hasSize(0)));
	}

	@Test
	void canceladoYAusenteNoOcupanConfirmadoYCompletadoSi() throws Exception {
		insertarTurnoA(MIERCOLES, "09:00", "09:30", "CANCELADO");
		insertarTurnoA(MIERCOLES, "09:30", "10:00", "AUSENTE");
		insertarTurnoA(MIERCOLES, "10:00", "10:30", "CONFIRMADO");
		insertarTurnoA(MIERCOLES, "10:30", "11:00", "COMPLETADO");

		assertThat(horasDe("barberia-a", servicioA, MIERCOLES)).containsExactly("09:00", "09:15", "09:30");
	}

	@Test
	void laPausaDeLaBarberiaSeRespeta() throws Exception {
		jdbc.update("UPDATE barberias SET pausa_entre_turnos_min = 10 WHERE slug = 'barberia-a'");
		insertarTurnoA(MIERCOLES, "10:00", "10:30", "CONFIRMADO");

		assertThat(horasDe("barberia-a", servicioA, MIERCOLES)).containsExactly("09:00", "09:15");
	}

	@Test
	void servicioInactivoDeOtraBarberiaOInexistenteDa404() throws Exception {
		Long inactivo = insertarServicio(barberiaIdDeSlug("barberia-a"), "Barba", 30, false);
		duenoConBarberia("b@mail.com", "Barbería B");
		Long deB = insertarServicio(barberiaIdDeSlug("barberia-b"), "Corte B", 30, true);

		for (Long id : List.of(inactivo, deB, 999_999L)) {
			disponibilidad("barberia-a", id, MIERCOLES, MIERCOLES)
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.detail").value("No encontramos ese servicio"));
		}
	}

	@Test
	void barberiaSuspendidaOInexistenteDa404() throws Exception {
		disponibilidad("no-existe", servicioA, MIERCOLES, MIERCOLES)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("No encontramos esa barbería"));

		jdbc.update("UPDATE barberias SET activa = FALSE WHERE slug = 'barberia-a'");
		disponibilidad("barberia-a", servicioA, MIERCOLES, MIERCOLES)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("No encontramos esa barbería"));
	}

	@Test
	void rangoDeHasta14Dias() throws Exception {
		disponibilidad("barberia-a", servicioA, LUNES, LUNES.plusDays(13))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.dias", hasSize(14)));

		disponibilidad("barberia-a", servicioA, LUNES, LUNES.plusDays(14))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("El rango puede ser de hasta 14 días"))
				.andExpect(jsonPath("$.errores.hasta").value("El rango puede ser de hasta 14 días"));
	}

	@Test
	void hastaAnteriorADesdeDa400() throws Exception {
		disponibilidad("barberia-a", servicioA, MIERCOLES, LUNES)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Datos inválidos"))
				.andExpect(jsonPath("$.errores.hasta").value("La fecha \"hasta\" no puede ser anterior a \"desde\""));
	}

	@Test
	void parametrosFaltantesOMalEscritosDan400() throws Exception {
		String base = URL + "barberia-a/disponibilidad";
		mvc.perform(get(base + "?desde=2026-11-18&hasta=2026-11-18"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.servicioId").value("Falta el parámetro servicioId"));
		mvc.perform(get(base + "?servicioId=" + servicioA + "&hasta=2026-11-18"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.desde").value("Falta el parámetro desde"));
		mvc.perform(get(base + "?servicioId=" + servicioA + "&desde=2026-11-18"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.hasta").value("Falta el parámetro hasta"));
		mvc.perform(get(base + "?servicioId=abc&desde=2026-11-18&hasta=2026-11-18"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.servicioId").value("El parámetro servicioId no es válido"));
		for (String fecha : List.of("18/11/2026", "2026-13-01", "mañana")) {
			mvc.perform(get(base + "?servicioId=" + servicioA + "&desde=" + fecha + "&hasta=2026-11-18"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.title").value("Datos inválidos"))
					.andExpect(jsonPath("$.errores.desde").value("El parámetro desde no es válido"));
		}
	}

	@Test
	void turnosYBloqueosDeOtraBarberiaNoAfectan() throws Exception {
		String tokenB = duenoConBarberia("b@mail.com", "Barbería B");
		putCon(tokenB, "/api/v1/horarios", HORARIO).andExpect(status().isOk());
		Long barberiaB = barberiaIdDeSlug("barberia-b");
		Long miembroB = miembroIdDeSlug("barberia-b");
		Long servicioB = insertarServicio(barberiaB, "Corte", 30, true);
		Long clienteB = insertarCliente(barberiaB);
		insertarTurno(barberiaB, miembroB, clienteB, servicioB, MIERCOLES, "09:00", "10:00", "CONFIRMADO");
		insertarBloqueo(miembroB, MIERCOLES, "10:00", "11:00");

		assertThat(horasDe("barberia-b", servicioB, MIERCOLES)).isEmpty();
		assertThat(horasDe("barberia-a", servicioA, MIERCOLES)).containsExactlyElementsOf(MIERCOLES_COMPLETO);
	}

	@Test
	void estaDisponibleCoincideConElListado() throws Exception {
		insertarTurnoA(MIERCOLES, "10:00", "10:30", "CONFIRMADO");
		List<String> listado = horasDe("barberia-a", servicioA, MIERCOLES);
		assertThat(listado).containsExactly("09:00", "09:15", "09:30", "10:30");

		Barberia barberia = barberias.findBySlugAndActivaTrue("barberia-a").orElseThrow();
		Servicio servicio = servicios.findById(servicioA).orElseThrow();
		Long miembroA = miembroIdDeSlug("barberia-a");

		// Cada 5 minutos de 08:00 a 12:00: disponible si y solo si está en el listado.
		for (LocalTime hora = LocalTime.of(8, 0); hora.isBefore(LocalTime.of(12, 0)); hora = hora.plusMinutes(5)) {
			Optional<MiembroBarberia> miembro = disponibilidadService.estaDisponible(barberia, servicio,
					instante(MIERCOLES, hora.toString()));
			if (listado.contains(hora.toString())) {
				assertThat(miembro).as(hora.toString()).isPresent();
				assertThat(miembro.get().getId()).isEqualTo(miembroA);
			}
			else {
				assertThat(miembro).as(hora.toString()).isEmpty();
			}
		}
		// Hoy a las 09:00 = "ahora": ya no.
		assertThat(disponibilidadService.estaDisponible(barberia, servicio, AHORA)).isEmpty();
		assertThat(disponibilidadService.estaDisponible(barberia, servicio, instante(LUNES, "09:15"))).isPresent();
	}

	// ---------------------------------------------------------------------------------------------------------

	private ResultActions disponibilidad(String slug, Long servicioId, LocalDate desde, LocalDate hasta)
			throws Exception {
		return mvc.perform(get(URL + slug + "/disponibilidad?servicioId=" + servicioId + "&desde=" + desde + "&hasta="
				+ hasta));
	}

	/** Horarios de un solo día como "HH:mm", verificando que salgan con el offset de Buenos Aires. */
	private List<String> horasDe(String slug, Long servicioId, LocalDate dia) throws Exception {
		String json = disponibilidad(slug, servicioId, dia, dia)
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		List<String> horarios = JsonPath.read(json, "$.dias[0].horarios");
		assertThat(horarios).allMatch(h -> h.startsWith(dia + "T") && h.endsWith(":00-03:00"));
		return horarios.stream().map(h -> h.substring(11, 16)).toList();
	}

	private Long insertarServicio(Long barberiaId, String nombre, int duracionMin, boolean activo) {
		return jdbc.queryForObject("""
				INSERT INTO servicios (barberia_id, nombre, duracion_min, precio, activo) VALUES (?, ?, ?, 5000, ?) RETURNING id
				""", Long.class, barberiaId, nombre, duracionMin, activo);
	}

	private Long insertarCliente(Long barberiaId) {
		return jdbc.queryForObject("INSERT INTO clientes (barberia_id, nombre) VALUES (?, 'Cliente') RETURNING id",
				Long.class, barberiaId);
	}

	private void insertarTurnoA(LocalDate dia, String inicio, String fin, String estado) {
		Long barberiaA = barberiaIdDeSlug("barberia-a");
		Long cliente = jdbc.queryForList("SELECT id FROM clientes WHERE barberia_id = ?", Long.class, barberiaA).stream()
				.findFirst().orElseGet(() -> insertarCliente(barberiaA));
		insertarTurno(barberiaA, miembroIdDeSlug("barberia-a"), cliente, servicioA, dia, inicio, fin, estado);
	}

	private void insertarTurno(Long barberiaId, Long miembroId, Long clienteId, Long servicioId, LocalDate dia,
			String inicio, String fin, String estado) {
		boolean cancelado = estado.equals("CANCELADO");
		jdbc.update("""
				INSERT INTO turnos (barberia_id, miembro_id, cliente_id, servicio_id, inicio, fin, estado, precio,
				                    cancelado_en, cancelado_por)
				VALUES (?, ?, ?, ?, ?, ?, ?, 5000, ?, ?)
				""", barberiaId, miembroId, clienteId, servicioId, offset(dia, inicio), offset(dia, fin), estado,
				cancelado ? OffsetDateTime.now(ZoneOffset.UTC) : null, cancelado ? "BARBERO" : null);
	}

	private void insertarBloqueo(Long miembroId, LocalDate dia, String inicio, String fin) {
		jdbc.update("INSERT INTO bloqueos_agenda (miembro_id, inicio, fin) VALUES (?, ?, ?)", miembroId,
				offset(dia, inicio), offset(dia, fin));
	}

	private static OffsetDateTime offset(LocalDate dia, String hora) {
		return OffsetDateTime.of(dia, LocalTime.parse(hora), ARG);
	}

	private static Instant instante(LocalDate dia, String hora) {
		return offset(dia, hora).toInstant();
	}

}
