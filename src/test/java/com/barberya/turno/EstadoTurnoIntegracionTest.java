package com.barberya.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

/** Completar, ausente y revertir. El reloj está en el lunes a las 09:00: los turnos de las 09:00 ya empezaron. */
class EstadoTurnoIntegracionTest extends TurnoIntegracionBase {

	private Long clienteA;

	@BeforeEach
	void ficha() {
		clienteA = insertarCliente(barberiaA, "Juan Pérez", "1144445555");
	}

	// --- Completar ---

	@Test
	void completarCreaElCobroConElPrecioDelTurno() throws Exception {
		Long turno = turnoDeLas9("CONFIRMADO");
		jdbc.update("UPDATE servicios SET precio = 7000 WHERE id = ?", servicioA);

		postCon(tokenA, url(turno, "completar"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(turno))
				.andExpect(jsonPath("$.estado").value("COMPLETADO"))
				.andExpect(jsonPath("$.precio").value(5000.0))
				.andExpect(jsonPath("$.inicio").value("2026-11-16T09:00:00-03:00"))
				.andExpect(jsonPath("$.servicio.nombre").value("Corte"))
				.andExpect(jsonPath("$.cliente.telefono").value("1144445555"));

		assertThat(estado(turno)).isEqualTo("COMPLETADO");
		Map<String, Object> cobro = jdbc.queryForMap("SELECT barberia_id, monto, metodo FROM cobros WHERE turno_id = ?",
				turno);
		assertThat(cobro.get("barberia_id")).isEqualTo(barberiaA);
		assertThat((BigDecimal) cobro.get("monto")).isEqualByComparingTo("5000");
		assertThat(cobro.get("metodo")).isNull();
		assertThat(jdbc.queryForObject("SELECT cobrado_en FROM cobros WHERE turno_id = ?", OffsetDateTime.class, turno)
				.toInstant()).isEqualTo(AHORA);
	}

	@Test
	void completarAntesDeQueEmpieceDa409() throws Exception {
		Long turno = insertarTurno(barberiaA, clienteA, servicioA, LUNES, "09:30", "10:00", "CONFIRMADO", "5000");

		postCon(tokenA, url(turno, "completar"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("TODAVIA_NO_EMPEZO"));
		assertThat(estado(turno)).isEqualTo("CONFIRMADO");
		assertThat(cobros(turno)).isZero();
	}

	@Test
	void completarDosVecesDa409() throws Exception {
		Long turno = turnoDeLas9("CONFIRMADO");
		postCon(tokenA, url(turno, "completar")).andExpect(status().isOk());

		postCon(tokenA, url(turno, "completar"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));
		assertThat(cobros(turno)).isEqualTo(1);
	}

	@Test
	void completarUnCanceladoDa409() throws Exception {
		Long turno = turnoDeLas9("CANCELADO");

		postCon(tokenA, url(turno, "completar"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));
		assertThat(cobros(turno)).isZero();
	}

	// --- Ausente ---

	@Test
	void ausenteLiberaElHorario() throws Exception {
		// De 09:00 a 11:00: ya empezó, y tapa los inicios de las 10:00 y 10:30, que todavía son reservables.
		Long largo = insertarServicio(barberiaA, "Color", 120, "15000.00", true);
		Long turno = insertarTurno(barberiaA, clienteA, largo, LUNES, "09:00", "11:00", "CONFIRMADO", "15000");
		assertThat(horasDelLunes()).doesNotContain("10:00", "10:30").contains("11:00");

		postCon(tokenA, url(turno, "ausente"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("AUSENTE"));

		assertThat(estado(turno)).isEqualTo("AUSENTE");
		assertThat(horasDelLunes()).contains("10:00", "10:30", "11:00");
	}

	@Test
	void ausenteAntesDeQueEmpieceOSinEstarConfirmadoDa409() throws Exception {
		Long futuro = insertarTurno(barberiaA, clienteA, servicioA, LUNES, "10:00", "10:30", "CONFIRMADO", "5000");
		postCon(tokenA, url(futuro, "ausente"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("TODAVIA_NO_EMPEZO"));

		Long completado = turnoDeLas9("COMPLETADO");
		postCon(tokenA, url(completado, "ausente"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));
		assertThat(estado(completado)).isEqualTo("COMPLETADO");
	}

	// --- Revertir ---

	@Test
	void revertirUnCompletadoBorraSuCobroYLoDejaConfirmado() throws Exception {
		Long turno = turnoDeLas9("CONFIRMADO");
		postCon(tokenA, url(turno, "completar")).andExpect(status().isOk());
		assertThat(cobros(turno)).isEqualTo(1);

		postCon(tokenA, url(turno, "revertir"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("CONFIRMADO"));

		// En la base, después del commit: el cambio de estado no se perdió al borrar el cobro.
		assertThat(estado(turno)).isEqualTo("CONFIRMADO");
		assertThat(cobros(turno)).isZero();
		assertThat(contar("SELECT count(*) FROM cobros")).isZero();
	}

	@Test
	void revertirUnAusenteLoDejaConfirmado() throws Exception {
		Long turno = turnoDeLas9("CONFIRMADO");
		postCon(tokenA, url(turno, "ausente")).andExpect(status().isOk());

		postCon(tokenA, url(turno, "revertir"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("CONFIRMADO"));
		assertThat(estado(turno)).isEqualTo("CONFIRMADO");
	}

	@Test
	void revertirUnAusenteConElHorarioOcupadoDa409() throws Exception {
		Long turno = turnoDeLas9("CONFIRMADO");
		postCon(tokenA, url(turno, "ausente")).andExpect(status().isOk());
		// Mientras tanto se cargó otro turno que se pisa con el del ausente.
		insertarTurno(barberiaA, clienteA, servicioA, LUNES, "09:15", "09:45", "CONFIRMADO", "5000");

		postCon(tokenA, url(turno, "revertir"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("HORARIO_OCUPADO"))
				.andExpect(jsonPath("$.detail").value("Ese horario ya lo ocupa otro turno"));
		assertThat(estado(turno)).isEqualTo("AUSENTE");
	}

	@Test
	void revertirUnAusenteConUnTurnoContiguoSePuede() throws Exception {
		Long turno = turnoDeLas9("CONFIRMADO");
		postCon(tokenA, url(turno, "ausente")).andExpect(status().isOk());
		insertarTurno(barberiaA, clienteA, servicioA, LUNES, "09:30", "10:00", "CONFIRMADO", "5000");

		postCon(tokenA, url(turno, "revertir")).andExpect(status().isOk());
		assertThat(estado(turno)).isEqualTo("CONFIRMADO");
	}

	@Test
	void revertirUnConfirmadoOUnCanceladoDa409() throws Exception {
		Long confirmado = turnoDeLas9("CONFIRMADO");
		postCon(tokenA, url(confirmado, "revertir"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));

		Long cancelado = insertarTurno(barberiaA, clienteA, servicioA, MIERCOLES, "09:00", "09:30", "CANCELADO", "5000");
		postCon(tokenA, url(cancelado, "revertir"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));
		assertThat(estado(cancelado)).isEqualTo("CANCELADO");
	}

	// --- Aislamiento ---

	@Test
	void elDuenoDeOtraBarberiaNoCambiaLosTurnosDeA() throws Exception {
		Long confirmado = turnoDeLas9("CONFIRMADO");
		Long completado = insertarTurno(barberiaA, clienteA, servicioA, LUNES, "09:30", "10:00", "COMPLETADO", "5000");
		String tokenB = duenoConBarberia("b@mail.com", "Barbería B");

		for (String accion : List.of("completar", "ausente")) {
			postCon(tokenB, url(confirmado, accion))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.detail").value("No encontramos ese turno"));
		}
		postCon(tokenB, url(completado, "revertir")).andExpect(status().isNotFound());

		assertThat(estado(confirmado)).isEqualTo("CONFIRMADO");
		assertThat(estado(completado)).isEqualTo("COMPLETADO");
		assertThat(contar("SELECT count(*) FROM cobros")).isZero();
	}

	@Test
	void unClienteSinRolDa403() throws Exception {
		Long turno = turnoDeLas9("CONFIRMADO");
		String token = tokenDe(clienteConPerfil("ana@mail.com"));

		for (String accion : List.of("completar", "ausente", "revertir")) {
			postCon(token, url(turno, accion)).andExpect(status().isForbidden());
		}
		assertThat(estado(turno)).isEqualTo("CONFIRMADO");
	}

	@Test
	void sinTokenDa401() throws Exception {
		Long turno = turnoDeLas9("CONFIRMADO");

		for (String accion : List.of("completar", "ausente", "revertir")) {
			mvc.perform(post(url(turno, accion))).andExpect(status().isUnauthorized());
		}
		assertThat(estado(turno)).isEqualTo("CONFIRMADO");
	}

	// ---------------------------------------------------------------------------------------------------------

	private Long turnoDeLas9(String estado) {
		return insertarTurno(barberiaA, clienteA, servicioA, LUNES, "09:00", "09:30", estado, "5000");
	}

	private static String url(Long turno, String accion) {
		return "/api/v1/turnos/" + turno + "/" + accion;
	}

	private String estado(Long turno) {
		return jdbc.queryForObject("SELECT estado FROM turnos WHERE id = ?", String.class, turno);
	}

	private Long cobros(Long turno) {
		return contar("SELECT count(*) FROM cobros WHERE turno_id = ?", turno);
	}

	private List<String> horasDelLunes() throws Exception {
		String json = mvc.perform(get("/api/v1/public/barberias/barberia-a/disponibilidad?servicioId=" + servicioA
				+ "&desde=" + LUNES + "&hasta=" + LUNES))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		List<String> horarios = JsonPath.read(json, "$.dias[0].horarios");
		return horarios.stream().map(h -> h.substring(11, 16)).toList();
	}

}
