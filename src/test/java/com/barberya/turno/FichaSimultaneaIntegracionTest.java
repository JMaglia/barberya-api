package com.barberya.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.barberya.cliente.ClienteRepository;

/**
 * Dos reservas que crean la ficha del mismo cliente a la vez: se simula que la búsqueda no vio la ficha que otra
 * request acaba de crear, así el insert choca con el UNIQUE (barberia_id, usuario_id). Tiene que dar 409, no 500.
 */
class FichaSimultaneaIntegracionTest extends TurnoIntegracionBase {

	@MockitoSpyBean
	private ClienteRepository clientes;

	@Test
	void fichaCreadaEnParaleloDa409YNoDejaNada() throws Exception {
		Long ana = clienteConPerfil("ana@mail.com");
		jdbc.update("INSERT INTO clientes (barberia_id, usuario_id, nombre) VALUES (?, ?, 'Ana')", barberiaA, ana);
		doReturn(Optional.empty()).when(clientes).findParaReservar(anyLong(), anyLong());

		reservar(tokenDe(ana), "barberia-a", servicioA, MIERCOLES, "10:00")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("Ya estamos procesando otra reserva tuya, probá de nuevo"));

		assertThat(contar("SELECT count(*) FROM clientes")).isOne();
		assertThat(contar("SELECT count(*) FROM turnos")).isZero();
	}

}
