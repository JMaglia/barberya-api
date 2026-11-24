package com.barberya.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

class CancelacionTurnoIntegracionTest extends TurnoIntegracionBase {

	// --- Cliente ---

	@Test
	void elClienteCancelaDentroDelPlazo() throws Exception {
		String token = tokenDe(clienteConPerfil("ana@mail.com"));
		Long turno = reservarOk(token, MIERCOLES, "10:00");

		postCon(token, "/api/v1/mis-turnos/" + turno + "/cancelar")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(turno))
				.andExpect(jsonPath("$.estado").value("CANCELADO"))
				.andExpect(jsonPath("$.inicio").value("2026-11-18T10:00:00-03:00"));

		Map<String, Object> fila = jdbc.queryForMap("SELECT estado, cancelado_por, cancelado_en FROM turnos WHERE id = ?",
				turno);
		assertThat(fila.get("estado")).isEqualTo("CANCELADO");
		assertThat(fila.get("cancelado_por")).isEqualTo("CLIENTE");
		assertThat(jdbc.queryForObject("SELECT cancelado_en FROM turnos WHERE id = ?", OffsetDateTime.class, turno)
				.toInstant()).isEqualTo(AHORA);
	}

	@Test
	void fueraDelPlazoDa409() throws Exception {
		String token = tokenDe(clienteConPerfil("ana@mail.com"));
		Long turno = reservarOk(token, MIERCOLES, "10:00");

		// Justo 24 h antes ya no se puede.
		reloj.fijar(instante(MARTES, "10:00"));
		postCon(token, "/api/v1/mis-turnos/" + turno + "/cancelar")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("FUERA_DE_PLAZO"))
				.andExpect(jsonPath("$.detail").value("Solo se puede cancelar hasta 24 horas antes"));

		reloj.fijar(instante(MARTES, "10:00").minus(Duration.ofMinutes(1)));
		postCon(token, "/api/v1/mis-turnos/" + turno + "/cancelar").andExpect(status().isOk());
	}

	@Test
	void cancelarDosVecesDa409() throws Exception {
		String token = tokenDe(clienteConPerfil("ana@mail.com"));
		Long turno = reservarOk(token, MIERCOLES, "10:00");
		postCon(token, "/api/v1/mis-turnos/" + turno + "/cancelar").andExpect(status().isOk());

		postCon(token, "/api/v1/mis-turnos/" + turno + "/cancelar")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("NO_CANCELABLE"))
				.andExpect(jsonPath("$.detail").value("Este turno ya no se puede cancelar"));
	}

	@Test
	void elTurnoDeOtroClienteDa404() throws Exception {
		Long turno = reservarOk(tokenDe(clienteConPerfil("ana@mail.com")), MIERCOLES, "10:00");

		for (String token : List.of(tokenDe(clienteConPerfil("beto@mail.com")), tokenA)) {
			postCon(token, "/api/v1/mis-turnos/" + turno + "/cancelar")
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.detail").value("No encontramos ese turno"));
		}
		postCon(tokenA, "/api/v1/mis-turnos/999999/cancelar").andExpect(status().isNotFound());
		assertThat(jdbc.queryForObject("SELECT estado FROM turnos WHERE id = ?", String.class, turno))
				.isEqualTo("CONFIRMADO");
	}

	@Test
	void sinTokenDa401() throws Exception {
		mvc.perform(post("/api/v1/mis-turnos/1/cancelar")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/turnos/1/cancelar")).andExpect(status().isUnauthorized());
	}

	@Test
	void despuesDeCancelarElHorarioVuelveAEstarDisponible() throws Exception {
		String token = tokenDe(clienteConPerfil("ana@mail.com"));
		Long turno = reservarOk(token, MIERCOLES, "10:00");
		assertThat(horasDelMiercoles()).doesNotContain("10:00");

		postCon(token, "/api/v1/mis-turnos/" + turno + "/cancelar").andExpect(status().isOk());

		assertThat(horasDelMiercoles()).contains("10:00");
		reservar(tokenDe(clienteConPerfil("beto@mail.com")), "barberia-a", servicioA, MIERCOLES, "10:00")
				.andExpect(status().isCreated());
	}

	// --- Barbero ---

	@Test
	void elBarberoCancelaSinLimiteDeHoras() throws Exception {
		// A 1 hora: el cliente ya no podría (24 h), el barbero sí.
		Long turno = reservarOk(tokenDe(clienteConPerfil("ana@mail.com")), LUNES, "10:00");

		postCon(tokenA, "/api/v1/turnos/" + turno + "/cancelar")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("CANCELADO"));

		Map<String, Object> fila = jdbc.queryForMap("SELECT estado, cancelado_por FROM turnos WHERE id = ?", turno);
		assertThat(fila.get("estado")).isEqualTo("CANCELADO");
		assertThat(fila.get("cancelado_por")).isEqualTo("BARBERO");
	}

	@Test
	void elBarberoNoCancelaUnTurnoQueYaEmpezoOPaso() throws Exception {
		Long turno = reservarOk(tokenDe(clienteConPerfil("ana@mail.com")), LUNES, "10:00");

		for (String hora : List.of("10:00", "10:15", "11:00")) {
			reloj.fijar(instante(LUNES, hora));
			postCon(tokenA, "/api/v1/turnos/" + turno + "/cancelar")
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.codigo").value("NO_CANCELABLE"))
					.andExpect(jsonPath("$.detail")
							.value("Este turno ya pasó: marcalo como completado o ausente desde la agenda"));
		}
		assertThat(jdbc.queryForObject("SELECT estado FROM turnos WHERE id = ?", String.class, turno))
				.isEqualTo("CONFIRMADO");
	}

	@Test
	void elBarberoNoCancelaUnTurnoYaCancelado() throws Exception {
		Long turno = reservarOk(tokenDe(clienteConPerfil("ana@mail.com")), MIERCOLES, "10:00");
		postCon(tokenA, "/api/v1/turnos/" + turno + "/cancelar").andExpect(status().isOk());

		postCon(tokenA, "/api/v1/turnos/" + turno + "/cancelar")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("NO_CANCELABLE"))
				.andExpect(jsonPath("$.detail").value("Este turno ya no se puede cancelar"));
	}

	@Test
	void elTurnoDeOtraBarberiaDa404() throws Exception {
		Long turno = reservarOk(tokenDe(clienteConPerfil("ana@mail.com")), MIERCOLES, "10:00");
		String tokenB = duenoConBarberia("b@mail.com", "Barbería B");

		postCon(tokenB, "/api/v1/turnos/" + turno + "/cancelar")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("No encontramos ese turno"));
		assertThat(jdbc.queryForObject("SELECT estado FROM turnos WHERE id = ?", String.class, turno))
				.isEqualTo("CONFIRMADO");
	}

	@Test
	void unClienteSinRolDa403() throws Exception {
		String token = tokenDe(clienteConPerfil("ana@mail.com"));
		Long turno = reservarOk(token, MIERCOLES, "10:00");

		postCon(token, "/api/v1/turnos/" + turno + "/cancelar").andExpect(status().isForbidden());
		assertThat(jdbc.queryForObject("SELECT estado FROM turnos WHERE id = ?", String.class, turno))
				.isEqualTo("CONFIRMADO");
	}

	// ---------------------------------------------------------------------------------------------------------

	private List<String> horasDelMiercoles() throws Exception {
		String json = mvc.perform(get("/api/v1/public/barberias/barberia-a/disponibilidad?servicioId=" + servicioA
				+ "&desde=" + MIERCOLES + "&hasta=" + MIERCOLES))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		List<String> horarios = JsonPath.read(json, "$.dias[0].horarios");
		return horarios.stream().map(h -> h.substring(11, 16)).toList();
	}

}
