package com.barberya.agenda;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.barberya.turno.TurnoIntegracionBase;

class AgendaIntegracionTest extends TurnoIntegracionBase {

	private static final LocalDate JUEVES = LUNES.plusDays(3);
	private static final LocalDate VIERNES = LUNES.plusDays(4);

	private Long clienteA;

	@BeforeEach
	void ficha() {
		clienteA = insertarCliente(barberiaA, "Juan Pérez", "1144445555");
	}

	@Test
	void devuelveTodosLosDiasConSusTurnosOrdenadosYSusBloqueos() throws Exception {
		// Insertados en desorden.
		Long mie11 = insertarTurno(barberiaA, clienteA, servicioA, MIERCOLES, "11:00", "11:30", "CONFIRMADO", "5000");
		Long lun10 = insertarTurno(barberiaA, clienteA, servicioA, LUNES, "10:30", "11:00", "AUSENTE", "5000");
		Long mie09 = insertarTurno(barberiaA, clienteA, servicioA, MIERCOLES, "09:00", "09:30", "CONFIRMADO", "5000");
		Long lun09 = insertarTurno(barberiaA, clienteA, servicioA, LUNES, "09:30", "10:00", "COMPLETADO", "4500");
		Long miembro = miembroIdDeSlug("barberia-a");
		Long franco = insertarBloqueo(miembro, MARTES, "10:00", MARTES, "12:00", "Trámite");
		Long viaje = insertarBloqueo(miembro, MARTES, "20:00", JUEVES, "08:00", "Viaje");

		agenda(tokenA, LUNES, VIERNES)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.zonaHoraria").value("America/Argentina/Buenos_Aires"))
				.andExpect(jsonPath("$.dias", hasSize(5)))
				.andExpect(jsonPath("$.dias[*].fecha",
						contains("2026-11-16", "2026-11-17", "2026-11-18", "2026-11-19", "2026-11-20")))
				// Lunes: ordenados por inicio; el AUSENTE se muestra.
				.andExpect(jsonPath("$.dias[0].turnos[*].id", contains(lun09.intValue(), lun10.intValue())))
				.andExpect(jsonPath("$.dias[0].turnos[0].inicio").value("2026-11-16T09:30:00-03:00"))
				.andExpect(jsonPath("$.dias[0].turnos[0].fin").value("2026-11-16T10:00:00-03:00"))
				.andExpect(jsonPath("$.dias[0].turnos[0].estado").value("COMPLETADO"))
				.andExpect(jsonPath("$.dias[0].turnos[0].precio").value(4500.0))
				.andExpect(jsonPath("$.dias[0].turnos[0].servicio.nombre").value("Corte"))
				.andExpect(jsonPath("$.dias[0].turnos[0].servicio.duracionMin").value(30))
				.andExpect(jsonPath("$.dias[0].turnos[0].cliente.nombre").value("Juan Pérez"))
				.andExpect(jsonPath("$.dias[0].turnos[0].cliente.telefono").value("1144445555"))
				.andExpect(jsonPath("$.dias[0].turnos[1].estado").value("AUSENTE"))
				.andExpect(jsonPath("$.dias[0].bloqueos", hasSize(0)))
				// Martes: sin turnos, con el trámite y el comienzo del viaje.
				.andExpect(jsonPath("$.dias[1].turnos", hasSize(0)))
				.andExpect(jsonPath("$.dias[1].bloqueos[*].id", contains(franco.intValue(), viaje.intValue())))
				.andExpect(jsonPath("$.dias[1].bloqueos[0].inicio").value("2026-11-17T10:00:00-03:00"))
				.andExpect(jsonPath("$.dias[1].bloqueos[0].fin").value("2026-11-17T12:00:00-03:00"))
				.andExpect(jsonPath("$.dias[1].bloqueos[0].motivo").value("Trámite"))
				// Miércoles: dos turnos y el viaje sigue.
				.andExpect(jsonPath("$.dias[2].turnos[*].id", contains(mie09.intValue(), mie11.intValue())))
				.andExpect(jsonPath("$.dias[2].bloqueos[*].id", contains(viaje.intValue())))
				// Jueves: termina el viaje, con su inicio y fin completos. Viernes vacío.
				.andExpect(jsonPath("$.dias[3].turnos", hasSize(0)))
				.andExpect(jsonPath("$.dias[3].bloqueos[0].inicio").value("2026-11-17T20:00:00-03:00"))
				.andExpect(jsonPath("$.dias[3].bloqueos[0].fin").value("2026-11-19T08:00:00-03:00"))
				.andExpect(jsonPath("$.dias[4].turnos", hasSize(0)))
				.andExpect(jsonPath("$.dias[4].bloqueos", hasSize(0)));
	}

