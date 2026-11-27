package com.barberya.gasto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.barberya.ApiIntegracionTest;
import com.jayway.jsonpath.JsonPath;

class ProveedorIntegracionTest extends ApiIntegracionTest {

	private static final String URL = "/api/v1/proveedores";

	@Test
	void crearYListar() throws Exception {
		String token = duenoConBarberia("a@mail.com", "Barbería A");

		postCon(token, URL, """
				{"nombre":"  Distribuidora Sur  ","rubro":"Insumos","telefono":"1155556666",
				 "email":"Ventas@Sur.COM","notas":"Entrega los martes"}
				""")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.nombre").value("Distribuidora Sur"))
				.andExpect(jsonPath("$.rubro").value("Insumos"))
				.andExpect(jsonPath("$.telefono").value("1155556666"))
				.andExpect(jsonPath("$.email").value("ventas@sur.com"))
				.andExpect(jsonPath("$.notas").value("Entrega los martes"))
				.andExpect(jsonPath("$.activo").value(true));
		postCon(token, URL, "{\"nombre\":\"barbería shop\",\"rubro\":\"  \"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.rubro").doesNotExist());

		getCon(token, URL)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].nombre", contains("barbería shop", "Distribuidora Sur")));
	}

	@Test
	void editarCambiaSoloLoQueVieneYVacioBorra() throws Exception {
		String token = duenoConBarberia("a@mail.com", "Barbería A");
		Long id = crear(token, "Sur");
		patchCon(token, URL + "/" + id, "{\"rubro\":\"Insumos\",\"telefono\":\"111\"}").andExpect(status().isOk());

		patchCon(token, URL + "/" + id, "{\"email\":\"A@B.COM\",\"rubro\":\"\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("Sur"))
				.andExpect(jsonPath("$.telefono").value("111"))
				.andExpect(jsonPath("$.email").value("a@b.com"))
				.andExpect(jsonPath("$.rubro").doesNotExist());

		assertThat(jdbc.queryForMap("SELECT nombre, rubro, telefono, email FROM proveedores WHERE id = ?", id))
				.containsEntry("nombre", "Sur")
				.containsEntry("rubro", null)
				.containsEntry("telefono", "111")
				.containsEntry("email", "a@b.com");
	}

	@Test
	void bajaLogicaYReactivar() throws Exception {
		String token = duenoConBarberia("a@mail.com", "Barbería A");
		Long sur = crear(token, "Sur");
		crear(token, "Norte");

		deleteCon(token, URL + "/" + sur).andExpect(status().isNoContent());
		deleteCon(token, URL + "/" + sur).andExpect(status().isNoContent());

		assertThat(jdbc.queryForObject("SELECT activo FROM proveedores WHERE id = ?", Boolean.class, sur)).isFalse();
		getCon(token, URL).andExpect(jsonPath("$[*].nombre", contains("Norte")));
		getCon(token, URL + "?incluirInactivos=true")
				.andExpect(jsonPath("$[*].nombre", contains("Norte", "Sur")))
				.andExpect(jsonPath("$[1].activo").value(false));

		postCon(token, URL + "/" + sur + "/reactivar")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activo").value(true));
		getCon(token, URL).andExpect(jsonPath("$", hasSize(2)));
	}

	@Test
	void validaciones() throws Exception {
		String token = duenoConBarberia("a@mail.com", "Barbería A");
		Map<String, String> invalidos = Map.of(
				"{\"nombre\":\"\"}", "nombre",
				"{}", "nombre",
				"{\"nombre\":\"" + "x".repeat(121) + "\"}", "nombre",
				"{\"nombre\":\"Sur\",\"rubro\":\"" + "x".repeat(81) + "\"}", "rubro",
				"{\"nombre\":\"Sur\",\"telefono\":\"" + "1".repeat(31) + "\"}", "telefono",
				"{\"nombre\":\"Sur\",\"email\":\"no-es-mail\"}", "email",
				"{\"nombre\":\"Sur\",\"notas\":\"" + "x".repeat(1001) + "\"}", "notas");
		for (Map.Entry<String, String> caso : invalidos.entrySet()) {
			postCon(token, URL, caso.getKey())
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errores." + caso.getValue()).exists());
		}
		postCon(token, URL, "{\"nombre\":\"Sur\",\"email\":\"no-es-mail\"}")
				.andExpect(jsonPath("$.errores.email").value("El mail no es válido"));

		Long id = crear(token, "Sur");
		patchCon(token, URL + "/" + id, "{\"nombre\":\"   \",\"email\":\"mal\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.nombre").exists())
				.andExpect(jsonPath("$.errores.email").exists());
		assertThat(jdbc.queryForObject("SELECT nombre FROM proveedores WHERE id = ?", String.class, id)).isEqualTo("Sur");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM proveedores", Integer.class)).isOne();
	}

	@Test
	void aNoVeNiTocaLosProveedoresDeB() throws Exception {
		String tokenB = duenoConBarberia("b@mail.com", "Barbería B");
		Long deB = crear(tokenB, "Proveedor B");
		Long inactivoDeB = crear(tokenB, "Viejo B");
		deleteCon(tokenB, URL + "/" + inactivoDeB).andExpect(status().isNoContent());
		String tokenA = duenoConBarberia("a@mail.com", "Barbería A");
		crear(tokenA, "Proveedor A");

		getCon(tokenA, URL + "?incluirInactivos=true").andExpect(jsonPath("$[*].nombre", contains("Proveedor A")));
		patchCon(tokenA, URL + "/" + deB, "{\"nombre\":\"Robado\"}")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("No encontramos ese proveedor"));
		deleteCon(tokenA, URL + "/" + deB).andExpect(status().isNotFound());
		postCon(tokenA, URL + "/" + inactivoDeB + "/reactivar").andExpect(status().isNotFound());

		assertThat(jdbc.queryForMap("SELECT nombre, activo FROM proveedores WHERE id = ?", deB))
				.containsEntry("nombre", "Proveedor B")
				.containsEntry("activo", true);
		assertThat(jdbc.queryForObject("SELECT activo FROM proveedores WHERE id = ?", Boolean.class, inactivoDeB)).isFalse();
	}

	@Test
	void crearIgnoraUnBarberiaIdEnElBody() throws Exception {
		duenoConBarberia("b@mail.com", "Barbería B");
		Long idB = barberiaIdDeSlug("barberia-b");
		String tokenA = duenoConBarberia("a@mail.com", "Barbería A");

		postCon(tokenA, URL, "{\"barberiaId\":%d,\"nombre\":\"Sur\"}".formatted(idB)).andExpect(status().isCreated());

		assertThat(jdbc.queryForObject("SELECT barberia_id FROM proveedores", Long.class))
				.isEqualTo(barberiaIdDeSlug("barberia-a"));
	}

	private Long crear(String token, String nombre) throws Exception {
		String respuesta = postCon(token, URL, "{\"nombre\":\"%s\"}".formatted(nombre))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
	}

}
