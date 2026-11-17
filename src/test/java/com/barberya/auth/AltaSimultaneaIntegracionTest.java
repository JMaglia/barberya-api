package com.barberya.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.barberya.IntegracionConLimpiezaTest;

/**
 * Dos altas simultáneas de la misma cuenta: se simula que la búsqueda no encontró al usuario que otra request
 * acaba de crear, así el insert choca con el UNIQUE. Tiene que responder 409, no 500.
 */
@AutoConfigureMockMvc
@Import(AuthFalsosConfig.class)
class AltaSimultaneaIntegracionTest extends IntegracionConLimpiezaTest {

	private static final String CUENTA_DUPLICADA = "Ya existe una cuenta con ese mail, probá entrar de nuevo";

	@Autowired
	private MockMvc mvc;
	@MockitoSpyBean
	private UsuarioRepository usuarios;
	@Autowired
	private AuthFalsosConfig.EnvioMailsFalso envioMails;
	@Autowired
	private AuthFalsosConfig.VerificadorGoogleFalso google;

	@BeforeEach
	void limpiarDobles() {
		envioMails.limpiar();
		google.limpiar();
	}

	@Test
	void googleConElMismoMailCreadoEnParaleloDevuelve409() throws Exception {
		jdbc.update("INSERT INTO usuarios (email, nombre) VALUES ('ana@gmail.com', 'Ana')");
		google.aceptar("token-ana", new DatosGoogle("sub-ana", "ana@gmail.com", "Ana", null));
		doReturn(Optional.empty()).when(usuarios).findByEmail(anyString());

		loginGoogle("token-ana")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value(CUENTA_DUPLICADA))
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

		assertThat(jdbc.queryForObject("SELECT count(*) FROM usuarios", Integer.class)).isOne();
	}

	@Test
	void googleConElMismoSubCreadoEnParaleloDevuelve409() throws Exception {
		jdbc.update("INSERT INTO usuarios (email, nombre, google_sub) VALUES ('ana@gmail.com', 'Ana', 'sub-ana')");
		// Google informa otro mail para el mismo sub; la búsqueda por sub "todavía" no lo ve.
		google.aceptar("token-ana", new DatosGoogle("sub-ana", "ana.otra@gmail.com", "Ana", null));
		doReturn(Optional.empty()).when(usuarios).findByGoogleSub(anyString());

		loginGoogle("token-ana")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value(CUENTA_DUPLICADA));

		assertThat(jdbc.queryForObject("SELECT count(*) FROM usuarios", Integer.class)).isOne();
	}

	@Test
	void codigoConElMismoMailCreadoEnParaleloDevuelve409YElCodigoSigueSinUsar() throws Exception {
		mvc.perform(post("/api/v1/auth/codigo/solicitar").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"ana@mail.com\"}")).andExpect(status().isOk());
		jdbc.update("INSERT INTO usuarios (email, nombre) VALUES ('ana@mail.com', 'Ana')");
		doReturn(Optional.empty()).when(usuarios).findByEmail(anyString());

		mvc.perform(post("/api/v1/auth/codigo/verificar").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"ana@mail.com","codigo":"%s","mantenerSesion":false}
						""".formatted(envioMails.ultimoCodigo())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value(CUENTA_DUPLICADA));

		// Se revirtió todo: el código no quedó usado y se puede volver a intentar.
		assertThat(jdbc.queryForObject("SELECT usado_en IS NULL FROM tokens_verificacion", Boolean.class)).isTrue();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM usuarios", Integer.class)).isOne();
	}

	private org.springframework.test.web.servlet.ResultActions loginGoogle(String idToken) throws Exception {
		return mvc.perform(post("/api/v1/auth/google").contentType(MediaType.APPLICATION_JSON)
				.content("{\"idToken\":\"%s\",\"mantenerSesion\":false}".formatted(idToken)));
	}

}