	@Test
	void losCanceladosSeOcultanSalvoQueSePidan() throws Exception {
		Long confirmado = insertarTurno(barberiaA, clienteA, servicioA, MIERCOLES, "09:00", "09:30", "CONFIRMADO",
				"5000");
		Long cancelado = insertarTurno(barberiaA, clienteA, servicioA, MIERCOLES, "10:00", "10:30", "CANCELADO", "5000");

		agenda(tokenA, MIERCOLES, MIERCOLES)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.dias[0].turnos[*].id", contains(confirmado.intValue())));

		getCon(tokenA, "/api/v1/agenda?desde=" + MIERCOLES + "&hasta=" + MIERCOLES + "&incluirCancelados=true")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.dias[0].turnos[*].id", contains(confirmado.intValue(), cancelado.intValue())))
				.andExpect(jsonPath("$.dias[0].turnos[1].estado").value("CANCELADO"))
				.andExpect(jsonPath("$.dias[0].turnos[1].canceladoPor").value("BARBERO"));
	}

	@Test
	void elRangoEsDeHasta31DiasContandoAmbosExtremos() throws Exception {
		agenda(tokenA, LUNES, LUNES.plusDays(30))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.dias", hasSize(31)));

		agenda(tokenA, LUNES, LUNES.plusDays(31))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.hasta").value("El rango puede ser de hasta 31 días"));
	}

	@Test
	void hastaAnteriorADesdeDa400() throws Exception {
		agenda(tokenA, MARTES, LUNES)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.hasta").exists());
	}

	@Test
	void unDiaSoloEsValido() throws Exception {
		agenda(tokenA, LUNES, LUNES)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.dias", hasSize(1)));
	}

	@Test
	void faltaUnaFechaDa400() throws Exception {
		getCon(tokenA, "/api/v1/agenda?desde=" + LUNES).andExpect(status().isBadRequest());
		getCon(tokenA, "/api/v1/agenda?desde=16/11/2026&hasta=" + LUNES).andExpect(status().isBadRequest());
	}

	// --- Aislamiento ---

	@Test
	void elDuenoDeOtraBarberiaNoVeLaAgendaDeA() throws Exception {
		insertarTurno(barberiaA, clienteA, servicioA, MIERCOLES, "09:00", "09:30", "CONFIRMADO", "5000");
		insertarBloqueo(miembroIdDeSlug("barberia-a"), MIERCOLES, "11:00", MIERCOLES, "12:00", null);
		String tokenB = duenoConBarberia("b@mail.com", "Barbería B");

		agenda(tokenB, LUNES, VIERNES)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.dias", hasSize(5)))
				.andExpect(jsonPath("$.dias[*].turnos[*]", hasSize(0)))
				.andExpect(jsonPath("$.dias[*].bloqueos[*]", hasSize(0)));
	}

	@Test
	void unClienteSinRolDa403() throws Exception {
		String token = tokenDe(clienteConPerfil("ana@mail.com"));
		agenda(token, LUNES, VIERNES).andExpect(status().isForbidden());
	}

	@Test
	void sinTokenDa401() throws Exception {
		mvc.perform(get("/api/v1/agenda?desde=" + LUNES + "&hasta=" + VIERNES)).andExpect(status().isUnauthorized());
	}

	// ---------------------------------------------------------------------------------------------------------

	private ResultActions agenda(String token, LocalDate desde, LocalDate hasta) throws Exception {
		return getCon(token, "/api/v1/agenda?desde=" + desde + "&hasta=" + hasta);
	}

	private Long insertarBloqueo(Long miembroId, LocalDate diaInicio, String horaInicio, LocalDate diaFin,
			String horaFin, String motivo) {
		return jdbc.queryForObject("""
				INSERT INTO bloqueos_agenda (miembro_id, inicio, fin, motivo) VALUES (?, ?, ?, ?) RETURNING id
				""", Long.class, miembroId, offset(diaInicio, horaInicio), offset(diaFin, horaFin), motivo);
	}

}
