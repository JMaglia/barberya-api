package com.barberya.gasto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.barberya.ApiIntegracionTest;

/**
 * Dos requests que crean o renombran a la vez una categoría con el mismo nombre: se simula que el control previo no
 * vio la que otra request acaba de guardar, así el guardado choca con el UNIQUE (barberia_id, nombre). Tiene que dar
 * 409, no 500.
 */
class CategoriaSimultaneaIntegracionTest extends ApiIntegracionTest {

	private static final String URL = "/api/v1/categorias-gasto";

	@MockitoSpyBean
	private CategoriaGastoRepository categorias;

	@Test
	void crearYRenombrarEnParaleloDan409() throws Exception {
		String token = duenoConBarberia("a@mail.com", "Barbería A");
		Long barberia = barberiaIdDeSlug("barberia-a");
		jdbc.update("INSERT INTO categorias_gasto (barberia_id, nombre) VALUES (?, 'Insumos')", barberia);
		Long limpieza = jdbc.queryForObject(
				"INSERT INTO categorias_gasto (barberia_id, nombre) VALUES (?, 'Limpieza') RETURNING id", Long.class, barberia);
		doReturn(false).when(categorias).existsByBarberiaIdAndNombreIgnoreCase(anyLong(), anyString());
		doReturn(false).when(categorias).existsByBarberiaIdAndNombreIgnoreCaseAndIdNot(anyLong(), anyString(), anyLong());

		postCon(token, URL, "{\"nombre\":\"Insumos\"}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("Ya tenés una categoría con ese nombre"));
		patchCon(token, URL + "/" + limpieza, "{\"nombre\":\"Insumos\"}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("Ya tenés una categoría con ese nombre"));

		assertThat(jdbc.queryForList("SELECT nombre FROM categorias_gasto ORDER BY nombre", String.class))
				.containsExactly("Insumos", "Limpieza");
	}

}
