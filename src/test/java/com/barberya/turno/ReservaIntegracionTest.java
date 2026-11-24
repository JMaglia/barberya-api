package com.barberya.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

class ReservaIntegracionTest extends TurnoIntegracionBase {

	private static final String HORARIO_NO_DISPONIBLE = "Ese horario ya no está disponible";

	// --- Caso feliz ---

	@Test
	void reservaFelizCreaElTurnoYLaFicha() throws Exception {
		Long ana = clienteConPerfil("ana@mail.com");
		String token = tokenDe(ana);

		String json = reservar(token, "barberia-a", servicioA, MIERCOLES, "10:00")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.inicio").value("2026-11-18T10:00:00-03:00"))
				.andExpect(jsonPath("$.fin").value("2026-11-18T10:30:00-03:00"))
				.andExpect(jsonPath("$.estado").value("CONFIRMADO"))
				.andExpect(jsonPath("$.precio").value(5000.0))
				.andExpect(jsonPath("$.servicio.nombre").value("Corte"))
				.andExpect(jsonPath("$.servicio.duracionMin").value(30))
				.andExpect(jsonPath("$.barberia.nombre").value("Barbería A"))
				.andExpect(jsonPath("$.barberia.slug").value("barberia-a"))
				.andExpect(jsonPath("$.barberia.telefono").value("1122334455"))
				.andExpect(jsonPath("$.cancelableHasta").value("2026-11-17T10:00:00-03:00"))
				.andReturn().getResponse().getContentAsString();
		assertThat(json).doesNotContain("miembro", "cliente", "usuario");

		Map<String, Object> turno = jdbc.queryForMap("SELECT * FROM turnos");
		assertThat(turno.get("precio")).isEqualTo(new BigDecimal("5000.00"));
		assertThat(turno.get("estado")).isEqualTo("CONFIRMADO");
		assertThat(turno.get("miembro_id")).isEqualTo(miembroIdDeSlug("barberia-a"));
		assertThat(turno.get("barberia_id")).isEqualTo(barberiaA);
		assertThat(contar("SELECT count(*) FROM turnos WHERE inicio = ? AND fin = ?", offset(MIERCOLES, "10:00"),
				offset(MIERCOLES, "10:30"))).isOne();

		Map<String, Object> ficha = jdbc.queryForMap("SELECT * FROM clientes");
		assertThat(ficha.get("id")).isEqualTo(turno.get("cliente_id"));
		assertThat(ficha.get("barberia_id")).isEqualTo(barberiaA);
		assertThat(ficha.get("usuario_id")).isEqualTo(ana);
		assertThat(ficha.get("nombre")).isEqualTo("Ana Gómez");
		assertThat(ficha.get("telefono")).isEqualTo("1155556666");
		assertThat(ficha.get("email")).isEqualTo("ana@mail.com");
		assertThat(ficha.get("activo")).isEqualTo(true);

		// La segunda reserva reusa la ficha.
		reservarOk(token, MIERCOLES, "11:00");
		assertThat(contar("SELECT count(*) FROM clientes")).isOne();
		assertThat(contar("SELECT count(DISTINCT cliente_id) FROM turnos")).isOne();
		assertThat(contar("SELECT count(*) FROM turnos")).isEqualTo(2);
	}

	@Test
	void elPrecioDelTurnoNoCambiaSiDespuesSeEditaElServicio() throws Exception {
		String token = tokenDe(clienteConPerfil("ana@mail.com"));
		Long turno = reservarOk(token, MIERCOLES, "10:00");

		patchCon(tokenA, "/api/v1/servicios/" + servicioA, "{\"precio\":9000}").andExpect(status().isOk());

		assertThat(jdbc.queryForObject("SELECT precio FROM servicios WHERE id = ?", BigDecimal.class, servicioA))
				.isEqualByComparingTo("9000");
		assertThat(jdbc.queryForObject("SELECT precio FROM turnos WHERE id = ?", BigDecimal.class, turno))
				.isEqualByComparingTo("5000");
	}

