package com.barberya.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import com.barberya.ApiIntegracionTest;
import com.barberya.RelojDeTest;

/** Límites de intentos de los endpoints de auth: 429 con Retry-After, sin revelar si el mail existe. */
class LimitesAuthIntegracionTest extends ApiIntegracionTest {

	private static final Instant AHORA = Instant.parse("2026-11-26T15:00:00Z");
	private static final String PASSWORD = "clave-segura-1";
	private static final String IP = "127.0.0.1";
	private static final String OTRA_IP = "10.0.0.2";
	private static final String DETALLE_15_MIN = "Hiciste demasiados intentos. Probá de nuevo en 15 minutos.";
	private static final String DETALLE_60_MIN = "Hiciste demasiados intentos. Probá de nuevo en 60 minutos.";

	@Autowired
	private RelojDeTest reloj;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private DoblesDeTestConfig.EnvioMailsFalso envioMails;
	@Autowired
	private DoblesDeTestConfig.VerificadorGoogleFalso google;

	@BeforeEach
	void preparar() {
		reloj.fijar(AHORA);
		envioMails.limpiar();
		google.limpiar();
	}

	@AfterEach
	void devolverReloj() {
		reloj.usarReal();
	}

	// --- Login ---

	@Test
	void loginDiezContrasenasMalYLaOnceavaAunqueSeaCorrectaDevuelve429() throws Exception {
		insertarUsuarioConPassword("ana@mail.com");
		for (int i = 0; i < 10; i++) {
			login("ana@mail.com", "mala-" + i, IP).andExpect(status().isUnauthorized());
		}

		esperar429(login("ana@mail.com", PASSWORD, IP), 900, DETALLE_15_MIN);
	}

	@Test
	void loginElMailSeCuentaAunqueSeEscribaDistintoYAunqueNoExistaLaCuenta() throws Exception {
		for (int i = 0; i < 10; i++) {
			login(i % 2 == 0 ? "nadie@mail.com" : " NADIE@Mail.com ", "mala", IP).andExpect(status().isUnauthorized());
		}

		esperar429(login("nadie@mail.com", "mala", IP), 900, DETALLE_15_MIN);
	}

	@Test
	void loginExitosoReiniciaElContadorDelMail() throws Exception {
		insertarUsuarioConPassword("ana@mail.com");
		for (int i = 0; i < 9; i++) {
			login("ana@mail.com", "mala", IP).andExpect(status().isUnauthorized());
		}
		login("ana@mail.com", PASSWORD, IP).andExpect(status().isOk());

		for (int i = 0; i < 10; i++) {
			login("ana@mail.com", "mala", IP).andExpect(status().isUnauthorized());
		}
		login("ana@mail.com", PASSWORD, IP).andExpect(status().isTooManyRequests());
	}

	@Test
	void loginConElRelojAdelantadoVuelveADejar() throws Exception {
		insertarUsuarioConPassword("ana@mail.com");
		for (int i = 0; i < 10; i++) {
			login("ana@mail.com", "mala", IP);
		}
		reloj.fijar(AHORA.plus(Duration.ofMinutes(10)));
		esperar429(login("ana@mail.com", PASSWORD, IP), 300, "Hiciste demasiados intentos. Probá de nuevo en 5 minutos.");

		reloj.fijar(AHORA.plus(Duration.ofMinutes(15)));
		login("ana@mail.com", PASSWORD, IP).andExpect(status().isOk());
	}

	@Test
	void loginPorIpCortaA30IntentosYOtraIpSigue() throws Exception {
		insertarUsuarioConPassword("ana@mail.com");
		for (int i = 0; i < 30; i++) {
			login("persona" + i + "@mail.com", "mala", IP).andExpect(status().isUnauthorized());
		}

		esperar429(login("ana@mail.com", PASSWORD, IP), 900, DETALLE_15_MIN);
		login("ana@mail.com", PASSWORD, OTRA_IP).andExpect(status().isOk());
	}

	// --- Pedir código ---

