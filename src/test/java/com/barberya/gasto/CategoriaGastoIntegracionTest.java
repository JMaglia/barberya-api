package com.barberya.gasto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.barberya.ApiIntegracionTest;
import com.jayway.jsonpath.JsonPath;

class CategoriaGastoIntegracionTest extends ApiIntegracionTest {

	private static final String URL = "/api/v1/categorias-gasto";

	@Test
	void crearListarRenombrarYBorrar() throws Exception {
		String token = duenoConBarberia("a@mail.com", "Barbería A");

		postCon(token, URL, "{\"nombre\":\"  Insumos  \"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.nombre").value("Insumos"));
		Long herramientas = crear(token, "herramientas");
		getCon(token, URL)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].nombre", contains("herramientas", "Insumos")));

		patchCon(token, URL + "/" + herramientas, "{\"nombre\":\"Herramientas\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("Herramientas"));
		assertThat(jdbc.queryForObject("SELECT nombre FROM categorias_gasto WHERE id = ?", String.class, herramientas))
				.isEqualTo("Herramientas");

		deleteCon(token, URL + "/" + herramientas).andExpect(status().isNoContent());
		getCon(token, URL).andExpect(jsonPath("$[*].nombre", contains("Insumos")));
		deleteCon(token, URL + "/" + herramientas).andExpect(status().isNotFound());
	}

	@Test
	void validaciones() throws Exception {
		String token = duenoConBarberia("a@mail.com", "Barbería A");
		Map<String, String> invalidos = Map.of(
				"{\"nombre\":\"\"}", "Ingresá el nombre de la categoría",
				"{\"nombre\":\"   \"}", "Ingresá el nombre de la categoría",
				"{}", "Ingresá el nombre de la categoría",
				"{\"nombre\":\"" + "x".repeat(61) + "\"}", "El nombre puede tener hasta 60 caracteres");
		for (Map.Entry<String, String> caso : invalidos.entrySet()) {
			postCon(token, URL, caso.getKey())
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errores.nombre").value(caso.getValue()));
		}
		Long id = crear(token, "Insumos");
		patchCon(token, URL + "/" + id, "{\"nombre\":\"\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.nombre").exists());
		assertThat(jdbc.queryForObject("SELECT nombre FROM categorias_gasto", String.class)).isEqualTo("Insumos");
	}

	@Test
	void nombreRepetidoDa409SinImportarMayusculas() throws Exception {
		String token = duenoConBarberia("a@mail.com", "Barbería A");
		Long insumos = crear(token, "Insumos");
		Long limpieza = crear(token, "Limpieza");

		postCon(token, URL, "{\"nombre\":\" INSUMOS \"}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("Ya tenés una categoría con ese nombre"));
		patchCon(token, URL + "/" + limpieza, "{\"nombre\":\"insumos\"}").andExpect(status().isConflict());
		// Renombrarse a sí misma cambiando mayúsculas está bien.
		patchCon(token, URL + "/" + insumos, "{\"nombre\":\"INSUMOS\"}").andExpect(status().isOk());

		// Otra barbería puede usar el mismo nombre.
		crear(duenoConBarberia("b@mail.com", "Barbería B"), "Insumos");
	}

	@Test
	void conGastosNoSeBorra() throws Exception {
		String token = duenoConBarberia("a@mail.com", "Barbería A");
		Long categoria = crear(token, "Insumos");
		Long fija = crear(token, "Alquiler");
		postCon(token, "/api/v1/gastos", """
				{"descripcion":"Navajas","monto":1000,"fecha":"2026-11-20","categoriaId":%d}
				""".formatted(categoria)).andExpect(status().isCreated());
		// Un gasto fijo también la deja en uso.
		jdbc.update("INSERT INTO gastos (barberia_id, categoria_id, descripcion, monto, fecha, es_fijo) "
				+ "VALUES (?, ?, 'Alquiler', 100000, '2026-11-01', TRUE)", barberiaIdDeSlug("barberia-a"), fija);

		deleteCon(token, URL + "/" + categoria)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("Esa categoría tiene gastos cargados y no se puede borrar"));
		deleteCon(token, URL + "/" + fija).andExpect(status().isConflict());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM categorias_gasto", Integer.class)).isEqualTo(2);
	}

	@Test
	void aNoVeNiTocaLasCategoriasDeB() throws Exception {
		String tokenB = duenoConBarberia("b@mail.com", "Barbería B");
		Long deB = crear(tokenB, "Categoría B");
		String tokenA = duenoConBarberia("a@mail.com", "Barbería A");
		crear(tokenA, "Categoría A");

		getCon(tokenA, URL).andExpect(jsonPath("$[*].nombre", contains("Categoría A")));
		patchCon(tokenA, URL + "/" + deB, "{\"nombre\":\"Robada\"}")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("No encontramos esa categoría"));
		deleteCon(tokenA, URL + "/" + deB).andExpect(status().isNotFound());

		assertThat(jdbc.queryForObject("SELECT nombre FROM categorias_gasto WHERE id = ?", String.class, deB))
				.isEqualTo("Categoría B");
	}

	@Test
	void crearIgnoraUnBarberiaIdEnElBody() throws Exception {
		duenoConBarberia("b@mail.com", "Barbería B");
		Long idB = barberiaIdDeSlug("barberia-b");
		String tokenA = duenoConBarberia("a@mail.com", "Barbería A");

		postCon(tokenA, URL, "{\"barberiaId\":%d,\"nombre\":\"Insumos\"}".formatted(idB)).andExpect(status().isCreated());

		assertThat(jdbc.queryForObject("SELECT barberia_id FROM categorias_gasto", Long.class))
				.isEqualTo(barberiaIdDeSlug("barberia-a"));
	}

	private Long crear(String token, String nombre) throws Exception {
		String respuesta = postCon(token, URL, "{\"nombre\":\"%s\"}".formatted(nombre))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
	}

}
