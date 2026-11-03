package com.barberya;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifica contra un Postgres real que la migración V1 aplica y que la base hace cumplir sus reglas.
 * Sin {@code @Transactional}: en Postgres un error aborta la transacción, así que cada test limpia antes de correr.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MigracionV1Test {

	private static final String EXCLUSION_VIOLATION = "23P01";
	private static final String FOREIGN_KEY_VIOLATION = "23503";
	private static final String UNIQUE_VIOLATION = "23505";
	private static final String CHECK_VIOLATION = "23514";

	private static final Instant LAS_10 = Instant.parse("2026-12-01T13:00:00Z");
	private static final Instant LAS_10_15 = Instant.parse("2026-12-01T13:15:00Z");
	private static final Instant LAS_10_30 = Instant.parse("2026-12-01T13:30:00Z");
	private static final Instant LAS_10_45 = Instant.parse("2026-12-01T13:45:00Z");
	private static final Instant LAS_11 = Instant.parse("2026-12-01T14:00:00Z");

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void limpiarBase() {
		jdbc.execute("TRUNCATE barberias, usuarios, planes RESTART IDENTITY CASCADE");
	}

	@Test
	void laMigracionAplicaYCreaLasTablasDeV1() {
		Boolean exitosa = jdbc.queryForObject(
				"SELECT success FROM flyway_schema_history WHERE version = '1'", Boolean.class);
		assertThat(exitosa).isTrue();

		List<String> tablas = jdbc.queryForList("""
				SELECT table_name FROM information_schema.tables
				WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
				  AND table_name <> 'flyway_schema_history'
				""", String.class);
		assertThat(tablas).containsExactlyInAnyOrder(
				"planes", "barberias", "suscripciones", "pagos_suscripcion",
				"usuarios", "refresh_tokens", "tokens_verificacion", "miembros_barberia", "clientes",
				"servicios", "horarios_atencion", "bloqueos_agenda", "turnos",
				"cobros", "categorias_gasto", "gastos_fijos", "gastos");

		Integer btreeGist = jdbc.queryForObject(
				"SELECT count(*) FROM pg_extension WHERE extname = 'btree_gist'", Integer.class);
		assertThat(btreeGist).isEqualTo(1);
	}

	@Test
	void dosTurnosSuperpuestosDelMismoBarberoSeRechazan() {
		Barberia barberia = crearBarberiaCompleta("uno");
		insertarTurno(barberia, barberia.clienteId(), LAS_10, LAS_10_30);

		assertRechazo(() -> insertarTurno(barberia, barberia.clienteId(), LAS_10_15, LAS_10_45),
				EXCLUSION_VIOLATION, "turnos_sin_superposicion");
	}

	@Test
	void dosTurnosContiguosSeAceptan() {
		Barberia barberia = crearBarberiaCompleta("uno");

		insertarTurno(barberia, barberia.clienteId(), LAS_10, LAS_10_30);
		insertarTurno(barberia, barberia.clienteId(), LAS_10_30, LAS_11);

		assertThat(contarTurnos()).isEqualTo(2);
	}

	@Test
	void unTurnoSuperpuestoConUnoCanceladoSeAcepta() {
		Barberia barberia = crearBarberiaCompleta("uno");
		jdbc.update("""
				INSERT INTO turnos (barberia_id, miembro_id, cliente_id, servicio_id, inicio, fin, precio,
				                    estado, cancelado_en, cancelado_por)
				VALUES (?, ?, ?, ?, ?, ?, ?, 'CANCELADO', now(), 'CLIENTE')
				""", barberia.id(), barberia.miembroId(), barberia.clienteId(), barberia.servicioId(),
				Timestamp.from(LAS_10), Timestamp.from(LAS_10_30), new BigDecimal("9900.00"));

		insertarTurno(barberia, barberia.clienteId(), LAS_10_15, LAS_10_45);

		assertThat(contarTurnos()).isEqualTo(2);
	}

	@Test
	void unTurnoConClienteDeOtraBarberiaSeRechaza() {
		Barberia barberia = crearBarberiaCompleta("uno");
		Barberia otra = crearBarberiaCompleta("dos");

		assertRechazo(() -> insertarTurno(barberia, otra.clienteId(), LAS_10, LAS_10_30),
				FOREIGN_KEY_VIOLATION, "turnos_cliente_id_barberia_id_fkey");
	}

	@Test
	void unaSegundaSuscripcionActivaEnLaMismaBarberiaSeRechaza() {
		Long barberiaId = crearBarberia("uno");
		Long planId = insertarDevolviendoId(
				"INSERT INTO planes (nombre, precio_mensual, max_barberos) VALUES ('Independiente', 9900.00, 1) RETURNING id");
		insertarSuscripcionActiva(barberiaId, planId);

		assertRechazo(() -> insertarSuscripcionActiva(barberiaId, planId),
				UNIQUE_VIOLATION, "ux_suscripcion_viva");
	}

	@Test
	void unEmailConMayusculasSeRechaza() {
		assertRechazo(() -> crearUsuario("Juan@Mail.com"),
				CHECK_VIOLATION, "usuarios_email_check");
	}

	@Test
	void borrarUnaBarberiaEliminaTodasSusFilasYNoTocaLasDeOtra() {
		Long planId = insertarDevolviendoId(
				"INSERT INTO planes (nombre, precio_mensual, max_barberos) VALUES ('Independiente', 9900.00, 1) RETURNING id");
		Barberia aBorrar = crearBarberiaConTodosSusDatos("uno", planId);
		Barberia otra = crearBarberiaConTodosSusDatos("dos", planId);
		Map<String, Integer> filasAntes = contarFilasPorTabla(aBorrar.id());
		Map<String, Integer> filasDeLaOtraAntes = contarFilasPorTabla(otra.id());
		assertThat(filasAntes).hasSize(13).allSatisfy((tabla, filas) -> assertThat(filas).as(tabla).isEqualTo(1));
		assertThat(filasDeLaOtraAntes).isEqualTo(filasAntes);

		int borradas = jdbc.update("DELETE FROM barberias WHERE id = ?", aBorrar.id());

		assertThat(borradas).isEqualTo(1);
		assertThat(contarFilasPorTabla(aBorrar.id()))
				.hasSize(13)
				.allSatisfy((tabla, filas) -> assertThat(filas).as(tabla).isZero());
		assertThat(contarFilasPorTabla(otra.id())).isEqualTo(filasDeLaOtraAntes);
		// El total por tabla descarta filas sueltas que los filtros por barbería no llegarían a ver.
		filasAntes.keySet().forEach(tabla -> assertThat(contar("SELECT count(*) FROM " + tabla)).as(tabla).isEqualTo(1));
		// La cuenta del usuario y el plan no son de la barbería: sobreviven al borrado.
		assertThat(contar("SELECT count(*) FROM usuarios")).isEqualTo(2);
		assertThat(contar("SELECT count(*) FROM planes")).isEqualTo(1);
	}

	/** Comprueba el motivo exacto del rechazo, para que un insert mal armado no haga pasar el test. */
	private void assertRechazo(Runnable insert, String sqlState, String restriccion) {
		assertThatThrownBy(insert::run)
				.isInstanceOf(DataAccessException.class)
				.satisfies(error -> {
					PSQLException causa = (PSQLException) ((DataAccessException) error).getMostSpecificCause();
					assertThat(causa.getSQLState()).isEqualTo(sqlState);
					assertThat(causa.getServerErrorMessage().getConstraint()).isEqualTo(restriccion);
				});
	}

	private Barberia crearBarberiaCompleta(String sufijo) {
		Long barberiaId = crearBarberia(sufijo);
		Long usuarioId = crearUsuario("barbero-" + sufijo + "@mail.com");
		Long miembroId = insertarDevolviendoId(
				"INSERT INTO miembros_barberia (barberia_id, usuario_id, rol) VALUES (?, ?, 'DUENO') RETURNING id",
				barberiaId, usuarioId);
		Long clienteId = insertarDevolviendoId(
				"INSERT INTO clientes (barberia_id, nombre) VALUES (?, 'Cliente') RETURNING id", barberiaId);
		Long servicioId = insertarDevolviendoId("""
				INSERT INTO servicios (barberia_id, nombre, duracion_min, precio)
				VALUES (?, 'Corte', 30, 9900.00) RETURNING id
				""", barberiaId);
		return new Barberia(barberiaId, miembroId, clienteId, servicioId);
	}

	private Long crearBarberia(String sufijo) {
		return insertarDevolviendoId(
				"INSERT INTO barberias (nombre, slug) VALUES (?, ?) RETURNING id", "Barberia " + sufijo, "barberia-" + sufijo);
	}

	private Long crearUsuario(String email) {
		return insertarDevolviendoId(
				"INSERT INTO usuarios (email, nombre) VALUES (?, 'Nombre') RETURNING id", email);
	}

	private void insertarTurno(Barberia barberia, Long clienteId, Instant inicio, Instant fin) {
		jdbc.update("""
				INSERT INTO turnos (barberia_id, miembro_id, cliente_id, servicio_id, inicio, fin, precio)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				""", barberia.id(), barberia.miembroId(), clienteId, barberia.servicioId(),
				Timestamp.from(inicio), Timestamp.from(fin), new BigDecimal("9900.00"));
	}

	private void insertarSuscripcionActiva(Long barberiaId, Long planId) {
		jdbc.update("""
				INSERT INTO suscripciones (barberia_id, plan_id, estado, fecha_inicio)
				VALUES (?, ?, 'ACTIVA', ?)
				""", barberiaId, planId, java.sql.Date.valueOf(LocalDate.of(2026, 11, 3)));
	}

	/** Crea exactamente una fila en cada tabla que depende de la barbería. */
	private Barberia crearBarberiaConTodosSusDatos(String sufijo, Long planId) {
		Barberia barberia = crearBarberiaCompleta(sufijo);
		jdbc.update("""
				INSERT INTO horarios_atencion (miembro_id, dia_semana, hora_inicio, hora_fin)
				VALUES (?, 2, '09:00', '18:00')
				""", barberia.miembroId());
		jdbc.update("""
				INSERT INTO bloqueos_agenda (miembro_id, inicio, fin, motivo)
				VALUES (?, ?, ?, 'Vacaciones')
				""", barberia.miembroId(), Timestamp.from(LAS_11), Timestamp.from(LAS_11.plusSeconds(3600)));
		Long turnoId = insertarDevolviendoId("""
				INSERT INTO turnos (barberia_id, miembro_id, cliente_id, servicio_id, inicio, fin, precio, estado)
				VALUES (?, ?, ?, ?, ?, ?, 9900.00, 'COMPLETADO') RETURNING id
				""", barberia.id(), barberia.miembroId(), barberia.clienteId(), barberia.servicioId(),
				Timestamp.from(LAS_10), Timestamp.from(LAS_10_30));
		jdbc.update("INSERT INTO cobros (barberia_id, turno_id, monto) VALUES (?, ?, 9900.00)", barberia.id(), turnoId);
		Long categoriaId = insertarDevolviendoId(
				"INSERT INTO categorias_gasto (barberia_id, nombre) VALUES (?, 'Local') RETURNING id", barberia.id());
		Long gastoFijoId = insertarDevolviendoId("""
				INSERT INTO gastos_fijos (barberia_id, concepto, monto, dia_del_mes)
				VALUES (?, 'Alquiler', 50000.00, 10) RETURNING id
				""", barberia.id());
		jdbc.update("""
				INSERT INTO gastos (barberia_id, categoria_id, gasto_fijo_id, descripcion, monto, fecha, es_fijo)
				VALUES (?, ?, ?, 'Alquiler de noviembre', 50000.00, ?, TRUE)
				""", barberia.id(), categoriaId, gastoFijoId, java.sql.Date.valueOf(LocalDate.of(2026, 11, 10)));
		Long suscripcionId = insertarDevolviendoId("""
				INSERT INTO suscripciones (barberia_id, plan_id, estado, fecha_inicio)
				VALUES (?, ?, 'ACTIVA', ?) RETURNING id
				""", barberia.id(), planId, java.sql.Date.valueOf(LocalDate.of(2026, 11, 3)));
		jdbc.update("""
				INSERT INTO pagos_suscripcion (suscripcion_id, monto, estado, referencia_externa)
				VALUES (?, 9900.00, 'APROBADO', ?)
				""", suscripcionId, "mp-" + sufijo);
		return barberia;
	}

	private Map<String, Integer> contarFilasPorTabla(Long barberiaId) {
		Map<String, Integer> filas = new HashMap<>();
		filas.put("barberias", contar("SELECT count(*) FROM barberias WHERE id = ?", barberiaId));
		List.of("suscripciones", "miembros_barberia", "clientes", "servicios", "turnos", "cobros",
				"categorias_gasto", "gastos_fijos", "gastos")
				.forEach(tabla -> filas.put(tabla,
						contar("SELECT count(*) FROM " + tabla + " WHERE barberia_id = ?", barberiaId)));
		filas.put("horarios_atencion", contar("""
				SELECT count(*) FROM horarios_atencion h
				JOIN miembros_barberia m ON m.id = h.miembro_id WHERE m.barberia_id = ?
				""", barberiaId));
		filas.put("bloqueos_agenda", contar("""
				SELECT count(*) FROM bloqueos_agenda b
				JOIN miembros_barberia m ON m.id = b.miembro_id WHERE m.barberia_id = ?
				""", barberiaId));
		filas.put("pagos_suscripcion", contar("""
				SELECT count(*) FROM pagos_suscripcion p
				JOIN suscripciones s ON s.id = p.suscripcion_id WHERE s.barberia_id = ?
				""", barberiaId));
		return filas;
	}

	private Long insertarDevolviendoId(String sql, Object... parametros) {
		return jdbc.queryForObject(sql, Long.class, parametros);
	}

	private Integer contar(String sql, Object... parametros) {
		return jdbc.queryForObject(sql, Integer.class, parametros);
	}

	private Integer contarTurnos() {
		return contar("SELECT count(*) FROM turnos");
	}

	private record Barberia(Long id, Long miembroId, Long clienteId, Long servicioId) {
	}

}
