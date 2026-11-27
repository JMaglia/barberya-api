package com.barberya.cliente;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import com.barberya.turno.TurnoIntegracionBase;

/** GET /api/v1/clientes/buscar: fichas activas de la barbería del token, por nombre o teléfono. */
class ClienteBusquedaIntegracionTest extends TurnoIntegracionBase {

	@Test
	void buscaPorNombreSinDistinguirMayusculasNiTildes() throws Exception {
		Long jose = insertarCliente(barberiaA, "José Pérez", "1144445555");
		insertarCliente(barberiaA, "Juan Gómez", null);

		buscar(tokenA, "jose")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(jose))
				.andExpect(jsonPath("$[0].nombre").value("José Pérez"))
				.andExpect(jsonPath("$[0].telefono").value("1144445555"));
		buscar(tokenA, "PÉR").andExpect(jsonPath("$[*].id", Matchers.contains(jose.intValue())));
		buscar(tokenA, "gomez").andExpect(jsonPath("$[*].nombre", Matchers.contains("Juan Gómez")));
	}

	@Test
	void buscaPorTelefonoSinImportarElFormato() throws Exception {
		Long ana = insertarCliente(barberiaA, "Ana", "11 5555-6666");
		insertarCliente(barberiaA, "Beto", "1144443333");

		buscar(tokenA, "5555666").andExpect(jsonPath("$[*].id", Matchers.contains(ana.intValue())));
		buscar(tokenA, "5555-66").andExpect(jsonPath("$[*].id", Matchers.contains(ana.intValue())));
		buscar(tokenA, "(11) 5555").andExpect(jsonPath("$[*].id", Matchers.contains(ana.intValue())));
	}

	@Test
	void menosDeDosCaracteresDa400() throws Exception {
		buscar(tokenA, "a")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.q").exists());
		buscar(tokenA, "  a  ").andExpect(status().isBadRequest());
		getCon(tokenA, "/api/v1/clientes/buscar").andExpect(status().isBadRequest());
	}

	@Test
	void soloTraeFichasActivas() throws Exception {
		Long activa = insertarCliente(barberiaA, "Carla Activa", null);
		Long inactiva = insertarCliente(barberiaA, "Carla Inactiva", null);
		jdbc.update("UPDATE clientes SET activo = FALSE WHERE id = ?", inactiva);

		buscar(tokenA, "carla").andExpect(jsonPath("$[*].id", Matchers.contains(activa.intValue())));
	}

	@Test
	void traeHastaDiezOrdenadasPorNombre() throws Exception {
		for (int i = 12; i >= 1; i--) {
			insertarCliente(barberiaA, "Cliente %02d".formatted(i), null);
		}

		buscar(tokenA, "cliente")
				.andExpect(jsonPath("$.length()").value(10))
				.andExpect(jsonPath("$[0].nombre").value("Cliente 01"))
				.andExpect(jsonPath("$[9].nombre").value("Cliente 10"));
	}

	@Test
	void losComodinesSeBuscanComoTexto() throws Exception {
		insertarCliente(barberiaA, "Pedro", null);
		Long conGuion = insertarCliente(barberiaA, "Pe_dro", null);

		buscar(tokenA, "%%").andExpect(jsonPath("$.length()").value(0));
		buscar(tokenA, "e_d").andExpect(jsonPath("$[*].id", Matchers.contains(conGuion.intValue())));
	}

	@Test
	void noTraeFichasDeOtraBarberia() throws Exception {
		insertarCliente(barberiaA, "Pedro de A", "1144445555");
		String tokenB = duenoConBarberia("b@mail.com", "Barbería B");
		Long pedroB = insertarCliente(barberiaIdDeSlug("barberia-b"), "Pedro de B", null);

		buscar(tokenB, "pedro").andExpect(jsonPath("$[*].id", Matchers.contains(pedroB.intValue())));
		buscar(tokenB, "44445555").andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void unClienteSinRolDa403() throws Exception {
		String token = tokenDe(clienteConPerfil("ana@mail.com"));

		buscar(token, "ana").andExpect(status().isForbidden());
	}

	@Test
	void sinTokenDa401() throws Exception {
		mvc.perform(get("/api/v1/clientes/buscar").param("q", "ana")).andExpect(status().isUnauthorized());
	}

	private ResultActions buscar(String token, String q) throws Exception {
		return mvc.perform(get("/api/v1/clientes/buscar").param("q", q).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

}
