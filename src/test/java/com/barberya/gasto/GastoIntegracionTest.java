package com.barberya.gasto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.barberya.ApiIntegracionTest;
import com.jayway.jsonpath.JsonPath;

class GastoIntegracionTest extends ApiIntegracionTest {

	private static final String URL = "/api/v1/gastos";
	private static final String NOVIEMBRE = URL + "?desde=2026-11-01&hasta=2026-11-30";

	private String token;
	private Long categoria;

	@BeforeEach
	void barberiaConCategoria() throws Exception {
		token = duenoConBarberia("a@mail.com", "Barbería A");
		categoria = crearCategoria(token, "Insumos");
	}

	@Test
	void crearConProveedorYCantidad() throws Exception {
		Long proveedor = crearProveedor(token, "Distribuidora Sur");

		postCon(token, URL, """
				{"descripcion":"  Navajas  ","monto":1000,"fecha":"2026-11-20","categoriaId":%d,
				 "proveedorId":%d,"cantidad":3}
				""".formatted(categoria, proveedor))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.descripcion").value("Navajas"))
				.andExpect(jsonPath("$.monto").value(1000))
				.andExpect(jsonPath("$.fecha").value("2026-11-20"))
				.andExpect(jsonPath("$.cantidad").value(3))
				.andExpect(jsonPath("$.precioUnitario").value(333.33))
				.andExpect(jsonPath("$.categoria.id").value(categoria))
				.andExpect(jsonPath("$.categoria.nombre").value("Insumos"))
				.andExpect(jsonPath("$.proveedor.id").value(proveedor))
				.andExpect(jsonPath("$.proveedor.nombre").value("Distribuidora Sur"))
				.andExpect(jsonPath("$.proveedor.activo").value(true));

		assertThat(jdbc.queryForMap("SELECT monto, es_fijo, gasto_fijo_id, barberia_id FROM gastos"))
				.containsEntry("es_fijo", false)
				.containsEntry("gasto_fijo_id", null)
				.containsEntry("barberia_id", barberiaIdDeSlug("barberia-a"));
		assertThat(jdbc.queryForObject("SELECT monto FROM gastos", BigDecimal.class)).isEqualByComparingTo("1000.00");
	}

	@Test
	void crearSinProveedorNiCantidad() throws Exception {
		postCon(token, URL, """
				{"descripcion":"Alcohol","monto":2500.5,"fecha":"2026-11-20","categoriaId":%d}
				""".formatted(categoria))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.monto").value(2500.5))
				.andExpect(jsonPath("$.cantidad").doesNotExist())
				.andExpect(jsonPath("$.precioUnitario").doesNotExist())
				.andExpect(jsonPath("$.proveedor").doesNotExist());
	}

	@Test
	void precioUnitarioRedondeaADosDecimales() throws Exception {
		postCon(token, URL, cuerpo("Gel", "100", "2026-11-20").replace("}", ",\"cantidad\":6}"))
				.andExpect(jsonPath("$.precioUnitario").value(16.67));
		postCon(token, URL, cuerpo("Peines", "10.05", "2026-11-20").replace("}", ",\"cantidad\":2}"))
				.andExpect(jsonPath("$.precioUnitario").value(5.03));
	}

	@Test
	void esFijoEnElBodySeIgnora() throws Exception {
		postCon(token, URL, """
				{"descripcion":"Navajas","monto":1000,"fecha":"2026-11-20","categoriaId":%d,"esFijo":true,"gastoFijoId":99}
				""".formatted(categoria)).andExpect(status().isCreated());

		assertThat(jdbc.queryForMap("SELECT es_fijo, gasto_fijo_id FROM gastos"))
				.containsEntry("es_fijo", false)
				.containsEntry("gasto_fijo_id", null);
	}