	@Test
	void unaFichaInactivaSeReactivaSinPisarLoQueCargoElBarbero() throws Exception {
		Long ana = clienteConPerfil("ana@mail.com");
		jdbc.update("""
				INSERT INTO clientes (barberia_id, usuario_id, nombre, notas, activo)
				VALUES (?, ?, 'Anita', 'Prefiere tijera', FALSE)
				""", barberiaA, ana);

		reservarOk(tokenDe(ana), MIERCOLES, "10:00");

		Map<String, Object> ficha = jdbc.queryForMap("SELECT * FROM clientes");
		assertThat(contar("SELECT count(*) FROM clientes")).isOne();
		assertThat(ficha.get("activo")).isEqualTo(true);
		assertThat(ficha.get("nombre")).isEqualTo("Anita");
		assertThat(ficha.get("notas")).isEqualTo("Prefiere tijera");
		// Los datos de contacto vacíos se completan.
		assertThat(ficha.get("telefono")).isEqualTo("1155556666");
		assertThat(ficha.get("email")).isEqualTo("ana@mail.com");
	}

	@Test
	void elDuenoDeOtraBarberiaPuedeReservarComoCliente() throws Exception {
		String tokenB = duenoConBarberia("b@mail.com", "Barbería B");
		jdbc.update("UPDATE usuarios SET telefono = '1177778888' WHERE email = 'b@mail.com'");

		reservar(tokenB, "barberia-a", servicioA, MIERCOLES, "10:00").andExpect(status().isCreated());
	}

	// --- Login, validación y perfil ---

	@Test
	void sinTokenDa401() throws Exception {
		mvc.perform(post("/api/v1/barberias/barberia-a/turnos").contentType(MediaType.APPLICATION_JSON)
				.content("{\"servicioId\":%d,\"inicio\":\"%s\"}".formatted(servicioA, offset(MIERCOLES, "10:00"))))
				.andExpect(status().isUnauthorized());
		assertThat(contar("SELECT count(*) FROM turnos")).isZero();
	}

