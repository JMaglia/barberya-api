package com.barberya.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.barberya.IntegracionConLimpiezaTest;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/**
 * Endpoints de auth contra el Postgres real. Sin {@code @Transactional}: la revocación de la familia de
 * refresh se commitea aunque la respuesta sea 401, y eso hay que verlo en la base.
 */
@AutoConfigureMockMvc
class AuthIntegracionTest extends IntegracionConLimpiezaTest {

	private static final String PASSWORD = "clave-segura-1";
	private static final String MENSAJE_LOGIN_GENERICO = "Mail o contraseña incorrectos";

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JwtDecoder jwtDecoder;
	@Autowired
	private EnvioMailsFalso envioMails;

	@BeforeEach
	void limpiarMails() {
		envioMails.links.clear();
	}

	// --- Registro ---

	@Test
	void registroOkDevuelve201SinPassword() throws Exception {
		registrar("juan@mail.com")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.nombre").value("Juan"))
				.andExpect(jsonPath("$.email").value("juan@mail.com"))
				.andExpect(jsonPath("$.telefono").value("1122334455"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());

		String hash = jdbc.queryForObject("SELECT password_hash FROM usuarios WHERE email = 'juan@mail.com'", String.class);
		assertThat(hash).startsWith("$2").isNotEqualTo(PASSWORD);
	}

	@Test
	void registroConMailRepetidoDevuelve409() throws Exception {
		registrar("juan@mail.com").andExpect(status().isCreated());

		registrar("JUAN@mail.com")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.detail").value("Ya existe una cuenta con ese mail"));
	}

	@Test
	void registroConMayusculasGuardaElMailEnMinuscula() throws Exception {
		registrar("Juan.Perez@Mail.COM")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value("juan.perez@mail.com"));

		assertThat(jdbc.queryForObject("SELECT email FROM usuarios", String.class)).isEqualTo("juan.perez@mail.com");
	}

