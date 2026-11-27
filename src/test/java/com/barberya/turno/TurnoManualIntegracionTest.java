package com.barberya.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

/**
 * POST /api/v1/turnos: el barbero carga un turno a mano. Reloj en el lunes 16/11 a las 09:00; horario lunes y
 * miércoles de 09:00 a 13:00; servicio "Corte" de 30 min a $5000.
 */
class TurnoManualIntegracionTest extends TurnoIntegracionBase {

	private static final String URL = "/api/v1/turnos";

	// --- Cliente ---

	@Test
	void conClienteNuevoCreaLaFichaSinUsuarioYElTurno() throws Exception {
		String json = cargar(tokenA, conClienteNuevo(LUNES, "10:00",
				"""
						{"nombre":" Juan Pérez ","telefono":"11 4444-5555","email":"Juan@Mail.com"}"""),
				"Corte con barba")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.turno.estado").value("CONFIRMADO"))
				.andExpect(jsonPath("$.turno.inicio").value("2026-11-16T10:00:00-03:00"))
				.andExpect(jsonPath("$.turno.fin").value("2026-11-16T10:30:00-03:00"))
				.andExpect(jsonPath("$.turno.precio").value(5000.0))
				.andExpect(jsonPath("$.turno.servicio.nombre").value("Corte"))
				.andExpect(jsonPath("$.turno.cliente.nombre").value("Juan Pérez"))
				.andExpect(jsonPath("$.turno.cliente.telefono").value("11 4444-5555"))
				.andExpect(jsonPath("$.advertencias").isEmpty())
				.andReturn().getResponse().getContentAsString();

		Long turno = ((Number) JsonPath.read(json, "$.turno.id")).longValue();
		Map<String, Object> fila = jdbc.queryForMap("""
				SELECT t.barberia_id, t.miembro_id, t.notas, t.estado, c.barberia_id AS ficha_barberia, c.usuario_id,
				       c.nombre, c.email, c.activo
				FROM turnos t JOIN clientes c ON c.id = t.cliente_id WHERE t.id = ?
				""", turno);
		assertThat(fila.get("barberia_id")).isEqualTo(barberiaA);
		assertThat(fila.get("miembro_id")).isEqualTo(miembroIdDeSlug("barberia-a"));
		assertThat(fila.get("notas")).isEqualTo("Corte con barba");
		assertThat(fila.get("ficha_barberia")).isEqualTo(barberiaA);
		assertThat(fila.get("usuario_id")).isNull();
		assertThat(fila.get("nombre")).isEqualTo("Juan Pérez");
		assertThat(fila.get("email")).isEqualTo("juan@mail.com");
		assertThat(fila.get("activo")).isEqualTo(true);
	}

