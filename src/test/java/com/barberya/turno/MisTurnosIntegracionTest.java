package com.barberya.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.jayway.jsonpath.JsonPath;

/** Reloj en lun 16/11/2026 09:00 ART. Ana tiene turnos en las barberías A y B, en estados mezclados. */
class MisTurnosIntegracionTest extends TurnoIntegracionBase {

	private static final LocalDate JUEVES = LUNES.plusDays(3);

	private Long servicioB;
	private Long ana;
	private String tokenAna;

	@BeforeEach
	void prepararBarberiaB() throws Exception {
		duenoConBarberia("b@mail.com", "Barbería B");
		servicioB = insertarServicio(barberiaIdDeSlug("barberia-b"), "Barba", 30, "3000.00", true);
		jdbc.update("UPDATE barberias SET direccion = 'Av. Corrientes 1234' WHERE id = ?", barberiaA);
		ana = clienteConPerfil("ana@mail.com");
		tokenAna = tokenDe(ana);
	}

	// --- Listado ---

	@Test
	void proximosSonLosConfirmadosFuturosDeTodasLasBarberiasEnOrdenAscendente() throws Exception {
		Long miercolesA = insertarTurno("barberia-a", ana, servicioA, MIERCOLES, "10:00", "CONFIRMADO");
		Long lunesB = insertarTurno("barberia-b", ana, servicioB, LUNES, "11:00", "CONFIRMADO");
		Long martesA = insertarTurno("barberia-a", ana, servicioA, MARTES, "10:00", "CONFIRMADO");
		cargarPasados();

		String json = getCon(tokenAna, "/api/v1/mis-turnos")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(3))
				.andExpect(jsonPath("$.contenido[0].barberia.nombre").value("Barbería B"))
				.andExpect(jsonPath("$.contenido[0].barberia.slug").value("barberia-b"))
				.andExpect(jsonPath("$.contenido[0].barberia.telefono").value("1122334455"))
				.andExpect(jsonPath("$.contenido[0].servicioId").value(servicioB))
				.andExpect(jsonPath("$.contenido[0].servicio.nombre").value("Barba"))
				.andExpect(jsonPath("$.contenido[0].inicio").value("2026-11-16T11:00:00-03:00"))
				.andExpect(jsonPath("$.contenido[0].fin").value("2026-11-16T11:30:00-03:00"))
				.andExpect(jsonPath("$.contenido[0].estado").value("CONFIRMADO"))
				.andExpect(jsonPath("$.contenido[0].precio").value(5000.00))
				.andExpect(jsonPath("$.contenido[0].cancelableHasta").value("2026-11-15T11:00:00-03:00"))
				.andExpect(jsonPath("$.contenido[1].barberia.direccion").value("Av. Corrientes 1234"))
				.andExpect(jsonPath("$.contenido[1].servicioId").value(servicioA))
				.andExpect(jsonPath("$.contenido[0].miembroId").doesNotExist())
				.andExpect(jsonPath("$.contenido[0].clienteId").doesNotExist())
				.andExpect(jsonPath("$.contenido[0].miembro").doesNotExist())
				.andExpect(jsonPath("$.contenido[0].cliente").doesNotExist())
				.andReturn().getResponse().getContentAsString();