	@Test
	void registroConPasswordCortaDevuelve400ConElCampo() throws Exception {
		mvc.perform(postJson("/api/v1/auth/registro", """
				{"nombre":"Juan","email":"juan@mail.com","telefono":"1122334455","password":"1234567"}
				"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.password").value("La contraseña tiene que tener entre 8 y 72 caracteres"));

		assertThat(jdbc.queryForObject("SELECT count(*) FROM usuarios", Integer.class)).isZero();
	}

	// --- Login ---

	@Test
	void loginOkDevuelveTokenYCookieDeTreintaDiasConMantenerSesion() throws Exception {
		registrar("juan@mail.com");

		MockHttpServletResponse respuesta = login("juan@mail.com", PASSWORD, true)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isString())
				.andExpect(jsonPath("$.tipo").value("Bearer"))
				.andExpect(jsonPath("$.expiraEnSegundos").value(900))
				.andReturn().getResponse();

		String setCookie = respuesta.getHeader(HttpHeaders.SET_COOKIE);
		assertThat(setCookie).startsWith("refresh_token=")
				.contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/api/v1/auth", "Max-Age=2592000");
		assertThat(jdbc.queryForObject("SELECT mantener_sesion FROM refresh_tokens", Boolean.class)).isTrue();
		// En la base solo está el hash.
		String valor = respuesta.getCookie("refresh_token").getValue();
		assertThat(jdbc.queryForObject("SELECT token_hash FROM refresh_tokens", String.class))
				.isEqualTo(TokenService.sha256(valor)).isNotEqualTo(valor);
	}

	@Test
	void loginSinMantenerSesionDevuelveCookieDeSesion() throws Exception {
		registrar("juan@mail.com");

		login("juan@mail.com", PASSWORD, false)
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
				.andExpect(header().string(HttpHeaders.SET_COOKIE, not(containsString("Max-Age"))));

		assertThat(jdbc.queryForObject(
				"SELECT expira_en < now() + interval '25 hours' AND NOT mantener_sesion FROM refresh_tokens", Boolean.class))
				.isTrue();
	}

	@Test
	void loginConPasswordIncorrectaDevuelve401Generico() throws Exception {
		registrar("juan@mail.com");

		login("juan@mail.com", "otra-clave-123", false)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value(MENSAJE_LOGIN_GENERICO))
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
	}

	@Test
	void loginConMailInexistenteDevuelveElMismoMensaje() throws Exception {
		login("nadie@mail.com", PASSWORD, false)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value(MENSAJE_LOGIN_GENERICO));
	}

	@Test
	void loginAceptaElMailConMayusculas() throws Exception {
		registrar("juan@mail.com");

		login("Juan@Mail.com", PASSWORD, false).andExpect(status().isOk());
	}

	// --- /me ---

	@Test
	void meSinTokenDevuelve401() throws Exception {
		mvc.perform(get("/api/v1/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").value("Tenés que iniciar sesión"));
	}

	@Test
	void meConTokenInvalidoDevuelve401() throws Exception {
		mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer no-es-un-jwt"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value("Tenés que iniciar sesión"));
	}

	@Test
	void meConTokenDevuelveLosDatosSinBarberia() throws Exception {
		Long id = idDe(registrar("juan@mail.com"));
		String access = accessDe(login("juan@mail.com", PASSWORD, false));

		mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.nombre").value("Juan"))
				.andExpect(jsonPath("$.email").value("juan@mail.com"))
				.andExpect(jsonPath("$.telefono").value("1122334455"))
				.andExpect(jsonPath("$.emailVerificado").value(false))
				.andExpect(jsonPath("$.barberiaId").isEmpty())
				.andExpect(jsonPath("$.rol").isEmpty());

		Jwt jwt = jwtDecoder.decode(access);
		assertThat(jwt.getSubject()).isEqualTo(id.toString());
		assertThat(jwt.hasClaim("barberiaId")).isFalse();
		assertThat(jwt.hasClaim("rol")).isFalse();
	}

	// --- Refresh ---

	@Test
	void refreshRotaElTokenYElViejoYaNoSirve() throws Exception {
		registrar("juan@mail.com");
		Cookie original = cookieDe(login("juan@mail.com", PASSWORD, true));

		MockHttpServletResponse rotada = refresh(original)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isString())
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=2592000")))
				.andReturn().getResponse();
		Cookie nueva = rotada.getCookie("refresh_token");
		assertThat(nueva.getValue()).isNotEqualTo(original.getValue());

		// El viejo quedó revocado y apunta al nuevo.
		Long idNuevo = jdbc.queryForObject("SELECT id FROM refresh_tokens WHERE token_hash = ?", Long.class,
				TokenService.sha256(nueva.getValue()));
		assertThat(jdbc.queryForMap("SELECT revocado, reemplazado_por, mantener_sesion FROM refresh_tokens WHERE token_hash = ?",
				TokenService.sha256(original.getValue())))
				.containsEntry("revocado", true)
				.containsEntry("reemplazado_por", idNuevo)
				.containsEntry("mantener_sesion", true);

		// El nuevo sirve para seguir rotando.
		refresh(nueva).andExpect(status().isOk());
	}

	@Test
	void reusarUnRefreshRevocadoRevocaTodaLaFamilia() throws Exception {
		registrar("juan@mail.com");
		Cookie original = cookieDe(login("juan@mail.com", PASSWORD, false));
		Cookie otroDispositivo = cookieDe(login("juan@mail.com", PASSWORD, true));
		Cookie nueva = cookieDe(refresh(original));

		refresh(original)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value("Tu sesión venció, volvé a iniciar sesión"))
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

		// Ni el que lo reemplazó ni las otras sesiones del usuario sirven más.
		refresh(nueva).andExpect(status().isUnauthorized());
		refresh(otroDispositivo).andExpect(status().isUnauthorized());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE NOT revocado", Integer.class)).isZero();
	}

	@Test
	void refreshSinCookieOConValorDesconocidoDevuelve401() throws Exception {
		mvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isUnauthorized());
		refresh(new Cookie("refresh_token", "inventado")).andExpect(status().isUnauthorized());
	}

	@Test
	void refreshVencidoDevuelve401() throws Exception {
		registrar("juan@mail.com");
		Cookie cookie = cookieDe(login("juan@mail.com", PASSWORD, false));
		jdbc.update("UPDATE refresh_tokens SET expira_en = now() - interval '1 minute'");

		refresh(cookie).andExpect(status().isUnauthorized());
	}

	// --- Logout ---

	@Test
	void despuesDelLogoutElRefreshNoSirve() throws Exception {
		registrar("juan@mail.com");
		Cookie cookie = cookieDe(login("juan@mail.com", PASSWORD, true));

		mvc.perform(post("/api/v1/auth/logout").cookie(cookie))
				.andExpect(status().isNoContent())
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/v1/auth")));

		refresh(cookie).andExpect(status().isUnauthorized());
	}

	@Test
	void logoutSinCookieDevuelve204() throws Exception {
		mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());
	}

	// --- Recuperar y restablecer ---

	@Test
	void recuperarConMailInexistenteDevuelve200YNoEnviaNada() throws Exception {
		recuperar("nadie@mail.com")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mensaje").isString());

		assertThat(envioMails.links).isEmpty();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM tokens_verificacion", Integer.class)).isZero();
	}

	@Test
	void recuperarYRestablecerCambiaLaPasswordUnaSolaVez() throws Exception {
		registrar("juan@mail.com");
		Cookie sesionVieja = cookieDe(login("juan@mail.com", PASSWORD, true));

		recuperar("Juan@Mail.com").andExpect(status().isOk());
		assertThat(envioMails.links).hasSize(1);
		String link = envioMails.links.getFirst();
		assertThat(link).startsWith("http://localhost:5173/restablecer?token=");
		String token = link.substring(link.indexOf("token=") + "token=".length());
		assertThat(jdbc.queryForMap("SELECT tipo, token_hash, expira_en < now() + interval '31 minutes' AS corto FROM tokens_verificacion"))
				.containsEntry("tipo", "RECUPERAR_CLAVE")
				.containsEntry("token_hash", TokenService.sha256(token))
				.containsEntry("corto", true);

		restablecer(token, "clave-nueva-99").andExpect(status().isNoContent());

		login("juan@mail.com", "clave-nueva-99", false).andExpect(status().isOk());
		login("juan@mail.com", PASSWORD, false).andExpect(status().isUnauthorized());
		refresh(sesionVieja).andExpect(status().isUnauthorized());
		restablecer(token, "otra-clave-77")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("El link para restablecer la contraseña no es válido o venció"));
		login("juan@mail.com", "clave-nueva-99", false).andExpect(status().isOk());
	}

	@Test
	void restablecerConTokenVencidoDevuelve400() throws Exception {
		registrar("juan@mail.com");
		recuperar("juan@mail.com");
		String link = envioMails.links.getFirst();
		String token = link.substring(link.indexOf("token=") + "token=".length());
		jdbc.update("UPDATE tokens_verificacion SET expira_en = now() - interval '1 minute'");

		restablecer(token, "clave-nueva-99").andExpect(status().isBadRequest());
		login("juan@mail.com", PASSWORD, false).andExpect(status().isOk());
	}

	// --- Miembros y aislamiento entre barberías ---

	@Test
	void unMiembroRecibeBarberiaIdYRolEnElTokenYEnMe() throws Exception {
		Long usuarioId = idDe(registrar("barbero@mail.com"));
		Long barberiaId = crearBarberiaConDueno("uno", usuarioId);

		String access = accessDe(login("barbero@mail.com", PASSWORD, false));

		Jwt jwt = jwtDecoder.decode(access);
		assertThat(jwt.getSubject()).isEqualTo(usuarioId.toString());
		assertThat(((Number) jwt.getClaim("barberiaId")).longValue()).isEqualTo(barberiaId);
		assertThat(jwt.getClaimAsString("rol")).isEqualTo("DUENO");
		mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.barberiaId").value(barberiaId))
				.andExpect(jsonPath("$.rol").value("DUENO"));
	}

	@Test
	void elTokenRefrescadoTambienTraeLaBarberia() throws Exception {
		Long usuarioId = idDe(registrar("barbero@mail.com"));
		Long barberiaId = crearBarberiaConDueno("uno", usuarioId);
		Cookie cookie = cookieDe(login("barbero@mail.com", PASSWORD, false));

		String access = accessDe(refresh(cookie));

		assertThat(((Number) jwtDecoder.decode(access).getClaim("barberiaId")).longValue()).isEqualTo(barberiaId);
	}

	@Test
	void cadaUsuarioVeSoloSuBarberiaAunqueMandeOtraEnElLogin() throws Exception {
		Long usuarioUno = idDe(registrar("uno@mail.com"));
		Long usuarioDos = idDe(registrar("dos@mail.com"));
		Long barberiaUno = crearBarberiaConDueno("uno", usuarioUno);
		Long barberiaDos = crearBarberiaConDueno("dos", usuarioDos);

		// barberiaId y rol en el body se ignoran: salen de miembros_barberia.
		String access = accessDe(mvc.perform(postJson("/api/v1/auth/login", """
				{"email":"uno@mail.com","password":"%s","mantenerSesion":false,"barberiaId":%d,"rol":"BARBERO"}
				""".formatted(PASSWORD, barberiaDos))).andExpect(status().isOk()));

		Jwt jwt = jwtDecoder.decode(access);
		assertThat(((Number) jwt.getClaim("barberiaId")).longValue()).isEqualTo(barberiaUno);
		assertThat(jwt.getClaimAsString("rol")).isEqualTo("DUENO");
		mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
				.andExpect(jsonPath("$.id").value(usuarioUno))
				.andExpect(jsonPath("$.barberiaId").value(barberiaUno));

		String accessDos = accessDe(login("dos@mail.com", PASSWORD, false));
		mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessDos))
				.andExpect(jsonPath("$.id").value(usuarioDos))
				.andExpect(jsonPath("$.barberiaId").value(barberiaDos));
	}

	// --- Helpers ---

	private ResultActions registrar(String email) throws Exception {
		return mvc.perform(postJson("/api/v1/auth/registro", """
				{"nombre":"Juan","email":"%s","telefono":"1122334455","password":"%s"}
				""".formatted(email, PASSWORD)));
	}

	private ResultActions login(String email, String password, boolean mantenerSesion) throws Exception {
		return mvc.perform(postJson("/api/v1/auth/login", """
				{"email":"%s","password":"%s","mantenerSesion":%b}
				""".formatted(email, password, mantenerSesion)));
	}

	private ResultActions refresh(Cookie cookie) throws Exception {
		return mvc.perform(post("/api/v1/auth/refresh").cookie(cookie));
	}

	private ResultActions recuperar(String email) throws Exception {
		return mvc.perform(postJson("/api/v1/auth/recuperar", """
				{"email":"%s"}
				""".formatted(email)));
	}

	private ResultActions restablecer(String token, String nuevaPassword) throws Exception {
		return mvc.perform(postJson("/api/v1/auth/restablecer", """
				{"token":"%s","nuevaPassword":"%s"}
				""".formatted(token, nuevaPassword)));
	}

	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postJson(String url, String json) {
		return post(url).contentType(MediaType.APPLICATION_JSON).content(json);
	}

	private static Long idDe(ResultActions registro) throws Exception {
		return ((Number) JsonPath.read(registro.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
	}

	private static String accessDe(ResultActions resultado) throws Exception {
		return JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), "$.accessToken");
	}

	private static Cookie cookieDe(ResultActions resultado) throws Exception {
		Cookie cookie = resultado.andExpect(status().isOk()).andReturn().getResponse().getCookie("refresh_token");
		assertThat(cookie).isNotNull();
		return new Cookie(cookie.getName(), cookie.getValue());
	}

	private Long crearBarberiaConDueno(String sufijo, Long usuarioId) {
		Long barberiaId = jdbc.queryForObject("INSERT INTO barberias (nombre, slug) VALUES (?, ?) RETURNING id",
				Long.class, "Barberia " + sufijo, "barberia-" + sufijo);
		jdbc.update("INSERT INTO miembros_barberia (barberia_id, usuario_id, rol) VALUES (?, ?, 'DUENO')",
				barberiaId, usuarioId);
		return barberiaId;
	}

	/** Guarda los links en vez de mandar mails, para que el test pueda usar el token. */
	static class EnvioMailsFalso implements EnvioMails {

		final List<String> links = new ArrayList<>();

		@Override
		public void enviarRecuperacionClave(String email, String link) {
			links.add(link);
		}
	}

	@TestConfiguration
	static class Config {

		@Bean
		@Primary
		EnvioMailsFalso envioMailsFalso() {
			return new EnvioMailsFalso();
		}
	}

}