	@Test
	void solicitarCodigoCortaIgualConMailExistenteEInexistente() throws Exception {
		insertarUsuario("ana@mail.com");
		for (int i = 0; i < 3; i++) {
			solicitar("ana@mail.com", IP).andExpect(status().isOk());
			solicitar("nadie@mail.com", IP).andExpect(status().isOk());
		}

		String existente = esperar429(solicitar("ana@mail.com", IP), 900, DETALLE_15_MIN);
		String inexistente = esperar429(solicitar("nadie@mail.com", IP), 900, DETALLE_15_MIN);
		assertThat(existente).isEqualTo(inexistente);
		// Cortado, no se manda ningún mail más.
		assertThat(envioMails.codigos).hasSize(6);
	}

	@Test
	void solicitarCodigoPorIpCortaA10EnUnaHoraYOtraIpSigue() throws Exception {
		for (int i = 0; i < 10; i++) {
			solicitar("persona" + i + "@mail.com", IP).andExpect(status().isOk());
		}

		esperar429(solicitar("otra@mail.com", IP), 3600, DETALLE_60_MIN);
		solicitar("otra@mail.com", OTRA_IP).andExpect(status().isOk());

		reloj.fijar(AHORA.plus(Duration.ofHours(1)));
		solicitar("otra2@mail.com", IP).andExpect(status().isOk());
	}

	@Test
	void solicitarCodigoConElRelojAdelantadoVuelveADejar() throws Exception {
		for (int i = 0; i < 3; i++) {
			solicitar("ana@mail.com", IP).andExpect(status().isOk());
		}
		solicitar("ana@mail.com", IP).andExpect(status().isTooManyRequests());

		reloj.fijar(AHORA.plus(Duration.ofMinutes(15)));
		solicitar("ana@mail.com", IP).andExpect(status().isOk());
	}

	// --- Verificar código ---

	@Test
	void verificarCodigoCortaA10PorMailYSeLiberaConElTiempo() throws Exception {
		for (int i = 0; i < 10; i++) {
			verificar("ana@mail.com", "000000", IP).andExpect(status().isUnauthorized());
		}

		esperar429(verificar("ana@mail.com", "000000", IP), 900, DETALLE_15_MIN);

		reloj.fijar(AHORA.plus(Duration.ofMinutes(15)));
		verificar("ana@mail.com", "000000", IP).andExpect(status().isUnauthorized());
	}

	@Test
	void verificarCodigoPorIpCortaA30YOtraIpSigue() throws Exception {
		for (int i = 0; i < 30; i++) {
			verificar("persona" + i + "@mail.com", "000000", IP).andExpect(status().isUnauthorized());
		}

		esperar429(verificar("otra@mail.com", "000000", IP), 900, DETALLE_15_MIN);
		verificar("otra@mail.com", "000000", OTRA_IP).andExpect(status().isUnauthorized());
	}

	// --- Recuperar ---

	@Test
	void recuperarCortaIgualConMailExistenteEInexistenteYSeLiberaALaHora() throws Exception {
		insertarUsuario("ana@mail.com");
		for (int i = 0; i < 3; i++) {
			recuperar("ana@mail.com", IP).andExpect(status().isOk());
			recuperar("nadie@mail.com", IP).andExpect(status().isOk());
		}

		String existente = esperar429(recuperar("ana@mail.com", IP), 3600, DETALLE_60_MIN);
		String inexistente = esperar429(recuperar("nadie@mail.com", IP), 3600, DETALLE_60_MIN);
		assertThat(existente).isEqualTo(inexistente);
		assertThat(envioMails.links).hasSize(3);

		reloj.fijar(AHORA.plus(Duration.ofHours(1)));
		recuperar("ana@mail.com", IP).andExpect(status().isOk());
	}

	@Test
	void recuperarPorIpCortaA10() throws Exception {
		for (int i = 0; i < 10; i++) {
			recuperar("persona" + i + "@mail.com", IP).andExpect(status().isOk());
		}

		esperar429(recuperar("otra@mail.com", IP), 3600, DETALLE_60_MIN);
		recuperar("otra@mail.com", OTRA_IP).andExpect(status().isOk());
	}

