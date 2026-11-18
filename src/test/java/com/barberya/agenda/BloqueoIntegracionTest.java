package com.barberya.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.barberya.ApiIntegracionTest;
import com.jayway.jsonpath.JsonPath;

class BloqueoIntegracionTest extends ApiIntegracionTest {

	private static final String URL = "/api/v1/bloqueos";
	private static final ZoneOffset ARG = ZoneOffset.ofHours(-3);

	/** Un día futuro fijo para cada test, así no depende de la hora en que corre. */
	private final LocalDate dia = LocalDate.now(ZoneOffset.UTC).plusDays(10);

	@Test
	void crearConOffsetGuardaEnUtc() throws Exception {
		String token = duenoConBarberia("a@mail.com", "Barbería A");

		postCon(token, URL, """
				{"inicio":"%s","fin":"%s","motivo":"  Turno médico  "}
				""".formatted(hora(10, 0), hora(12, 0)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.inicio").value(dia + "T13:00:00Z"))
				.andExpect(jsonPath("$.fin").value(dia + "T15:00:00Z"))
				.andExpect(jsonPath("$.motivo").value("Turno médico"))
				.andExpect(jsonPath("$.turnosAfectados").value(0));

		assertThat(jdbc.queryForObject("SELECT inicio FROM bloqueos_agenda", OffsetDateTime.class).toInstant())
				.isEqualTo(Instant.parse(dia + "T13:00:00Z"));
		assertThat(jdbc.queryForObject("SELECT miembro_id FROM bloqueos_agenda", Long.class))
				.isEqualTo(miembroIdDeSlug("barberia-a"));
	}

	@Test
	void listarDevuelveLosQueSePisanConElRangoOrdenados() throws Exception {
		String token = duenoConBarberia("a@mail.com", "Barbería A");
		Long dentro = crear(token, hora(10, 0), hora(12, 0));
		Long antesYPisa = crear(token, hora(6, 0), hora(9, 0));
		Long superpuesto = crear(token, hora(11, 0), hora(13, 0)); // superpuestos entre sí: se permite
		crear(token, hora(5, 0), hora(8, 0)); // termina justo cuando empieza el rango: no entra
		crear(token, hora(20, 0), hora(21, 0)); // después del rango

		getCon(token, URL + "?desde=" + hora(8, 0) + "&hasta=" + hora(14, 0))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", contains(antesYPisa.intValue(), dentro.intValue(), superpuesto.intValue())))
				.andExpect(jsonPath("$[0].turnosAfectados").doesNotExist());
	}

	@Test
	void turnosAfectadosCuentaSoloLosConfirmadosQueSePisan() throws Exception {
		String token = duenoConBarberia("a@mail.com", "Barbería A");
		Long barberiaId = barberiaIdDeSlug("barberia-a");
		Long miembroId = miembroIdDeSlug("barberia-a");
		Long clienteId = jdbc.queryForObject("INSERT INTO clientes (barberia_id, nombre) VALUES (?, 'Cliente') RETURNING id",
				Long.class, barberiaId);
		Long servicioId = jdbc.queryForObject(
				"INSERT INTO servicios (barberia_id, nombre, duracion_min, precio) VALUES (?, 'Corte', 30, 5000) RETURNING id",
				Long.class, barberiaId);
		insertarTurno(barberiaId, miembroId, clienteId, servicioId, hora(11, 0), hora(11, 30), "CONFIRMADO"); // se pisa
		insertarTurno(barberiaId, miembroId, clienteId, servicioId, hora(12, 0), hora(12, 30), "CONFIRMADO"); // contiguo
		insertarTurno(barberiaId, miembroId, clienteId, servicioId, hora(9, 30), hora(10, 0), "CONFIRMADO"); // contiguo
		insertarTurno(barberiaId, miembroId, clienteId, servicioId, hora(10, 0), hora(10, 30), "CANCELADO"); // cancelado

		postCon(token, URL, "{\"inicio\":\"%s\",\"fin\":\"%s\"}".formatted(hora(10, 0), hora(12, 0)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.turnosAfectados").value(1))
				.andExpect(jsonPath("$.motivo").isEmpty());

		// No los cancela.
		assertThat(jdbc.queryForObject("SELECT count(*) FROM turnos WHERE estado = 'CONFIRMADO'", Integer.class)).isEqualTo(3);
	}

	@Test
	void validacionesDeCrear() throws Exception {
		String token = duenoConBarberia("a@mail.com", "Barbería A");
		OffsetDateTime ahora = OffsetDateTime.now(ARG);

		esperar400(token, hora(12, 0), hora(12, 0), "fin");
		esperar400(token, hora(12, 0), hora(10, 0), "fin");
		esperar400(token, ahora.minusDays(2).toString(), ahora.minusDays(1).toString(), "fin");
		esperar400(token, hora(10, 0), dia.plusDays(91).atTime(10, 0).atOffset(ARG).toString(), "fin");
		esperar400(token, ahora.plusDays(370).toString(), ahora.plusDays(371).toString(), "inicio");
		postCon(token, URL, "{\"inicio\":\"%s\",\"fin\":\"%s\",\"motivo\":\"%s\"}".formatted(hora(10, 0), hora(12, 0), "x".repeat(121)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.motivo").exists());
		postCon(token, URL, "{\"fin\":\"%s\"}".formatted(hora(12, 0)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.inicio").exists());
		postCon(token, URL, "{\"inicio\":\"mañana\",\"fin\":\"%s\"}".formatted(hora(12, 0)))
				.andExpect(status().isBadRequest());

		// Exactamente 90 días y uno que empezó en el pasado pero sigue vigente, valen.
		postCon(token, URL, "{\"inicio\":\"%s\",\"fin\":\"%s\"}".formatted(hora(10, 0),
				dia.plusDays(90).atTime(10, 0).atOffset(ARG))).andExpect(status().isCreated());
		postCon(token, URL, "{\"inicio\":\"%s\",\"fin\":\"%s\"}".formatted(ahora.minusHours(1), ahora.plusHours(1)))
				.andExpect(status().isCreated());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM bloqueos_agenda", Integer.class)).isEqualTo(2);
	}

	@Test
	void validacionesDeListar() throws Exception {
		String token = duenoConBarberia("a@mail.com", "Barbería A");
		String desde = dia.atStartOfDay().atOffset(ARG).toString();

		getCon(token, URL + "?desde=" + desde + "&hasta=" + dia.plusDays(93).atStartOfDay().atOffset(ARG))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.hasta").value("El rango puede ser de hasta 92 días"));
		getCon(token, URL + "?desde=" + desde + "&hasta=" + desde)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.hasta").exists());
		getCon(token, URL + "?hasta=" + desde)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Falta el parámetro desde"));
		getCon(token, URL + "?desde=ayer&hasta=" + desde)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.desde").exists());
		getCon(token, URL + "?desde=" + desde + "&hasta=" + dia.plusDays(92).atStartOfDay().atOffset(ARG))
				.andExpect(status().isOk());
	}

	@Test
	void borrarElPropioLoEliminaDeLaBase() throws Exception {
		String token = duenoConBarberia("a@mail.com", "Barbería A");
		Long id = crear(token, hora(10, 0), hora(12, 0));

		deleteCon(token, URL + "/" + id).andExpect(status().isNoContent());

		assertThat(jdbc.queryForObject("SELECT count(*) FROM bloqueos_agenda", Integer.class)).isZero();
		deleteCon(token, URL + "/" + id).andExpect(status().isNotFound());
	}

	@Test
	void aNoVeNiBorraLosBloqueosDeB() throws Exception {
		String tokenB = duenoConBarberia("b@mail.com", "Barbería B");
		Long deB = crear(tokenB, hora(10, 0), hora(12, 0));
		String tokenA = duenoConBarberia("a@mail.com", "Barbería A");
		Long deA = crear(tokenA, hora(15, 0), hora(16, 0));

		getCon(tokenA, URL + "?desde=" + hora(0, 0) + "&hasta=" + hora(23, 0))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(deA));
		deleteCon(tokenA, URL + "/" + deB)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("No encontramos ese bloqueo"));

		assertThat(jdbc.queryForObject("SELECT miembro_id FROM bloqueos_agenda WHERE id = ?", Long.class, deB))
				.isEqualTo(miembroIdDeSlug("barberia-b"));
	}

	@Test
	void crearIgnoraUnMiembroIdEnElBody() throws Exception {
		duenoConBarberia("b@mail.com", "Barbería B");
		Long miembroB = miembroIdDeSlug("barberia-b");
		String tokenA = duenoConBarberia("a@mail.com", "Barbería A");

		postCon(tokenA, URL, "{\"miembroId\":%d,\"inicio\":\"%s\",\"fin\":\"%s\"}".formatted(miembroB, hora(10, 0), hora(12, 0)))
				.andExpect(status().isCreated());

		assertThat(jdbc.queryForObject("SELECT miembro_id FROM bloqueos_agenda", Long.class))
				.isEqualTo(miembroIdDeSlug("barberia-a"));
	}

	private String hora(int h, int m) {
		return dia.atTime(h, m).atOffset(ARG).toString();
	}

	private Long crear(String token, String inicio, String fin) throws Exception {
		String respuesta = postCon(token, URL, "{\"inicio\":\"%s\",\"fin\":\"%s\"}".formatted(inicio, fin))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
	}

	private void esperar400(String token, String inicio, String fin, String campo) throws Exception {
		postCon(token, URL, "{\"inicio\":\"%s\",\"fin\":\"%s\"}".formatted(inicio, fin))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores." + campo).exists());
	}

	private void insertarTurno(Long barberiaId, Long miembroId, Long clienteId, Long servicioId, String inicio, String fin,
			String estado) {
		boolean cancelado = estado.equals("CANCELADO");
		jdbc.update("""
				INSERT INTO turnos (barberia_id, miembro_id, cliente_id, servicio_id, inicio, fin, estado, precio,
				                    cancelado_en, cancelado_por)
				VALUES (?, ?, ?, ?, ?, ?, ?, 5000, ?, ?)
				""", barberiaId, miembroId, clienteId, servicioId, OffsetDateTime.parse(inicio), OffsetDateTime.parse(fin),
				estado, cancelado ? OffsetDateTime.now(ZoneOffset.UTC) : null, cancelado ? "BARBERO" : null);
	}

}