	@Test
	void conClienteExistenteUsaEsaFicha() throws Exception {
		Long ficha = insertarCliente(barberiaA, "Ana", "1155556666");

		String json = cargar(tokenA, conClienteId(LUNES, "10:00", ficha), null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.turno.cliente.nombre").value("Ana"))
				.andReturn().getResponse().getContentAsString();

		Long turno = ((Number) JsonPath.read(json, "$.turno.id")).longValue();
		assertThat(contar("SELECT cliente_id FROM turnos WHERE id = ?", turno)).isEqualTo(ficha);
		assertThat(contar("SELECT count(*) FROM clientes")).isEqualTo(1);
	}

	@Test
	void unaFichaInactivaSeReactiva() throws Exception {
		Long ficha = insertarCliente(barberiaA, "Ana", null);
		jdbc.update("UPDATE clientes SET activo = FALSE WHERE id = ?", ficha);

		cargar(tokenA, conClienteId(LUNES, "10:00", ficha), null).andExpect(status().isCreated());

		assertThat(jdbc.queryForObject("SELECT activo FROM clientes WHERE id = ?", Boolean.class, ficha)).isTrue();
	}

	@Test
	void sinClienteOConLosDosDa400() throws Exception {
		Long ficha = insertarCliente(barberiaA, "Ana", null);

		postCon(tokenA, URL, """
				{"servicioId":%d,"inicio":"%s"}
				""".formatted(servicioA, offset(LUNES, "10:00")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.cliente").exists());
		postCon(tokenA, URL, """
				{"servicioId":%d,"inicio":"%s","clienteId":%d,"clienteNuevo":{"nombre":"Juan"}}
				""".formatted(servicioA, offset(LUNES, "10:00"), ficha))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.cliente").exists());
		assertThat(contar("SELECT count(*) FROM turnos")).isZero();
	}

	@Test
	void clienteNuevoInvalidoDa400() throws Exception {
		cargar(tokenA, conClienteNuevo(LUNES, "10:00", "{\"nombre\":\" \"}"), null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores['clienteNuevo.nombre']").exists());
		cargar(tokenA, conClienteNuevo(LUNES, "10:00", "{\"nombre\":\"Juan\",\"telefono\":\"abc\"}"), null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores['clienteNuevo.telefono']").value("El celular no es válido"));
		cargar(tokenA, conClienteNuevo(LUNES, "10:00", "{\"nombre\":\"Juan\",\"email\":\"no-es-mail\"}"), null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores['clienteNuevo.email']").exists());
		cargar(tokenA, conClienteNuevo(LUNES, "10:00", "{\"nombre\":\"" + "x".repeat(81) + "\"}"), null)
				.andExpect(status().isBadRequest());
		assertThat(contar("SELECT count(*) FROM clientes")).isZero();
	}

	@Test
	void faltanServicioOInicioDa400() throws Exception {
		postCon(tokenA, URL, "{\"clienteNuevo\":{\"nombre\":\"Juan\"}}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.servicioId").exists())
				.andExpect(jsonPath("$.errores.inicio").exists());
	}

	// --- Aislamiento y servicio ---

	@Test
	void clienteDeOtraBarberiaDa404() throws Exception {
		duenoConBarberia("b@mail.com", "Barbería B");
		Long fichaB = insertarCliente(barberiaIdDeSlug("barberia-b"), "De B", null);

		cargar(tokenA, conClienteId(LUNES, "10:00", fichaB), null).andExpect(status().isNotFound());
		assertThat(contar("SELECT count(*) FROM turnos")).isZero();
	}

	@Test
	void servicioDeOtraBarberiaOInactivoDa404() throws Exception {
		duenoConBarberia("b@mail.com", "Barbería B");
		Long servicioB = insertarServicio(barberiaIdDeSlug("barberia-b"), "Corte B", 30, "4000.00", true);
		Long inactivo = insertarServicio(barberiaA, "Viejo", 30, "1000.00", false);

		for (Long servicio : new Long[] { servicioB, inactivo }) {
			postCon(tokenA, URL, """
					{"servicioId":%d,"inicio":"%s","clienteNuevo":{"nombre":"Juan"}}
					""".formatted(servicio, offset(LUNES, "10:00")))
					.andExpect(status().isNotFound());
		}
		assertThat(contar("SELECT count(*) FROM clientes")).isZero();
	}

	@Test
	void elTurnoDeOtraBarberiaNoOcupaElHorario() throws Exception {
		String tokenB = duenoConBarberia("b@mail.com", "Barbería B");
		insertarServicio(barberiaIdDeSlug("barberia-b"), "Corte B", 30, "4000.00", true);
		cargar(tokenA, nuevo(LUNES, "10:00"), null).andExpect(status().isCreated());
		Long servicioB = contar("SELECT id FROM servicios WHERE nombre = 'Corte B'");

		postCon(tokenB, URL, """
				{"servicioId":%d,"inicio":"%s","clienteNuevo":{"nombre":"Juan"}}
				""".formatted(servicioB, offset(LUNES, "10:00")))
				.andExpect(status().isCreated());
		assertThat(contar("SELECT count(*) FROM clientes WHERE barberia_id = ?", barberiaA)).isEqualTo(1);
	}

	// --- Reglas del cliente que no aplican y advertencias ---

	@Test
	void fueraDeHorarioSeCargaConAdvertencia() throws Exception {
		cargar(tokenA, nuevo(MARTES, "10:00"), null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.advertencias", Matchers.contains("FUERA_DE_HORARIO")));
		// Empieza dentro pero termina después del cierre (13:00).
		cargar(tokenA, nuevo(LUNES, "12:45"), null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.advertencias", Matchers.contains("FUERA_DE_HORARIO")));
	}

	@Test
	void terminarJustoAlCierreNoEsFueraDeHorario() throws Exception {
		cargar(tokenA, nuevo(LUNES, "12:30"), null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.advertencias").isEmpty());
	}

	@Test
	void sobreUnBloqueoSeCargaConAdvertencia() throws Exception {
		postCon(tokenA, "/api/v1/bloqueos", """
				{"inicio":"%s","fin":"%s","motivo":"Trámite"}
				""".formatted(offset(LUNES, "11:00"), offset(LUNES, "12:00"))).andExpect(status().isCreated());

		cargar(tokenA, nuevo(LUNES, "11:15"), null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.advertencias", Matchers.contains("PISA_BLOQUEO")));
		// Contiguo al bloqueo no lo pisa.
		cargar(tokenA, nuevo(LUNES, "10:30"), null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.advertencias").isEmpty());
	}

	@Test
	void sinAnticipacionNiGrillaNiLimiteDeDias() throws Exception {
		// A 10 min de ahora (la anticipación mínima es 60) y fuera de la grilla de 15 min.
		cargar(tokenA, nuevo(LUNES, "09:10"), null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.advertencias").isEmpty());
		// Más allá de reserva_max_dias (30): un miércoles dentro de su horario.
		cargar(tokenA, nuevo(MIERCOLES.plusWeeks(8), "10:00"), null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.advertencias").isEmpty());
	}

	// --- Superposición ---

	@Test
	void pisandoOtroTurnoDa409YNoQuedaLaFichaNueva() throws Exception {
		Long ficha = insertarCliente(barberiaA, "Ana", null);
		insertarTurno(barberiaA, ficha, servicioA, LUNES, "10:00", "10:30", "CONFIRMADO", "5000");

		cargar(tokenA, nuevo(LUNES, "10:15"), null)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("HORARIO_OCUPADO"));
		assertThat(contar("SELECT count(*) FROM clientes")).isEqualTo(1);
		assertThat(contar("SELECT count(*) FROM turnos")).isEqualTo(1);
	}

	@Test
	void contiguoOSobreCanceladosYAusentesSePuede() throws Exception {
		Long ficha = insertarCliente(barberiaA, "Ana", null);
		insertarTurno(barberiaA, ficha, servicioA, LUNES, "10:00", "10:30", "CONFIRMADO", "5000");
		insertarTurno(barberiaA, ficha, servicioA, LUNES, "11:00", "11:30", "CANCELADO", "5000");
		insertarTurno(barberiaA, ficha, servicioA, LUNES, "12:00", "12:30", "AUSENTE", "5000");

		cargar(tokenA, nuevo(LUNES, "10:30"), null).andExpect(status().isCreated());
		cargar(tokenA, nuevo(LUNES, "11:00"), null).andExpect(status().isCreated());
		cargar(tokenA, nuevo(LUNES, "12:00"), null).andExpect(status().isCreated());
	}

	// --- Límites de inicio ---

	@Test
	void unTurnoQueEmpezoHaceDosHorasSeCargaYSePuedeCompletar() throws Exception {
		String json = cargar(tokenA, nuevo(LUNES, "07:00"), null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.advertencias", Matchers.contains("FUERA_DE_HORARIO")))
				.andReturn().getResponse().getContentAsString();
		Long turno = ((Number) JsonPath.read(json, "$.turno.id")).longValue();

		postCon(tokenA, URL + "/" + turno + "/completar")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("COMPLETADO"));
		assertThat(contar("SELECT count(*) FROM cobros WHERE turno_id = ?", turno)).isEqualTo(1);
	}

	@Test
	void haceMasDe24HorasOMasDeUnAnioAdelanteDa400() throws Exception {
		cargar(tokenA, nuevo(LUNES.minusDays(1), "08:00"), null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.inicio").exists());
		cargar(tokenA, nuevo(LUNES.plusDays(366), "10:00"), null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.inicio").exists());
		assertThat(contar("SELECT count(*) FROM clientes")).isZero();
		// Justo 24 h antes todavía vale.
		cargar(tokenA, nuevo(LUNES.minusDays(1), "09:00"), null).andExpect(status().isCreated());
	}

	// --- Permisos ---

	@Test
	void unClienteSinRolDa403() throws Exception {
		String token = tokenDe(clienteConPerfil("ana@mail.com"));

		cargar(token, nuevo(LUNES, "10:00"), null).andExpect(status().isForbidden());
		assertThat(contar("SELECT count(*) FROM turnos")).isZero();
	}

	@Test
	void sinTokenDa401() throws Exception {
		mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body(nuevo(LUNES, "10:00"), null)))
				.andExpect(status().isUnauthorized());
	}

	// ---------------------------------------------------------------------------------------------------------

	private ResultActions cargar(String token, String camposCliente, String notas) throws Exception {
		return postCon(token, URL, body(camposCliente, notas));
	}

	/** Arma el body con el servicio A; {@code camposCliente} trae inicio y cliente. */
	private String body(String camposCliente, String notas) {
		String json = "{\"servicioId\":" + servicioA + "," + camposCliente;
		return (notas == null ? json : json + ",\"notas\":\"" + notas + "\"") + "}";
	}

	private static String nuevo(LocalDate dia, String hora) {
		return conClienteNuevo(dia, hora, "{\"nombre\":\"Juan\"}");
	}

	private static String conClienteNuevo(LocalDate dia, String hora, String clienteNuevo) {
		return "\"inicio\":\"" + offset(dia, hora) + "\",\"clienteNuevo\":" + clienteNuevo;
	}

	private static String conClienteId(LocalDate dia, String hora, Long clienteId) {
		return "\"inicio\":\"" + offset(dia, hora) + "\",\"clienteId\":" + clienteId;
	}

}