	// --- Registro ---

	@Test
	void registroCortaA10PorIpYOtraIpSigue() throws Exception {
		for (int i = 0; i < 10; i++) {
			registrar("persona" + i + "@mail.com", IP).andExpect(status().isCreated());
		}

		esperar429(registrar("otra@mail.com", IP), 3600, DETALLE_60_MIN);
		registrar("otra@mail.com", OTRA_IP).andExpect(status().isCreated());

		reloj.fijar(AHORA.plus(Duration.ofHours(1)));
		registrar("otra2@mail.com", IP).andExpect(status().isCreated());
	}

	@Test
	void unBodyInvalidoNoConsumeIntentos() throws Exception {
		for (int i = 0; i < 15; i++) {
			mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON).content("{}"))
					.andExpect(status().isBadRequest());
		}

		registrar("ana@mail.com", IP).andExpect(status().isCreated());
	}

	// --- Google ---

	@Test
	void googleCortaA30PorIpYOtraIpSigue() throws Exception {
		google.aceptar("token-ana", new DatosGoogle("sub-ana", "ana@gmail.com", "Ana", null));
		for (int i = 0; i < 30; i++) {
			loginGoogle("token-invalido", IP).andExpect(status().isUnauthorized());
		}

		esperar429(loginGoogle("token-ana", IP), 900, DETALLE_15_MIN);
		loginGoogle("token-ana", OTRA_IP).andExpect(status().isOk());

		reloj.fijar(AHORA.plus(Duration.ofMinutes(15)));
		loginGoogle("token-ana", IP).andExpect(status().isOk());
	}

	// --- Helpers ---

	/** Chequea el 429 completo y devuelve el body, para comparar respuestas. */
	private static String esperar429(ResultActions resultado, long segundos, String detalle) throws Exception {
		return resultado
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string(HttpHeaders.RETRY_AFTER, String.valueOf(segundos)))
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
				.andExpect(jsonPath("$.status").value(429))
				.andExpect(jsonPath("$.title").value("Demasiados intentos"))
				.andExpect(jsonPath("$.detail").value(detalle))
				.andExpect(jsonPath("$.codigo").value("DEMASIADOS_INTENTOS"))
				.andReturn().getResponse().getContentAsString();
	}

	private void insertarUsuarioConPassword(String email) {
		jdbc.update("INSERT INTO usuarios (email, nombre, email_verificado, password_hash) VALUES (?, 'Ana', TRUE, ?)",
				email, passwordEncoder.encode(PASSWORD));
	}

	private ResultActions login(String email, String password, String ip) throws Exception {
		return postDesde("/api/v1/auth/login", """
				{"email":"%s","password":"%s","mantenerSesion":false}
				""".formatted(email, password), ip);
	}

	private ResultActions solicitar(String email, String ip) throws Exception {
		return postDesde("/api/v1/auth/codigo/solicitar", "{\"email\":\"%s\"}".formatted(email), ip);
	}

	private ResultActions verificar(String email, String codigo, String ip) throws Exception {
		return postDesde("/api/v1/auth/codigo/verificar", """
				{"email":"%s","codigo":"%s","mantenerSesion":false}
				""".formatted(email, codigo), ip);
	}

	private ResultActions recuperar(String email, String ip) throws Exception {
		return postDesde("/api/v1/auth/recuperar", "{\"email\":\"%s\"}".formatted(email), ip);
	}

	private ResultActions registrar(String email, String ip) throws Exception {
		return postDesde("/api/v1/auth/registro", """
				{"nombre":"Juan","email":"%s","telefono":"1122334455","password":"%s"}
				""".formatted(email, PASSWORD), ip);
	}

	private ResultActions loginGoogle(String idToken, String ip) throws Exception {
		return postDesde("/api/v1/auth/google", "{\"idToken\":\"%s\",\"mantenerSesion\":false}".formatted(idToken), ip);
	}

	private ResultActions postDesde(String url, String json, String ip) throws Exception {
		return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json).with(request -> {
			request.setRemoteAddr(ip);
			return request;
		}));
	}

}
