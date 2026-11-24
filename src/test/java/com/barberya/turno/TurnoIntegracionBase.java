package com.barberya.turno;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import com.barberya.ApiIntegracionTest;
import com.barberya.RelojDeTest;
import com.jayway.jsonpath.JsonPath;

/**
 * Barbería A con horario y un servicio, y el reloj fijo en el lunes 16/11/2026 a las 09:00 de Buenos Aires.
 * Anticipación mínima por defecto (60 min): el primer inicio reservable del lunes es a las 10:00.
 */
abstract class TurnoIntegracionBase extends ApiIntegracionTest {

	static final ZoneOffset ARG = ZoneOffset.ofHours(-3);
	static final Instant AHORA = Instant.parse("2026-11-16T12:00:00Z");
	static final LocalDate LUNES = LocalDate.of(2026, 11, 16);
	static final LocalDate MARTES = LUNES.plusDays(1);
	static final LocalDate MIERCOLES = LUNES.plusDays(2);

	/** Lunes y miércoles de 09:00 a 13:00. */
	static final String HORARIO = """
			[{"diaSemana":1,"horaInicio":"09:00","horaFin":"13:00"},
			 {"diaSemana":3,"horaInicio":"09:00","horaFin":"13:00"}]
			""";

	@Autowired
	protected RelojDeTest reloj;

	protected String tokenA;
	protected Long barberiaA;
	protected Long servicioA;

	@BeforeEach
	void prepararBarberiaA() throws Exception {
		reloj.fijar(AHORA);
		tokenA = duenoConBarberia("a@mail.com", "Barbería A");
		putCon(tokenA, "/api/v1/horarios", HORARIO).andExpect(status().isOk());
		barberiaA = barberiaIdDeSlug("barberia-a");
		servicioA = insertarServicio(barberiaA, "Corte", 30, "5000.00", true);
	}

	@AfterEach
	void relojReal() {
		reloj.usarReal();
	}

	/** Usuario con nombre, apellido y celular: puede reservar. */
	protected Long clienteConPerfil(String email) {
		Long id = insertarUsuario(email);
		jdbc.update("UPDATE usuarios SET nombre = 'Ana', apellido = 'Gómez', telefono = '1155556666' WHERE id = ?", id);
		return id;
	}

	protected Long insertarServicio(Long barberiaId, String nombre, int duracionMin, String precio, boolean activo) {
		return jdbc.queryForObject("""
				INSERT INTO servicios (barberia_id, nombre, duracion_min, precio, activo)
				VALUES (?, ?, ?, ?::numeric, ?) RETURNING id
				""", Long.class, barberiaId, nombre, duracionMin, precio, activo);
	}

	protected ResultActions reservar(String token, String slug, Long servicioId, LocalDate dia, String hora)
			throws Exception {
		return postCon(token, "/api/v1/barberias/" + slug + "/turnos", """
				{"servicioId":%d,"inicio":"%s"}
				""".formatted(servicioId, offset(dia, hora)));
	}

	/** Reserva en la barbería A y devuelve el id del turno. */
	protected Long reservarOk(String token, LocalDate dia, String hora) throws Exception {
		String json = reservar(token, "barberia-a", servicioA, dia, hora)
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(json, "$.id")).longValue();
	}

	protected Long contar(String sql, Object... args) {
		return jdbc.queryForObject(sql, Long.class, args);
	}

	static OffsetDateTime offset(LocalDate dia, String hora) {
		return OffsetDateTime.of(dia, LocalTime.parse(hora), ARG);
	}

	static Instant instante(LocalDate dia, String hora) {
		return offset(dia, hora).toInstant();
	}

}
