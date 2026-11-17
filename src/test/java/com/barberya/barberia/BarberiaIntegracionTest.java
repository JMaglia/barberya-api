package com.barberya.barberia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import com.barberya.ApiIntegracionTest;
import com.jayway.jsonpath.JsonPath;

class BarberiaIntegracionTest extends ApiIntegracionTest {

	@Autowired
	private JwtDecoder jwtDecoder;

	// --- POST /barberias ---

	@Test
	void crearDevuelveLaBarberiaYUnTokenConBarberiaIdYRol() throws Exception {
		Long usuarioId = insertarUsuario("juan@mail.com");
		String respuesta = crearBarberia(tokenDe(usuarioId), """
				{"nombre":"Lo de Juan","telefono":"+54 11 2233-4455","direccion":"Av. Siempre Viva 742"}
				""")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.barberia.nombre").value("Lo de Juan"))
				.andExpect(jsonPath("$.barberia.slug").value("lo-de-juan"))
				.andExpect(jsonPath("$.barberia.zonaHoraria").value("America/Argentina/Buenos_Aires"))
				.andExpect(jsonPath("$.barberia.cancelarHastaHoras").value(24))
				.andExpect(jsonPath("$.token.tipo").value("Bearer"))
				.andReturn().getResponse().getContentAsString();

		Long barberiaId = barberiaIdDeSlug("lo-de-juan");
		assertThat(jdbc.queryForMap("SELECT usuario_id, rol, atiende_turnos, activo FROM miembros_barberia WHERE barberia_id = ?",
				barberiaId))
				.containsEntry("usuario_id", usuarioId)
				.containsEntry("rol", "DUENO")
				.containsEntry("atiende_turnos", true)
				.containsEntry("activo", true);

		String tokenNuevo = JsonPath.read(respuesta, "$.token.accessToken");
		Jwt jwt = jwtDecoder.decode(tokenNuevo);
		assertThat(((Number) jwt.getClaim("barberiaId")).longValue()).isEqualTo(barberiaId);
		assertThat(jwt.getClaimAsString("rol")).isEqualTo("DUENO");
		getCon(tokenNuevo, "/api/v1/barberia")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(barberiaId));
	}

	@Test
	void slugGeneradoSinTildesYConSufijoSiSeRepite() throws Exception {
		crearBarberia(tokenDe(insertarUsuario("a@mail.com")), """
				{"nombre":"Peluquería Ñandú","telefono":"1122334455"}
				""").andExpect(status().isCreated()).andExpect(jsonPath("$.barberia.slug").value("peluqueria-nandu"));

		crearBarberia(tokenDe(insertarUsuario("b@mail.com")), """
				{"nombre":"Peluqueria Nandu","telefono":"1122334455"}
				""").andExpect(status().isCreated()).andExpect(jsonPath("$.barberia.slug").value("peluqueria-nandu-2"));
	}

	@Test
	void slugExplicitoOcupadoDa409() throws Exception {
		duenoConBarberia("a@mail.com", "Lo de Juan");

		crearBarberia(tokenDe(insertarUsuario("b@mail.com")), """
				{"nombre":"Otra","telefono":"1122334455","slug":"lo-de-juan"}
				""")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("Ese link ya está en uso"));
	}

	@Test
	void slugReservadoDa409() throws Exception {
		crearBarberia(tokenDe(insertarUsuario("a@mail.com")), """
				{"nombre":"Mi barbería","telefono":"1122334455","slug":"agenda"}
				""")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("Ese link no está disponible"));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM barberias", Integer.class)).isZero();
	}

	@Test
	void slugConFormatoInvalidoDa400() throws Exception {
		String token = tokenDe(insertarUsuario("a@mail.com"));
		for (String slug : new String[] { "Mi_Slug", "ab", "-juan", "juan--perez" }) {
			crearBarberia(token, """
					{"nombre":"Mi barbería","telefono":"1122334455","slug":"%s"}
					""".formatted(slug))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errores.slug").exists());
		}
	}

	@Test
	void zonaHorariaInvalidaDa400YLaValidaSeGuarda() throws Exception {
		crearBarberia(tokenDe(insertarUsuario("a@mail.com")), """
				{"nombre":"Mi barbería","telefono":"1122334455","zonaHoraria":"Marte/Olympus"}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.zonaHoraria").value("La zona horaria no es válida"));

		crearBarberia(tokenDe(insertarUsuario("b@mail.com")), """
				{"nombre":"Mi barbería","telefono":"1122334455","zonaHoraria":"America/Argentina/Cordoba"}
				""")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.barberia.zonaHoraria").value("America/Argentina/Cordoba"));
	}

	@Test
	void validaNombreYTelefono() throws Exception {
		String token = tokenDe(insertarUsuario("a@mail.com"));
		crearBarberia(token, """
				{"nombre":" ","telefono":""}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.nombre").value("Ingresá el nombre de la barbería"))
				.andExpect(jsonPath("$.errores.telefono").value("Ingresá el teléfono de la barbería"));
		crearBarberia(token, """
				{"nombre":"Mi barbería","telefono":"abc"}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.telefono").value("El celular no es válido"));
	}

	@Test
	void crearDosVecesDa409() throws Exception {
		Long usuarioId = insertarUsuario("a@mail.com");
		String tokenViejo = tokenDe(usuarioId);
		crearBarberia(tokenViejo, """
				{"nombre":"Primera","telefono":"1122334455"}
				""").andExpect(status().isCreated());

		// Con el token viejo (sin barberiaId) y con uno nuevo: igual ya es miembro.
		for (String token : new String[] { tokenViejo, tokenDe(usuarioId) }) {
			crearBarberia(token, """
					{"nombre":"Segunda","telefono":"1122334455"}
					""")
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.detail").value("Ya tenés una barbería"));
		}
		assertThat(jdbc.queryForObject("SELECT count(*) FROM barberias", Integer.class)).isOne();
	}

	@Test
	void sinTokenDa401() throws Exception {
		mvc.perform(get("/api/v1/barberia")).andExpect(status().isUnauthorized());
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/barberias")
				.contentType("application/json").content("{\"nombre\":\"X\",\"telefono\":\"1122334455\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void usuarioSinBarberiaRecibe403ConMensajeClaro() throws Exception {
		String token = tokenDe(insertarUsuario("cliente@mail.com"));

		for (String url : new String[] { "/api/v1/barberia", "/api/v1/servicios" }) {
			getCon(token, url)
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.detail").value("Primero creá tu barbería"));
		}
		postCon(token, "/api/v1/servicios", """
				{"nombre":"Corte","duracionMin":30,"precio":5000}
				""")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail").value("Primero creá tu barbería"));
	}

	// --- GET / PATCH /barberia ---

	@Test
	void patchActualizaSoloLoQueViene() throws Exception {
		String token = duenoConBarberia("a@mail.com", "Lo de Juan");

		patchCon(token, "/api/v1/barberia", """
				{"nombre":"Lo de Juan Barber","slug":"juan-barber","zonaHoraria":"America/Montevideo",
				 "cancelarHastaHoras":12,"reservaMaxDias":60,"pausaEntreTurnosMin":10,"direccion":"Calle 1"}
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("Lo de Juan Barber"))
				.andExpect(jsonPath("$.slug").value("juan-barber"))
				.andExpect(jsonPath("$.telefono").value("1122334455"))
				.andExpect(jsonPath("$.zonaHoraria").value("America/Montevideo"))
				.andExpect(jsonPath("$.cancelarHastaHoras").value(12))
				.andExpect(jsonPath("$.reservaMaxDias").value(60))
				.andExpect(jsonPath("$.pausaEntreTurnosMin").value(10))
				.andExpect(jsonPath("$.direccion").value("Calle 1"));

		// La dirección vacía la borra; el resto queda.
		patchCon(token, "/api/v1/barberia", """
				{"direccion":""}
				""").andExpect(status().isOk()).andExpect(jsonPath("$.direccion").isEmpty());
		getCon(token, "/api/v1/barberia")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.slug").value("juan-barber"))
				.andExpect(jsonPath("$.reservaMaxDias").value(60));
	}

	@Test
	void patchValidaRangosYCampos() throws Exception {
		String token = duenoConBarberia("a@mail.com", "Lo de Juan");

		patchCon(token, "/api/v1/barberia", """
				{"reservaMaxDias":0,"cancelarHastaHoras":500,"pausaEntreTurnosMin":-5,"nombre":"  ",
				 "telefono":"12","zonaHoraria":"GMT-3x","slug":"MAL"}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.reservaMaxDias").exists())
				.andExpect(jsonPath("$.errores.cancelarHastaHoras").exists())
				.andExpect(jsonPath("$.errores.pausaEntreTurnosMin").exists())
				.andExpect(jsonPath("$.errores.nombre").exists())
				.andExpect(jsonPath("$.errores.telefono").exists())
				.andExpect(jsonPath("$.errores.zonaHoraria").exists())
				.andExpect(jsonPath("$.errores.slug").exists());
	}

	@Test
	void patchConSlugDeOtraBarberiaOReservadoDa409YConElPropioNo() throws Exception {
		duenoConBarberia("b@mail.com", "Barbería B");
		String tokenA = duenoConBarberia("a@mail.com", "Barbería A");

		patchCon(tokenA, "/api/v1/barberia", "{\"slug\":\"barberia-b\"}").andExpect(status().isConflict());
		patchCon(tokenA, "/api/v1/barberia", "{\"slug\":\"login\"}").andExpect(status().isConflict());
		patchCon(tokenA, "/api/v1/barberia", "{\"slug\":\"barberia-a\"}").andExpect(status().isOk());
	}

	@Test
	void patchDeANoTocaBAunqueMandeSuId() throws Exception {
		duenoConBarberia("b@mail.com", "Barbería B");
		Long idB = barberiaIdDeSlug("barberia-b");
		String tokenA = duenoConBarberia("a@mail.com", "Barbería A");
		Long idA = barberiaIdDeSlug("barberia-a");

		patchCon(tokenA, "/api/v1/barberia", """
				{"id":%d,"barberiaId":%d,"nombre":"Hackeada","reservaMaxDias":99}
				""".formatted(idB, idB))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(idA))
				.andExpect(jsonPath("$.nombre").value("Hackeada"));

		assertThat(jdbc.queryForMap("SELECT nombre, reserva_max_dias FROM barberias WHERE id = ?", idB))
				.containsEntry("nombre", "Barbería B")
				.containsEntry("reserva_max_dias", 30);
	}

	@Test
	void getDevuelveSoloLaBarberiaDelToken() throws Exception {
		duenoConBarberia("b@mail.com", "Barbería B");
		String tokenA = duenoConBarberia("a@mail.com", "Barbería A");

		getCon(tokenA, "/api/v1/barberia?id=" + barberiaIdDeSlug("barberia-b"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.slug").value("barberia-a"));
	}

}
