package com.barberya.barberia;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.barberya.ApiIntegracionTest;

class BarberiaPublicaIntegracionTest extends ApiIntegracionTest {

	private static final String URL = "/api/v1/public/barberias/";

	@Test
	void datosPublicosSinTokenYSinDatosInternos() throws Exception {
		String token = duenoConBarberia("a@mail.com", "Lo de Juan");
		patchCon(token, "/api/v1/barberia", """
				{"direccion":"Calle 1","cancelarHastaHoras":12,"pausaEntreTurnosMin":10}
				""").andExpect(status().isOk());

		mvc.perform(get(URL + "lo-de-juan"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("Lo de Juan"))
				.andExpect(jsonPath("$.slug").value("lo-de-juan"))
				.andExpect(jsonPath("$.direccion").value("Calle 1"))
				.andExpect(jsonPath("$.telefono").value("1122334455"))
				.andExpect(jsonPath("$.zonaHoraria").value("America/Argentina/Buenos_Aires"))
				.andExpect(jsonPath("$.cancelarHastaHoras").value(12))
				.andExpect(jsonPath("$.reservaMaxDias").value(30))
				.andExpect(jsonPath("$.reservaMinMinutos").value(60))
				.andExpect(jsonPath("$.id").doesNotExist())
				.andExpect(jsonPath("$.activa").doesNotExist())
				.andExpect(jsonPath("$.pausaEntreTurnosMin").doesNotExist())
				.andExpect(jsonPath("$.creadoEn").doesNotExist());
	}

	@Test
	void serviciosSoloActivosPorNombreYSinDatosInternos() throws Exception {
		String token = duenoConBarberia("a@mail.com", "Lo de Juan");
		postCon(token, "/api/v1/servicios", "{\"nombre\":\"Corte\",\"duracionMin\":30,\"precio\":5000}")
				.andExpect(status().isCreated());
		postCon(token, "/api/v1/servicios", "{\"nombre\":\"Barba\",\"duracionMin\":15,\"precio\":3000}")
				.andExpect(status().isCreated());
		String viejo = postCon(token, "/api/v1/servicios", "{\"nombre\":\"Alisado\",\"duracionMin\":60,\"precio\":9000}")
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		Number idViejo = com.jayway.jsonpath.JsonPath.read(viejo, "$.id");
		deleteCon(token, "/api/v1/servicios/" + idViejo).andExpect(status().isNoContent());
		// Los de otra barbería no aparecen.
		String otra = duenoConBarberia("b@mail.com", "Otra");
		postCon(otra, "/api/v1/servicios", "{\"nombre\":\"Ajeno\",\"duracionMin\":30,\"precio\":1}")
				.andExpect(status().isCreated());

		mvc.perform(get(URL + "lo-de-juan/servicios"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].nombre", contains("Barba", "Corte")))
				.andExpect(jsonPath("$[0].id").isNumber())
				.andExpect(jsonPath("$[0].duracionMin").value(15))
				.andExpect(jsonPath("$[0].precio").value(3000))
				.andExpect(jsonPath("$[0].activo").doesNotExist());
	}

	@Test
	void horariosSinTokenOrdenadosYSinDatosInternos() throws Exception {
		String token = duenoConBarberia("a@mail.com", "Lo de Juan");
		putCon(token, "/api/v1/horarios", """
				[{"diaSemana":2,"horaInicio":"14:00","horaFin":"20:00"},
				 {"diaSemana":2,"horaInicio":"09:00","horaFin":"13:00"},
				 {"diaSemana":0,"horaInicio":"10:00","horaFin":"13:00"}]
				""").andExpect(status().isOk());
		String inicio = java.time.LocalDate.now().plusDays(5) + "T10:00:00-03:00";
		String fin = java.time.LocalDate.now().plusDays(5) + "T12:00:00-03:00";
		postCon(token, "/api/v1/bloqueos", "{\"inicio\":\"%s\",\"fin\":\"%s\",\"motivo\":\"Privado\"}".formatted(inicio, fin))
				.andExpect(status().isCreated());
		// Los de otra barbería no aparecen.
		String otra = duenoConBarberia("b@mail.com", "Otra");
		putCon(otra, "/api/v1/horarios", "[{\"diaSemana\":3,\"horaInicio\":\"08:00\",\"horaFin\":\"12:00\"}]")
				.andExpect(status().isOk());

		mvc.perform(get(URL + "lo-de-juan/horarios"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].diaSemana", contains(0, 2, 2)))
				.andExpect(jsonPath("$[*].horaInicio", contains("10:00", "09:00", "14:00")))
				.andExpect(jsonPath("$[*].horaFin", contains("13:00", "13:00", "20:00")))
				.andExpect(jsonPath("$[0].id").doesNotExist())
				.andExpect(jsonPath("$[0].miembroId").doesNotExist())
				.andExpect(jsonPath("$[0].motivo").doesNotExist())
				.andExpect(jsonPath("$..inicio").isEmpty());
	}

	@Test
	void slugInexistenteOBarberiaSuspendidaDa404() throws Exception {
		duenoConBarberia("a@mail.com", "Lo de Juan");
		jdbc.update("UPDATE barberias SET activa = FALSE WHERE slug = 'lo-de-juan'");

		for (String url : new String[] { "no-existe", "no-existe/servicios", "no-existe/horarios", "lo-de-juan",
				"lo-de-juan/servicios", "lo-de-juan/horarios" }) {
			mvc.perform(get(URL + url))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.detail").value("No encontramos esa barbería"));
		}
	}

}
