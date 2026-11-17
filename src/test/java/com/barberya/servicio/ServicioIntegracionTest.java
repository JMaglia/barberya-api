package com.barberya.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.barberya.ApiIntegracionTest;
import com.jayway.jsonpath.JsonPath;

class ServicioIntegracionTest extends ApiIntegracionTest {

	private static final String URL = "/api/v1/servicios";

	@Test
	void crearYListar() throws Exception {
		String token = duenoConBarberia("a@mail.com", "Barbería A");

		postCon(token, URL, """
				{"nombre":"  Corte  ","descripcion":"Con tijera","duracionMin":30,"precio":5000.5}
				""")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.nombre").value("Corte"))
				.andExpect(jsonPath("$.descripcion").value("Con tijera"))
				.andExpect(jsonPath("$.duracionMin").value(30))
				.andExpect(jsonPath("$.precio").value(5000.5))
				.andExpect(jsonPath("$.activo").value(true));
		crear(token, "barba", 15, "3000");

		assertThat(jdbc.queryForObject("SELECT precio FROM servicios WHERE nombre = 'Corte'", BigDecimal.class))
				.isEqualByComparingTo("5000.50");
		getCon(token, URL)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].nombre", contains("barba", "Corte")));
	}

	@Test
	void editarCambiaSoloLoQueViene() throws Exception {
		String token = duenoConBarberia("a@mail.com", "Barbería A");
		Long id = crear(token, "Corte", 30, "5000");

		patchCon(token, URL + "/" + id, """
				{"precio":6000,"duracionMin":45}
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("Corte"))
				.andExpect(jsonPath("$.duracionMin").value(45))
				.andExpect(jsonPath("$.precio").value(6000));

		assertThat(jdbc.queryForMap("SELECT nombre, duracion_min, precio FROM servicios WHERE id = ?", id))
				.containsEntry("nombre", "Corte")
				.containsEntry("duracion_min", 45);
	}

	@Test
	void bajaLogicaYReactivar() throws Exception {
		String token = duenoConBarberia("a@mail.com", "Barbería A");
		Long corte = crear(token, "Corte", 30, "5000");
		crear(token, "Barba", 15, "3000");

		deleteCon(token, URL + "/" + corte).andExpect(status().isNoContent());
		deleteCon(token, URL + "/" + corte).andExpect(status().isNoContent());

		assertThat(jdbc.queryForObject("SELECT activo FROM servicios WHERE id = ?", Boolean.class, corte)).isFalse();
		getCon(token, URL).andExpect(jsonPath("$[*].nombre", contains("Barba")));
		getCon(token, URL + "?incluirInactivos=true")
				.andExpect(jsonPath("$[*].nombre", contains("Barba", "Corte")))
				.andExpect(jsonPath("$[1].activo").value(false));

		postCon(token, URL + "/" + corte + "/reactivar")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activo").value(true));
		getCon(token, URL).andExpect(jsonPath("$", hasSize(2)));
	}

	@Test
	void validaciones() throws Exception {
		String token = duenoConBarberia("a@mail.com", "Barbería A");
		Map<String, String> invalidos = Map.of(
				"{\"nombre\":\"\",\"duracionMin\":30,\"precio\":100}", "nombre",
				"{\"nombre\":\"Corte\",\"duracionMin\":30,\"precio\":0}", "precio",
				"{\"nombre\":\"Corte\",\"duracionMin\":30,\"precio\":10.555}", "precio",
				"{\"nombre\":\"Corte\",\"duracionMin\":30}", "precio",
				"{\"nombre\":\"Corte\",\"duracionMin\":3,\"precio\":100}", "duracionMin",
				"{\"nombre\":\"Corte\",\"duracionMin\":500,\"precio\":100}", "duracionMin",
				"{\"nombre\":\"Corte\",\"duracionMin\":22,\"precio\":100}", "duracionMin");
		for (Map.Entry<String, String> caso : invalidos.entrySet()) {
			postCon(token, URL, caso.getKey())
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errores." + caso.getValue()).exists());
		}
		postCon(token, URL, "{\"nombre\":\"Corte\",\"duracionMin\":22,\"precio\":100}")
				.andExpect(jsonPath("$.errores.duracionMin").value("La duración tiene que ser múltiplo de 5"));

		Long id = crear(token, "Corte", 30, "5000");
		patchCon(token, URL + "/" + id, "{\"nombre\":\"  \",\"precio\":-1,\"duracionMin\":7}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.nombre").exists())
				.andExpect(jsonPath("$.errores.precio").exists())
				.andExpect(jsonPath("$.errores.duracionMin").exists());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM servicios", Integer.class)).isOne();
	}

	@Test
	void nombreRepetidoEntreActivosDa409() throws Exception {
		String token = duenoConBarberia("a@mail.com", "Barbería A");
		Long corte = crear(token, "Corte", 30, "5000");
		Long barba = crear(token, "Barba", 15, "3000");

		postCon(token, URL, "{\"nombre\":\" CORTE \",\"duracionMin\":30,\"precio\":100}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("Ya tenés un servicio activo con ese nombre"));
		patchCon(token, URL + "/" + barba, "{\"nombre\":\"corte\"}").andExpect(status().isConflict());
		// Renombrarse a sí mismo (cambiando mayúsculas) está bien.
		patchCon(token, URL + "/" + corte, "{\"nombre\":\"CORTE\"}").andExpect(status().isOk());

		// Con el viejo dado de baja se puede crear otro con el mismo nombre, pero no reactivar el viejo.
		deleteCon(token, URL + "/" + corte).andExpect(status().isNoContent());
		crear(token, "Corte", 45, "7000");
		postCon(token, URL + "/" + corte + "/reactivar").andExpect(status().isConflict());
	}

	@Test
	void otraBarberiaUsaElMismoNombreSinProblema() throws Exception {
		crear(duenoConBarberia("a@mail.com", "Barbería A"), "Corte", 30, "5000");
		crear(duenoConBarberia("b@mail.com", "Barbería B"), "Corte", 30, "5000");
	}

	@Test
	void aNoVeNiTocaLosServiciosDeB() throws Exception {
		String tokenB = duenoConBarberia("b@mail.com", "Barbería B");
		Long deB = crear(tokenB, "Corte B", 30, "5000");
		Long inactivoDeB = crear(tokenB, "Viejo B", 30, "5000");
		deleteCon(tokenB, URL + "/" + inactivoDeB).andExpect(status().isNoContent());
		String tokenA = duenoConBarberia("a@mail.com", "Barbería A");
		crear(tokenA, "Corte A", 30, "4000");

		getCon(tokenA, URL + "?incluirInactivos=true").andExpect(jsonPath("$[*].nombre", contains("Corte A")));
		patchCon(tokenA, URL + "/" + deB, "{\"nombre\":\"Robado\",\"precio\":1}")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("No encontramos ese servicio"));
		deleteCon(tokenA, URL + "/" + deB).andExpect(status().isNotFound());
		postCon(tokenA, URL + "/" + inactivoDeB + "/reactivar").andExpect(status().isNotFound());

		assertThat(jdbc.queryForMap("SELECT nombre, precio, activo FROM servicios WHERE id = ?", deB))
				.containsEntry("nombre", "Corte B")
				.containsEntry("activo", true);
		assertThat(jdbc.queryForObject("SELECT activo FROM servicios WHERE id = ?", Boolean.class, inactivoDeB)).isFalse();
	}

	@Test
	void crearIgnoraUnBarberiaIdEnElBody() throws Exception {
		duenoConBarberia("b@mail.com", "Barbería B");
		Long idB = barberiaIdDeSlug("barberia-b");
		String tokenA = duenoConBarberia("a@mail.com", "Barbería A");

		postCon(tokenA, URL, """
				{"barberiaId":%d,"nombre":"Corte","duracionMin":30,"precio":100}
				""".formatted(idB)).andExpect(status().isCreated());

		assertThat(jdbc.queryForObject("SELECT barberia_id FROM servicios", Long.class))
				.isEqualTo(barberiaIdDeSlug("barberia-a"));
	}

	private Long crear(String token, String nombre, int duracion, String precio) throws Exception {
		String respuesta = postCon(token, URL, """
				{"nombre":"%s","duracionMin":%d,"precio":%s}
				""".formatted(nombre, duracion, precio))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
	}

}
