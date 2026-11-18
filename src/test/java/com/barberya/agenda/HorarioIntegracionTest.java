package com.barberya.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.barberya.ApiIntegracionTest;

class HorarioIntegracionTest extends ApiIntegracionTest {

	private static final String URL = "/api/v1/horarios";

	@Test
	void sinHorariosDevuelveListaVacia() throws Exception {
		String token = duenoConBarberia("a@mail.com", "Barbería A");

		getCon(token, URL).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void putGuardaLaSemanaYGetLaDevuelveOrdenada() throws Exception {
		String token = duenoConBarberia("a@mail.com", "Barbería A");

		// Desordenado, con turno partido contiguo el lunes y un domingo.
		putCon(token, URL, """
				[{"diaSemana":1,"horaInicio":"13:00","horaFin":"17:00"},
				 {"diaSemana":6,"horaInicio":"10:00","horaFin":"14:00"},
				 {"diaSemana":1,"horaInicio":"09:00","horaFin":"13:00"},
				 {"diaSemana":0,"horaInicio":"10:30","horaFin":"12:55"}]
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(4)))
				.andExpect(jsonPath("$[*].diaSemana", contains(0, 1, 1, 6)));

		getCon(token, URL)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].diaSemana", contains(0, 1, 1, 6)))
				.andExpect(jsonPath("$[*].horaInicio", contains("10:30", "09:00", "13:00", "10:00")))
				.andExpect(jsonPath("$[*].horaFin", contains("12:55", "13:00", "17:00", "14:00")))
				.andExpect(jsonPath("$[0].id").doesNotExist());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM horarios_atencion WHERE miembro_id = ?", Integer.class,
				miembroIdDeSlug("barberia-a"))).isEqualTo(4);
	}

	@Test
	void putReemplazaLoAnterior() throws Exception {
		String token = duenoConBarberia("a@mail.com", "Barbería A");
		putCon(token, URL, """
				[{"diaSemana":1,"horaInicio":"09:00","horaFin":"13:00"},
				 {"diaSemana":2,"horaInicio":"09:00","horaFin":"13:00"}]
				""").andExpect(status().isOk());

		putCon(token, URL, """
				[{"diaSemana":3,"horaInicio":"15:00","horaFin":"20:00"}]
				""").andExpect(status().isOk());

		assertThat(jdbc.queryForList("SELECT dia_semana FROM horarios_atencion", Integer.class)).containsExactly(3);
		getCon(token, URL).andExpect(jsonPath("$[*].horaInicio", contains("15:00")));
	}

	@Test
	void listaVaciaCierraTodosLosDias() throws Exception {
		String token = duenoConBarberia("a@mail.com", "Barbería A");
		putCon(token, URL, "[{\"diaSemana\":1,\"horaInicio\":\"09:00\",\"horaFin\":\"13:00\"}]").andExpect(status().isOk());

		putCon(token, URL, "[]").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));

		assertThat(jdbc.queryForObject("SELECT count(*) FROM horarios_atencion", Integer.class)).isZero();
	}

	@Test
	void validacionesDan400YNoTocanLaSemana() throws Exception {
		String token = duenoConBarberia("a@mail.com", "Barbería A");
		putCon(token, URL, "[{\"diaSemana\":5,\"horaInicio\":\"09:00\",\"horaFin\":\"13:00\"}]").andExpect(status().isOk());

		Map<String, String> invalidos = new LinkedHashMap<>();
		invalidos.put("[{\"diaSemana\":1,\"horaInicio\":\"13:00\",\"horaFin\":\"09:00\"}]", "[0]");
		invalidos.put("[{\"diaSemana\":1,\"horaInicio\":\"10:00\",\"horaFin\":\"10:00\"}]", "[0]");
		invalidos.put("""
				[{"diaSemana":2,"horaInicio":"09:00","horaFin":"10:00"},
				 {"diaSemana":1,"horaInicio":"09:00","horaFin":"13:00"},
				 {"diaSemana":1,"horaInicio":"12:00","horaFin":"15:00"}]""", "[2]");
		invalidos.put("[{\"diaSemana\":1,\"horaInicio\":\"09:07\",\"horaFin\":\"13:00\"}]", "[0]");
		invalidos.put("[{\"diaSemana\":7,\"horaInicio\":\"09:00\",\"horaFin\":\"13:00\"}]", "[0].diaSemana");
		invalidos.put("[{\"diaSemana\":-1,\"horaInicio\":\"09:00\",\"horaFin\":\"13:00\"}]", "[0].diaSemana");
		invalidos.put("[{\"diaSemana\":1,\"horaInicio\":\"09:00\"}]", "[0].horaFin");
		invalidos.put("""
				[{"diaSemana":4,"horaInicio":"08:00","horaFin":"09:00"},
				 {"diaSemana":4,"horaInicio":"10:00","horaFin":"11:00"},
				 {"diaSemana":4,"horaInicio":"12:00","horaFin":"13:00"},
				 {"diaSemana":4,"horaInicio":"14:00","horaFin":"15:00"},
				 {"diaSemana":4,"horaInicio":"16:00","horaFin":"17:00"}]""", "[4]");
		for (Map.Entry<String, String> caso : invalidos.entrySet()) {
			putCon(token, URL, caso.getKey())
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errores['" + caso.getValue() + "']").exists());
		}

		putCon(token, URL, """
				[{"diaSemana":1,"horaInicio":"09:00","horaFin":"13:00"},
				 {"diaSemana":1,"horaInicio":"12:00","horaFin":"15:00"}]
				""")
				.andExpect(jsonPath("$.detail").value("El lunes, 12:00–15:00 se superpone con 09:00–13:00"));
		putCon(token, URL, "[{\"diaSemana\":1,\"horaInicio\":\"9 hs\",\"horaFin\":\"13:00\"}]")
				.andExpect(status().isBadRequest());

		assertThat(jdbc.queryForList("SELECT dia_semana FROM horarios_atencion", Integer.class)).containsExactly(5);
	}

	@Test
	void aNoVeNiTocaLosHorariosDeB() throws Exception {
		String tokenB = duenoConBarberia("b@mail.com", "Barbería B");
		putCon(tokenB, URL, "[{\"diaSemana\":2,\"horaInicio\":\"10:00\",\"horaFin\":\"18:00\"}]").andExpect(status().isOk());
		String tokenA = duenoConBarberia("a@mail.com", "Barbería A");
		Long miembroB = miembroIdDeSlug("barberia-b");

		getCon(tokenA, URL).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
		putCon(tokenA, URL, "[{\"diaSemana\":1,\"horaInicio\":\"09:00\",\"horaFin\":\"13:00\"}]").andExpect(status().isOk());
		putCon(tokenA, URL, "[]").andExpect(status().isOk());

		assertThat(jdbc.queryForList("SELECT dia_semana FROM horarios_atencion WHERE miembro_id = ?", Integer.class, miembroB))
				.containsExactly(2);
		getCon(tokenB, URL).andExpect(jsonPath("$[*].horaInicio", contains("10:00")));
	}

	@Test
	void clienteSinBarberiaRecibe403() throws Exception {
		String token = tokenDe(insertarUsuario("cliente@mail.com"));

		getCon(token, URL).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail").value("Primero creá tu barbería"));
		putCon(token, URL, "[]").andExpect(status().isForbidden());
		getCon(token, "/api/v1/bloqueos?desde=2030-01-01T00:00:00Z&hasta=2030-01-02T00:00:00Z")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail").value("Primero creá tu barbería"));
		postCon(token, "/api/v1/bloqueos", "{\"inicio\":\"2030-01-01T10:00:00Z\",\"fin\":\"2030-01-01T12:00:00Z\"}")
				.andExpect(status().isForbidden());
		deleteCon(token, "/api/v1/bloqueos/1").andExpect(status().isForbidden());
	}

	@Test
	void miembroDadoDeBajaRecibe403AunqueElTokenTengaBarberia() throws Exception {
		String token = duenoConBarberia("a@mail.com", "Barbería A");
		jdbc.update("UPDATE miembros_barberia SET activo = FALSE");

		for (String url : List.of(URL, "/api/v1/bloqueos?desde=2030-01-01T00:00:00Z&hasta=2030-01-02T00:00:00Z")) {
			getCon(token, url).andExpect(status().isForbidden());
		}
		putCon(token, URL, "[]").andExpect(status().isForbidden());
	}

}