	@Test
	void faltanDatosDa400() throws Exception {
		String token = tokenDe(clienteConPerfil("ana@mail.com"));

		postCon(token, "/api/v1/barberias/barberia-a/turnos", "{}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.servicioId").value("Elegí un servicio"))
				.andExpect(jsonPath("$.errores.inicio").value("Elegí un horario"));
		postCon(token, "/api/v1/barberias/barberia-a/turnos",
				"{\"servicioId\":%d,\"inicio\":\"mañana\"}".formatted(servicioA))
				.andExpect(status().isBadRequest());
	}

	@Test
	void perfilIncompletoDa409() throws Exception {
		Long sinCelular = insertarUsuario("ana@mail.com");
		reservar(tokenDe(sinCelular), "barberia-a", servicioA, MIERCOLES, "10:00")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("PERFIL_INCOMPLETO"))
				.andExpect(jsonPath("$.detail").value("Completá tu nombre y celular para reservar"));

		Long sinNombre = clienteConPerfil("beto@mail.com");
		jdbc.update("UPDATE usuarios SET nombre = NULL WHERE id = ?", sinNombre);
		reservar(tokenDe(sinNombre), "barberia-a", servicioA, MIERCOLES, "10:00")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("PERFIL_INCOMPLETO"));

		assertThat(contar("SELECT count(*) FROM clientes")).isZero();
	}

	@Test
	void elDuenoNoReservaEnSuPropiaBarberia() throws Exception {
		jdbc.update("UPDATE usuarios SET telefono = '1177778888' WHERE email = 'a@mail.com'");

		reservar(tokenA, "barberia-a", servicioA, MIERCOLES, "10:00")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Para cargar turnos en tu barbería usá la agenda"));
		assertThat(contar("SELECT count(*) FROM clientes")).isZero();
	}

	// --- Aislamiento y 404 ---

	@Test
	void servicioInactivoDeOtraBarberiaOInexistenteDa404() throws Exception {
		String token = tokenDe(clienteConPerfil("ana@mail.com"));
		Long inactivo = insertarServicio(barberiaA, "Barba", 30, "3000.00", false);
		duenoConBarberia("b@mail.com", "Barbería B");
		Long deB = insertarServicio(barberiaIdDeSlug("barberia-b"), "Corte B", 30, "4000.00", true);

		for (Long id : List.of(inactivo, deB, 999_999L)) {
			reservar(token, "barberia-a", id, MIERCOLES, "10:00")
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.detail").value("No encontramos ese servicio"));
		}
		assertThat(contar("SELECT count(*) FROM turnos")).isZero();
	}

	@Test
	void barberiaSuspendidaOInexistenteDa404() throws Exception {
		String token = tokenDe(clienteConPerfil("ana@mail.com"));
		reservar(token, "no-existe", servicioA, MIERCOLES, "10:00").andExpect(status().isNotFound());

		jdbc.update("UPDATE barberias SET activa = FALSE WHERE id = ?", barberiaA);
		reservar(token, "barberia-a", servicioA, MIERCOLES, "10:00")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("No encontramos esa barbería"));
	}

	// --- Horario fuera de la disponibilidad ---

	@Test
	void horarioOcupadoDa409() throws Exception {
		reservarOk(tokenDe(clienteConPerfil("ana@mail.com")), MIERCOLES, "10:00");

		String beto = tokenDe(clienteConPerfil("beto@mail.com"));
		esperarNoDisponible(reservar(beto, "barberia-a", servicioA, MIERCOLES, "10:00"));
		esperarNoDisponible(reservar(beto, "barberia-a", servicioA, MIERCOLES, "09:45"));
		assertThat(contar("SELECT count(*) FROM turnos")).isOne();
	}

	@Test
	void horarioFueraDeFranjaBloqueadoOFueraDeGrillaDa409() throws Exception {
		String token = tokenDe(clienteConPerfil("ana@mail.com"));
		jdbc.update("INSERT INTO bloqueos_agenda (miembro_id, inicio, fin) VALUES (?, ?, ?)",
				miembroIdDeSlug("barberia-a"), offset(MIERCOLES, "11:00"), offset(MIERCOLES, "12:00"));

		esperarNoDisponible(reservar(token, "barberia-a", servicioA, MIERCOLES, "14:00"));
		esperarNoDisponible(reservar(token, "barberia-a", servicioA, MIERCOLES, "12:45"));
		esperarNoDisponible(reservar(token, "barberia-a", servicioA, MARTES, "10:00"));
		esperarNoDisponible(reservar(token, "barberia-a", servicioA, MIERCOLES, "11:00"));
		esperarNoDisponible(reservar(token, "barberia-a", servicioA, MIERCOLES, "10:07"));
		assertThat(contar("SELECT count(*) FROM turnos")).isZero();
	}

	@Test
	void horarioPasadoOConPocaAnticipacionDa409() throws Exception {
		String token = tokenDe(clienteConPerfil("ana@mail.com"));

		esperarNoDisponible(reservar(token, "barberia-a", servicioA, MIERCOLES.minusDays(7), "10:00"));
		esperarNoDisponible(reservar(token, "barberia-a", servicioA, LUNES, "09:00"));
		// A 59 minutos no; a 60 sí.
		reloj.fijar(AHORA.plusSeconds(60));
		esperarNoDisponible(reservar(token, "barberia-a", servicioA, LUNES, "10:00"));
		reloj.fijar(AHORA);
		reservar(token, "barberia-a", servicioA, LUNES, "10:00").andExpect(status().isCreated());
	}

	@Test
	void masAllaDeReservaMaxDiasDa409() throws Exception {
		String token = tokenDe(clienteConPerfil("ana@mail.com"));
		// Por defecto 30 días: el miércoles 16/12 entra, el 23/12 no.
		reservar(token, "barberia-a", servicioA, MIERCOLES.plusWeeks(4), "10:00").andExpect(status().isCreated());
		esperarNoDisponible(reservar(token, "barberia-a", servicioA, MIERCOLES.plusWeeks(5), "10:00"));
	}

	@Test
	void dosClientesReservanElMismoHorarioALaVez() throws Exception {
		List<String> tokens = List.of(tokenDe(clienteConPerfil("ana@mail.com")),
				tokenDe(clienteConPerfil("beto@mail.com")));
		CountDownLatch largada = new CountDownLatch(1);
		ExecutorService hilos = Executors.newFixedThreadPool(2);
		try {
			List<Future<Integer>> resultados = new ArrayList<>();
			for (String token : tokens) {
				resultados.add(hilos.submit(() -> {
					largada.await();
					return reservar(token, "barberia-a", servicioA, MIERCOLES, "10:00").andReturn().getResponse()
							.getStatus();
				}));
			}
			largada.countDown();
			List<Integer> estados = new ArrayList<>();
			for (Future<Integer> r : resultados) {
				estados.add(r.get(30, TimeUnit.SECONDS));
			}
			Collections.sort(estados);
			assertThat(estados).containsExactly(201, 409);
		}
		finally {
			hilos.shutdownNow();
		}
		assertThat(contar("SELECT count(*) FROM turnos")).isOne();
	}

	// --- Tope de turnos futuros ---

	@Test
	void elQuintoTurnoFuturoDa409() throws Exception {
		String token = tokenDe(clienteConPerfil("ana@mail.com"));
		for (String hora : List.of("10:00", "10:30", "11:00", "11:30")) {
			reservarOk(token, MIERCOLES, hora);
		}

		reservar(token, "barberia-a", servicioA, MIERCOLES, "12:00")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("TOPE_TURNOS"))
				.andExpect(jsonPath("$.detail").value("Ya tenés 4 turnos reservados en esta barbería"));
		assertThat(contar("SELECT count(*) FROM turnos")).isEqualTo(4);
	}

	@Test
	void losTurnosCanceladosOPasadosNoCuentanParaElTope() throws Exception {
		String token = tokenDe(clienteConPerfil("ana@mail.com"));
		Long primero = reservarOk(token, MIERCOLES, "10:00");
		Long ficha = jdbc.queryForObject("SELECT cliente_id FROM turnos WHERE id = ?", Long.class, primero);
		Long miembro = miembroIdDeSlug("barberia-a");
		insertarTurno(ficha, miembro, MIERCOLES.minusDays(7), "10:00", "CONFIRMADO");
		insertarTurno(ficha, miembro, LUNES, "09:00", "CONFIRMADO");
		postCon(token, "/api/v1/mis-turnos/" + primero + "/cancelar").andExpect(status().isOk());

		for (String hora : List.of("10:00", "10:30", "11:00", "11:30")) {
			reservarOk(token, MIERCOLES, hora);
		}
		reservar(token, "barberia-a", servicioA, MIERCOLES, "12:00")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("TOPE_TURNOS"));
	}

	@Test
	void conElTopeEnUnaBarberiaPuedeReservarEnOtra() throws Exception {
		String token = tokenDe(clienteConPerfil("ana@mail.com"));
		for (String hora : List.of("10:00", "10:30", "11:00", "11:30")) {
			reservarOk(token, MIERCOLES, hora);
		}
		String tokenB = duenoConBarberia("b@mail.com", "Barbería B");
		putCon(tokenB, "/api/v1/horarios", HORARIO).andExpect(status().isOk());
		Long servicioB = insertarServicio(barberiaIdDeSlug("barberia-b"), "Corte", 30, "4000.00", true);

		String json = reservar(token, "barberia-b", servicioB, MIERCOLES, "10:00")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.precio").value(4000.0))
				.andReturn().getResponse().getContentAsString();
		assertThat((String) JsonPath.read(json, "$.barberia.slug")).isEqualTo("barberia-b");
		assertThat(contar("SELECT count(*) FROM clientes WHERE usuario_id IS NOT NULL")).isEqualTo(2);
	}

	// ---------------------------------------------------------------------------------------------------------

	private static void esperarNoDisponible(org.springframework.test.web.servlet.ResultActions resultado)
			throws Exception {
		resultado.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("HORARIO_NO_DISPONIBLE"))
				.andExpect(jsonPath("$.detail").value(HORARIO_NO_DISPONIBLE));
	}

	private void insertarTurno(Long clienteId, Long miembroId, java.time.LocalDate dia, String hora, String estado) {
		jdbc.update("""
				INSERT INTO turnos (barberia_id, miembro_id, cliente_id, servicio_id, inicio, fin, estado, precio)
				VALUES (?, ?, ?, ?, ?, ?, ?, 5000)
				""", barberiaA, miembroId, clienteId, servicioA, offset(dia, hora), offset(dia, hora).plusMinutes(30),
				estado);
	}

}