		assertThat(ids(json)).containsExactly(lunesB, martesA, miercolesA);
		// El explícito da lo mismo que el default.
		String explicito = getCon(tokenAna, "/api/v1/mis-turnos?alcance=proximos").andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertThat(ids(explicito)).containsExactly(lunesB, martesA, miercolesA);
	}

	@Test
	void pasadosSonElRestoEnOrdenDescendenteYPaginados() throws Exception {
		insertarTurno("barberia-a", ana, servicioA, MIERCOLES, "10:00", "CONFIRMADO");
		List<Long> pasados = cargarPasados();

		String json = getCon(tokenAna, "/api/v1/mis-turnos?alcance=pasados").andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(4))
				.andReturn().getResponse().getContentAsString();
		assertThat(ids(json)).containsExactlyElementsOf(pasados);

		String pagina0 = getCon(tokenAna, "/api/v1/mis-turnos?alcance=pasados&page=0&size=2")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pagina").value(0))
				.andExpect(jsonPath("$.tamanio").value(2))
				.andExpect(jsonPath("$.totalElementos").value(4))
				.andExpect(jsonPath("$.totalPaginas").value(2))
				.andExpect(jsonPath("$.contenido[0].estado").value("CANCELADO"))
				.andReturn().getResponse().getContentAsString();
		assertThat(ids(pagina0)).containsExactlyElementsOf(pasados.subList(0, 2));

		String pagina1 = getCon(tokenAna, "/api/v1/mis-turnos?alcance=pasados&page=1&size=2")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pagina").value(1))
				.andExpect(jsonPath("$.contenido[1].estado").value("AUSENTE"))
				.andReturn().getResponse().getContentAsString();
		assertThat(ids(pagina1)).containsExactlyElementsOf(pasados.subList(2, 4));

		getCon(tokenAna, "/api/v1/mis-turnos?alcance=pasados&page=2&size=2")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.contenido").isEmpty());
	}

	@Test
	void sinTurnosDaUnaPaginaVacia() throws Exception {
		getCon(tokenAna, "/api/v1/mis-turnos")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.contenido").isEmpty())
				.andExpect(jsonPath("$.totalElementos").value(0))
				.andExpect(jsonPath("$.totalPaginas").value(0));
	}

	@Test
	void parametrosInvalidosDan400() throws Exception {
		getCon(tokenAna, "/api/v1/mis-turnos?alcance=otro")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.alcance").value("El parámetro alcance no es válido"));
		getCon(tokenAna, "/api/v1/mis-turnos?alcance=pasados&size=51")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.size").exists());
		getCon(tokenAna, "/api/v1/mis-turnos?size=0")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.size").exists());
		getCon(tokenAna, "/api/v1/mis-turnos?page=-1")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.page").exists());
		getCon(tokenAna, "/api/v1/mis-turnos?size=50").andExpect(status().isOk());
	}

	// --- puedeCancelar ---

	@Test
	void puedeCancelarSegunElPlazo() throws Exception {
		Long turno = insertarTurno("barberia-a", ana, servicioA, MIERCOLES, "10:00", "CONFIRMADO");

		getCon(tokenAna, "/api/v1/mis-turnos")
				.andExpect(jsonPath("$.contenido[0].id").value(turno))
				.andExpect(jsonPath("$.contenido[0].puedeCancelar").value(true));

		// Igual que el endpoint de cancelar: justo 24 h antes ya no se puede.
		reloj.fijar(instante(MARTES, "10:00").minus(Duration.ofMinutes(1)));
		getCon(tokenAna, "/api/v1/mis-turnos").andExpect(jsonPath("$.contenido[0].puedeCancelar").value(true));
		reloj.fijar(instante(MARTES, "10:00"));
		getCon(tokenAna, "/api/v1/mis-turnos").andExpect(jsonPath("$.contenido[0].puedeCancelar").value(false));
		postCon(tokenAna, "/api/v1/mis-turnos/" + turno + "/cancelar").andExpect(status().isConflict());
	}

	@Test
	void unTurnoNoConfirmadoNoSePuedeCancelar() throws Exception {
		Long cancelado = insertarTurno("barberia-a", ana, servicioA, JUEVES, "10:00", "CANCELADO");
		Long completado = insertarTurno("barberia-a", ana, servicioA, LUNES.minusDays(3), "10:00", "COMPLETADO");

		String json = getCon(tokenAna, "/api/v1/mis-turnos?alcance=pasados").andExpect(status().isOk())
				.andExpect(jsonPath("$.contenido[0].id").value(cancelado))
				.andExpect(jsonPath("$.contenido[0].puedeCancelar").value(false))
				.andExpect(jsonPath("$.contenido[1].id").value(completado))
				.andExpect(jsonPath("$.contenido[1].puedeCancelar").value(false))
				.andReturn().getResponse().getContentAsString();
		assertThat(ids(json)).hasSize(2);
	}

	// --- Aislamiento ---

	@Test
	void otroUsuarioNoVeMisTurnos() throws Exception {
		insertarTurno("barberia-a", ana, servicioA, MIERCOLES, "10:00", "CONFIRMADO");
		cargarPasados();
		Long beto = clienteConPerfil("beto@mail.com");
		Long turnoBeto = insertarTurno("barberia-a", beto, servicioA, MIERCOLES, "11:00", "CONFIRMADO");

		String json = getCon(tokenDe(beto), "/api/v1/mis-turnos").andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertThat(ids(json)).containsExactly(turnoBeto);
		getCon(tokenDe(beto), "/api/v1/mis-turnos?alcance=pasados")
				.andExpect(jsonPath("$.contenido").isEmpty());

		String deAna = getCon(tokenAna, "/api/v1/mis-turnos").andReturn().getResponse().getContentAsString();
		assertThat(ids(deAna)).doesNotContain(turnoBeto);

		// El dueño de A tiene turnos en su barbería, pero ninguna ficha de cliente propia.
		for (String alcance : List.of("proximos", "pasados")) {
			getCon(tokenA, "/api/v1/mis-turnos?alcance=" + alcance)
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.contenido").isEmpty());
		}
	}

	@Test
	void elCalendarioDeUnTurnoAjenoDa404() throws Exception {
		Long turno = insertarTurno("barberia-a", ana, servicioA, MIERCOLES, "10:00", "CONFIRMADO");

		for (String token : List.of(tokenDe(clienteConPerfil("beto@mail.com")), tokenA)) {
			getCon(token, "/api/v1/mis-turnos/" + turno + "/calendario.ics")
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.detail").value("No encontramos ese turno"));
		}
		getCon(tokenAna, "/api/v1/mis-turnos/999999/calendario.ics").andExpect(status().isNotFound());
	}

	@Test
	void sinTokenDa401() throws Exception {
		mvc.perform(get("/api/v1/mis-turnos")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/mis-turnos/1/calendario.ics")).andExpect(status().isUnauthorized());
	}

	// --- Calendario ---

	@Test
	void elCalendarioEsUnIcsConElTurnoEnUtc() throws Exception {
		jdbc.update("UPDATE barberias SET nombre = 'Lo de Juan, cortes; barba', direccion = 'Av. Corrientes 1234, CABA'"
				+ " WHERE id = ?", barberiaA);
		Long turno = insertarTurno("barberia-a", ana, servicioA, MIERCOLES, "10:00", "CONFIRMADO");

		String ics = getCon(tokenAna, "/api/v1/mis-turnos/" + turno + "/calendario.ics")
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("text/calendar"))
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
						"attachment; filename=\"turno-" + turno + ".ics\""))
				.andReturn().getResponse().getContentAsString();

		assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n");
		assertThat(ics.replace("\r\n", "")).doesNotContain("\n").doesNotContain("\r");
		assertThat(ics.split("\r\n")).contains(
				"VERSION:2.0",
				"BEGIN:VEVENT",
				"UID:turno-" + turno + "@barberya.app",
				"DTSTAMP:20261116T120000Z",
				"DTSTART:20261118T130000Z",
				"DTEND:20261118T133000Z",
				"STATUS:CONFIRMED",
				"SUMMARY:Corte en Lo de Juan\\, cortes\\; barba",
				"LOCATION:Av. Corrientes 1234\\, CABA",
				"DESCRIPTION:Servicio: Corte\\nTeléfono de la barbería: 1122334455",
				"BEGIN:VALARM",
				"ACTION:DISPLAY",
				"TRIGGER:-PT2H",
				"END:VALARM",
				"END:VEVENT");
	}

	@Test
	void elCalendarioDeUnTurnoCanceladoLoMarcaCancelado() throws Exception {
		Long turno = insertarTurno("barberia-b", ana, servicioB, MIERCOLES, "10:00", "CANCELADO");

		String ics = getCon(tokenAna, "/api/v1/mis-turnos/" + turno + "/calendario.ics")
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertThat(ics.split("\r\n")).contains("STATUS:CANCELLED", "SUMMARY:Barba en Barbería B")
				.doesNotContain("STATUS:CONFIRMED")
				// B no tiene dirección cargada: sin LOCATION.
				.noneMatch(l -> l.startsWith("LOCATION:"));
	}

	// ---------------------------------------------------------------------------------------------------------

	/**
	 * Turnos de Ana que van en "pasados", en el orden esperado (descendente): un CANCELADO futuro, un CONFIRMADO que ya
	 * empezó, un COMPLETADO y un AUSENTE (este en B).
	 */
	private List<Long> cargarPasados() {
		Long canceladoFuturo = insertarTurno("barberia-a", ana, servicioA, JUEVES, "10:00", "CANCELADO");
		Long confirmadoPasado = insertarTurno("barberia-a", ana, servicioA, LUNES, "08:00", "CONFIRMADO");
		Long completado = insertarTurno("barberia-a", ana, servicioA, LUNES.minusDays(3), "10:00", "COMPLETADO");
		Long ausente = insertarTurno("barberia-b", ana, servicioB, LUNES.minusDays(4), "10:00", "AUSENTE");
		return List.of(canceladoFuturo, confirmadoPasado, completado, ausente);
	}

	/** Inserta por JDBC (sin las reglas de reserva) un turno de 30 min; crea la ficha del usuario si falta. */
	private Long insertarTurno(String slug, Long usuarioId, Long servicioId, LocalDate dia, String hora, String estado) {
		Long barberiaId = barberiaIdDeSlug(slug);
		jdbc.update("""
				INSERT INTO clientes (barberia_id, usuario_id, nombre) VALUES (?, ?, 'Cliente')
				ON CONFLICT (barberia_id, usuario_id) DO NOTHING
				""", barberiaId, usuarioId);
		Long ficha = contar("SELECT id FROM clientes WHERE barberia_id = ? AND usuario_id = ?", barberiaId, usuarioId);
		Instant inicio = instante(dia, hora);
		boolean cancelado = estado.equals("CANCELADO");
		return jdbc.queryForObject("""
				INSERT INTO turnos (barberia_id, miembro_id, cliente_id, servicio_id, inicio, fin, estado, precio,
				                    cancelado_en, cancelado_por)
				VALUES (?, ?, ?, ?, ?, ?, ?, 5000.00, ?, ?) RETURNING id
				""", Long.class, barberiaId, miembroIdDeSlug(slug), ficha, servicioId, utc(inicio),
				utc(inicio.plus(Duration.ofMinutes(30))), estado, cancelado ? utc(AHORA) : null,
				cancelado ? "CLIENTE" : null);
	}

	private static OffsetDateTime utc(Instant instante) {
		return instante.atOffset(ZoneOffset.UTC);
	}

	private static List<Long> ids(String json) {
		List<Number> ids = JsonPath.read(json, "$.contenido[*].id");
		return ids.stream().map(Number::longValue).toList();
	}

}