	@Test
	void listarPorRangoInclusivoMasRecientesPrimero() throws Exception {
		crear("Fuera antes", "2026-10-31");
		Long primero = crear("Primero", "2026-11-01");
		crear("Medio", "2026-11-15");
		crear("Medio después", "2026-11-15");
		crear("Último", "2026-11-30");
		crear("Fuera después", "2026-12-01");

		getCon(token, NOVIEMBRE)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].descripcion", contains("Último", "Medio después", "Medio", "Primero")))
				.andExpect(jsonPath("$[3].id").value(primero));
		getCon(token, URL + "?desde=2026-11-15&hasta=2026-11-15")
				.andExpect(jsonPath("$[*].descripcion", contains("Medio después", "Medio")));
	}

	@Test
	void editarCambiaSoloLoQueViene() throws Exception {
		Long proveedor = crearProveedor(token, "Sur");
		Long otraCategoria = crearCategoria(token, "Herramientas");
		Long id = crearCon(cuerpo("Navajas", "1000", "2026-11-20").replace("}", ",\"proveedorId\":" + proveedor
				+ ",\"cantidad\":4}"));

		patchCon(token, URL + "/" + id, """
				{"monto":1200.5,"fecha":"2026-11-21","categoriaId":%d}
				""".formatted(otraCategoria))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.descripcion").value("Navajas"))
				.andExpect(jsonPath("$.monto").value(1200.5))
				.andExpect(jsonPath("$.fecha").value("2026-11-21"))
				.andExpect(jsonPath("$.categoria.nombre").value("Herramientas"))
				.andExpect(jsonPath("$.proveedor.id").value(proveedor))
				.andExpect(jsonPath("$.cantidad").value(4))
				.andExpect(jsonPath("$.precioUnitario").value(300.13));

		assertThat(jdbc.queryForMap("SELECT descripcion, fecha::text AS fecha, categoria_id, cantidad FROM gastos WHERE id = ?", id))
				.containsEntry("descripcion", "Navajas")
				.containsEntry("fecha", "2026-11-21")
				.containsEntry("categoria_id", otraCategoria)
				.containsEntry("cantidad", 4);
	}

	@Test
	void editarQuitaProveedorYCantidad() throws Exception {
		Long proveedor = crearProveedor(token, "Sur");
		Long id = crearCon(cuerpo("Navajas", "1000", "2026-11-20").replace("}", ",\"proveedorId\":" + proveedor
				+ ",\"cantidad\":4}"));

		patchCon(token, URL + "/" + id, "{\"quitarProveedor\":true,\"quitarCantidad\":true}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.proveedor").doesNotExist())
				.andExpect(jsonPath("$.cantidad").doesNotExist())
				.andExpect(jsonPath("$.precioUnitario").doesNotExist());

		assertThat(jdbc.queryForMap("SELECT proveedor_id, cantidad FROM gastos WHERE id = ?", id))
				.containsEntry("proveedor_id", null)
				.containsEntry("cantidad", null);
	}

	@Test
	void borrar() throws Exception {
		Long id = crear("Navajas", "2026-11-20");
		Long otro = crear("Alcohol", "2026-11-21");

		deleteCon(token, URL + "/" + id).andExpect(status().isNoContent());
		deleteCon(token, URL + "/" + id)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("No encontramos ese gasto"));

		assertThat(jdbc.queryForList("SELECT id FROM gastos", Long.class)).containsExactly(otro);
	}

	@Test
	void validacionesAlCrear() throws Exception {
		String base = "\"descripcion\":\"Navajas\",\"fecha\":\"2026-11-20\",\"categoriaId\":" + categoria;
		Map<String, String> invalidos = Map.of(
				"{" + base + ",\"monto\":0}", "monto",
				"{" + base + ",\"monto\":-5}", "monto",
				"{" + base + ",\"monto\":10.555}", "monto",
				"{" + base + "}", "monto",
				"{" + base + ",\"monto\":100,\"cantidad\":0}", "cantidad",
				"{\"descripcion\":\" \",\"monto\":100,\"fecha\":\"2026-11-20\",\"categoriaId\":" + categoria + "}", "descripcion",
				"{\"descripcion\":\"" + "x".repeat(201) + "\",\"monto\":100,\"fecha\":\"2026-11-20\",\"categoriaId\":"
						+ categoria + "}", "descripcion",
				"{\"descripcion\":\"Navajas\",\"monto\":100,\"categoriaId\":" + categoria + "}", "fecha",
				"{\"descripcion\":\"Navajas\",\"monto\":100,\"fecha\":\"2026-11-20\"}", "categoriaId");
		for (Map.Entry<String, String> caso : invalidos.entrySet()) {
			postCon(token, URL, caso.getKey())
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errores." + caso.getValue()).exists());
		}
		postCon(token, URL, "{" + base + ",\"monto\":10.555}")
				.andExpect(jsonPath("$.errores.monto").value("El monto puede tener hasta 2 decimales"));
		postCon(token, URL, "{\"descripcion\":\"Navajas\",\"monto\":100,\"fecha\":\"2026-11-20\"}")
				.andExpect(jsonPath("$.errores.categoriaId").value("Elegí una categoría"));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM gastos", Integer.class)).isZero();
	}

	@Test
	void validacionesAlEditar() throws Exception {
		Long proveedor = crearProveedor(token, "Sur");
		Long id = crear("Navajas", "2026-11-20");

		patchCon(token, URL + "/" + id, "{\"descripcion\":\"  \",\"monto\":0,\"cantidad\":-1}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.descripcion").exists())
				.andExpect(jsonPath("$.errores.monto").exists())
				.andExpect(jsonPath("$.errores.cantidad").exists());
		patchCon(token, URL + "/" + id, "{\"quitarProveedor\":true,\"proveedorId\":" + proveedor + "}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.quitarProveedor").exists());
		patchCon(token, URL + "/" + id, "{\"quitarCantidad\":true,\"cantidad\":2}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.quitarCantidad").exists());

		assertThat(jdbc.queryForMap("SELECT descripcion, proveedor_id, cantidad FROM gastos WHERE id = ?", id))
				.containsEntry("descripcion", "Navajas")
				.containsEntry("proveedor_id", null)
				.containsEntry("cantidad", null);
	}

	@Test
	void validacionesDelListado() throws Exception {
		getCon(token, URL + "?desde=2026-11-01")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.hasta").exists());
		getCon(token, URL + "?hasta=2026-11-01")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.desde").exists());
		getCon(token, URL + "?desde=2026-11-30&hasta=2026-11-01")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.hasta").value("La fecha \"hasta\" no puede ser anterior a \"desde\""));
		getCon(token, URL + "?desde=2026-01-01&hasta=2027-01-02")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.hasta").value("El rango puede ser de hasta 366 días"));
		getCon(token, URL + "?desde=nada&hasta=2026-11-01")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.desde").exists());
		// 366 días justos está bien.
		getCon(token, URL + "?desde=2026-01-01&hasta=2027-01-01").andExpect(status().isOk());
	}

	@Test
	void proveedorInactivoNoSePuedeElegirPeroUnGastoViejoLoConserva() throws Exception {
		Long viejo = crearProveedor(token, "Viejo");
		Long gasto = crearCon(cuerpo("Navajas", "1000", "2026-11-20").replace("}", ",\"proveedorId\":" + viejo + "}"));
		Long otroGasto = crear("Alcohol", "2026-11-20");
		deleteCon(token, "/api/v1/proveedores/" + viejo).andExpect(status().isNoContent());

		postCon(token, URL, cuerpo("Peines", "500", "2026-11-21").replace("}", ",\"proveedorId\":" + viejo + "}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("No encontramos ese proveedor"));
		patchCon(token, URL + "/" + otroGasto, "{\"proveedorId\":" + viejo + "}").andExpect(status().isNotFound());

		// El gasto que ya lo tenía se puede editar, incluso mandando el mismo proveedor.
		patchCon(token, URL + "/" + gasto, "{\"monto\":1100,\"proveedorId\":" + viejo + "}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.proveedor.id").value(viejo))
				.andExpect(jsonPath("$.proveedor.activo").value(false));
		getCon(token, NOVIEMBRE).andExpect(jsonPath("$[?(@.id == " + gasto + ")].proveedor.nombre", contains("Viejo")));
	}

	@Test
	void gastosFijosNoSeVenNiSeTocan() throws Exception {
		Long barberia = barberiaIdDeSlug("barberia-a");
		Long fijo = jdbc.queryForObject("""
				INSERT INTO gastos (barberia_id, categoria_id, descripcion, monto, fecha, es_fijo)
				VALUES (?, ?, 'Alquiler', 100000, '2026-11-05', TRUE) RETURNING id
				""", Long.class, barberia, categoria);

		getCon(token, NOVIEMBRE).andExpect(jsonPath("$", empty()));
		patchCon(token, URL + "/" + fijo, "{\"monto\":1}")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("No encontramos ese gasto"));
		deleteCon(token, URL + "/" + fijo).andExpect(status().isNotFound());

		assertThat(jdbc.queryForObject("SELECT monto FROM gastos WHERE id = ?", BigDecimal.class, fijo))
				.isEqualByComparingTo("100000");
	}

	@Test
	void aNoVeNiTocaLosGastosDeB() throws Exception {
		String tokenB = duenoConBarberia("b@mail.com", "Barbería B");
		Long categoriaB = crearCategoria(tokenB, "Insumos B");
		String respuesta = postCon(tokenB, URL, """
				{"descripcion":"Gasto B","monto":500,"fecha":"2026-11-10","categoriaId":%d}
				""".formatted(categoriaB)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		Long deB = ((Number) JsonPath.read(respuesta, "$.id")).longValue();
		crear("Gasto A", "2026-11-10");

		getCon(token, NOVIEMBRE).andExpect(jsonPath("$[*].descripcion", contains("Gasto A")));
		patchCon(token, URL + "/" + deB, "{\"monto\":1}")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("No encontramos ese gasto"));
		deleteCon(token, URL + "/" + deB).andExpect(status().isNotFound());

		assertThat(jdbc.queryForObject("SELECT monto FROM gastos WHERE id = ?", BigDecimal.class, deB))
				.isEqualByComparingTo("500");
	}

	@Test
	void categoriaOProveedorDeOtraBarberiaDan404() throws Exception {
		String tokenB = duenoConBarberia("b@mail.com", "Barbería B");
		Long categoriaB = crearCategoria(tokenB, "Insumos B");
		Long proveedorB = crearProveedor(tokenB, "Proveedor B");

		postCon(token, URL, """
				{"descripcion":"Navajas","monto":1000,"fecha":"2026-11-20","categoriaId":%d}
				""".formatted(categoriaB))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("No encontramos esa categoría"));
		postCon(token, URL, cuerpo("Navajas", "1000", "2026-11-20").replace("}", ",\"proveedorId\":" + proveedorB + "}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("No encontramos ese proveedor"));

		Long id = crear("Navajas", "2026-11-20");
		patchCon(token, URL + "/" + id, "{\"categoriaId\":" + categoriaB + "}").andExpect(status().isNotFound());
		patchCon(token, URL + "/" + id, "{\"proveedorId\":" + proveedorB + "}").andExpect(status().isNotFound());

		assertThat(jdbc.queryForMap("SELECT categoria_id, proveedor_id FROM gastos WHERE id = ?", id))
				.containsEntry("categoria_id", categoria)
				.containsEntry("proveedor_id", null);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM gastos", Integer.class)).isOne();
	}

	@Test
	void sinBarberiaNoEntra() throws Exception {
		String sinBarberia = tokenDe(insertarUsuario("c@mail.com"));

		getCon(sinBarberia, NOVIEMBRE).andExpect(status().isForbidden());
		getCon(sinBarberia, "/api/v1/proveedores").andExpect(status().isForbidden());
		getCon(sinBarberia, "/api/v1/categorias-gasto").andExpect(status().isForbidden());
	}

	private String cuerpo(String descripcion, String monto, String fecha) {
		return "{\"descripcion\":\"%s\",\"monto\":%s,\"fecha\":\"%s\",\"categoriaId\":%d}"
				.formatted(descripcion, monto, fecha, categoria);
	}

	private Long crear(String descripcion, String fecha) throws Exception {
		return crearCon(cuerpo(descripcion, "1000", fecha));
	}

	private Long crearCon(String json) throws Exception {
		String respuesta = postCon(token, URL, json)
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
	}

	private Long crearCategoria(String token, String nombre) throws Exception {
		String respuesta = postCon(token, "/api/v1/categorias-gasto", "{\"nombre\":\"%s\"}".formatted(nombre))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
	}

	private Long crearProveedor(String token, String nombre) throws Exception {
		String respuesta = postCon(token, "/api/v1/proveedores", "{\"nombre\":\"%s\"}".formatted(nombre))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
	}

}
