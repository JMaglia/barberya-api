package com.barberya.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.barberya.IntegracionConLimpiezaTest;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/**
 * Login de clientes con Google y con código por mail, y PATCH /me. Sin {@code @Transactional}: los intentos fallidos
 * de un código se commitean aunque la respuesta sea 401, y eso hay que verlo en la base.
 */
@AutoConfigureMockMvc
@Import(DoblesDeTestConfig.class)
class LoginClientesIntegracionTest extends IntegracionConLimpiezaTest {

	private static final String PASSWORD = "clave-segura-1";
	private static final String CODIGO_INVALIDO = "El código no es válido o venció";

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JwtDecoder jwtDecoder;
	@Autowired
	private DoblesDeTestConfig.EnvioMailsFalso envioMails;
	@Autowired
	private DoblesDeTestConfig.VerificadorGoogleFalso google;

	@BeforeEach
	void limpiarDobles() {
		envioMails.limpiar();
		google.limpiar();
	}

	// --- Google ---

	@Test
	void googleConUsuarioNuevoLoCreaVerificadoYSinPassword() throws Exception {
		google.aceptar("token-ana", new DatosGoogle("sub-ana", "Ana@Gmail.com", "Ana", "García"));

		String access = accessDe(loginGoogle("token-ana", true)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isString())
				.andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=2592000"))));

		assertThat(jdbc.queryForMap(
				"SELECT email, google_sub, email_verificado, password_hash, nombre, apellido, telefono FROM usuarios"))
				.containsEntry("email", "ana@gmail.com")
				.containsEntry("google_sub", "sub-ana")
				.containsEntry("email_verificado", true)
				.containsEntry("password_hash", null)
				.containsEntry("nombre", "Ana")
				.containsEntry("apellido", "García")
				.containsEntry("telefono", null);
		me(access)
				.andExpect(jsonPath("$.emailVerificado").value(true))
				.andExpect(jsonPath("$.perfilCompleto").value(false));
	}

	@Test
	void googleConUsuarioExistentePorSubEntraAlMismoUsuario() throws Exception {
		google.aceptar("token-1", new DatosGoogle("sub-ana", "ana@gmail.com", "Ana", null));
		Long id = idDe(accessDe(loginGoogle("token-1", false).andExpect(status().isOk())));
		// Aunque Google informe otro mail, manda el sub.
		google.aceptar("token-2", new DatosGoogle("sub-ana", "ana.nueva@gmail.com", "Ana", null));

		Long otraVez = idDe(accessDe(loginGoogle("token-2", false).andExpect(status().isOk())));

		assertThat(otraVez).isEqualTo(id);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM usuarios", Integer.class)).isOne();
	}

	@Test
	void googleVinculaPorEmailAUnUsuarioVerificadoSinTocarSusDatos() throws Exception {
		Long id = insertarUsuario("juan@mail.com", "Juan", "$2a$hash-de-prueba", true);
		google.aceptar("token-juan", new DatosGoogle("sub-juan", "Juan@Mail.com", "Juan Carlos", "Pérez"));

		Long logueado = idDe(accessDe(loginGoogle("token-juan", false).andExpect(status().isOk())));

		assertThat(logueado).isEqualTo(id);
		assertThat(jdbc.queryForMap("SELECT google_sub, password_hash, nombre, apellido FROM usuarios WHERE id = ?", id))
				.containsEntry("google_sub", "sub-juan")
				.containsEntry("password_hash", "$2a$hash-de-prueba")
				.containsEntry("nombre", "Juan")
				.containsEntry("apellido", null);
	}

	@Test
	void googleSobreUnRegistroSinVerificarBorraLaPasswordYCierraSusSesiones() throws Exception {
		registrar("juan@mail.com");
		Cookie sesionDelRegistro = cookieDe(login("juan@mail.com", PASSWORD).andExpect(status().isOk()));
		google.aceptar("token-juan", new DatosGoogle("sub-juan", "juan@mail.com", "Juan", null));

		Cookie sesionGoogle = cookieDe(loginGoogle("token-juan", false).andExpect(status().isOk()));

		login("juan@mail.com", PASSWORD).andExpect(status().isUnauthorized());
		// La nueva primero: reusar la vieja (revocada) revoca toda la familia.
		refresh(sesionGoogle).andExpect(status().isOk());
		refresh(sesionDelRegistro).andExpect(status().isUnauthorized());
		assertThat(jdbc.queryForMap("SELECT email_verificado, password_hash FROM usuarios"))
				.containsEntry("email_verificado", true)
				.containsEntry("password_hash", null);
	}

	@Test
	void googleConTokenInvalidoDevuelve401SinCookie() throws Exception {
		loginGoogle("token-trucho", false)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value("No pudimos verificar tu cuenta de Google"))
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

		assertThat(jdbc.queryForObject("SELECT count(*) FROM usuarios", Integer.class)).isZero();
	}

	@Test
	void googleConUnMailYaVinculadoAOtraCuentaDeGoogleDevuelve401() throws Exception {
		Long id = insertarUsuario("ana@gmail.com", "Ana", null, true);
		jdbc.update("UPDATE usuarios SET google_sub = 'sub-viejo' WHERE id = ?", id);
		google.aceptar("token-otro", new DatosGoogle("sub-nuevo", "ana@gmail.com", "Ana", null));

		loginGoogle("token-otro", false).andExpect(status().isUnauthorized());

		assertThat(jdbc.queryForObject("SELECT google_sub FROM usuarios", String.class)).isEqualTo("sub-viejo");
	}

	@Test
	void googleSinTokenDevuelve400ConElCampo() throws Exception {
		mvc.perform(postJson("/api/v1/auth/google", "{\"mantenerSesion\":false}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.idToken").value("Falta el token de Google"));
	}

	// --- Código por mail ---

	@Test
	void codigoConMailNuevoCreaElUsuarioSinNombreYVerificado() throws Exception {
		solicitar("Nuevo@Mail.com")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mensaje").value("Si el mail es válido, te enviamos un código para entrar"));
		String codigo = envioMails.ultimoCodigo();
		assertThat(codigo).matches("\\d{6}");
		assertThat(envioMails.codigos.getLast().email()).isEqualTo("nuevo@mail.com");
		Map<String, Object> token = jdbc.queryForMap("""
				SELECT tipo, email, usuario_id, token_hash, intentos,
				       expira_en BETWEEN now() + interval '9 minutes' AND now() + interval '10 minutes' AS diez_minutos
				FROM tokens_verificacion""");
		assertThat(token)
				.containsEntry("tipo", "CODIGO_LOGIN")
				.containsEntry("email", "nuevo@mail.com")
				.containsEntry("usuario_id", null)
				.containsEntry("token_hash", TokenService.sha256(codigo))
				.containsEntry("intentos", 0)
				.containsEntry("diez_minutos", true);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM usuarios", Integer.class)).isZero();

		String access = accessDe(verificar("nuevo@mail.com", codigo, false).andExpect(status().isOk()));

		Long id = idDe(access);
		assertThat(jdbc.queryForMap("SELECT email, nombre, email_verificado, password_hash FROM usuarios"))
				.containsEntry("email", "nuevo@mail.com")
				.containsEntry("nombre", null)
				.containsEntry("email_verificado", true)
				.containsEntry("password_hash", null);
		assertThat(jdbc.queryForMap("SELECT usuario_id, usado_en IS NOT NULL AS usado FROM tokens_verificacion"))
				.containsEntry("usuario_id", id)
				.containsEntry("usado", true);
		me(access)
				.andExpect(jsonPath("$.nombre").isEmpty())
				.andExpect(jsonPath("$.perfilCompleto").value(false));
	}

	@Test
	void codigoConUsuarioExistenteEntraAlMismoUsuario() throws Exception {
		Long id = insertarUsuario("juan@mail.com", "Juan", "$2a$hash-de-prueba", true);

		solicitar("juan@mail.com").andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT usuario_id FROM tokens_verificacion", Long.class)).isEqualTo(id);
		String access = accessDe(verificar("JUAN@mail.com", envioMails.ultimoCodigo(), true).andExpect(status().isOk()));

		assertThat(idDe(access)).isEqualTo(id);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM usuarios", Integer.class)).isOne();
		// Ya estaba verificado: la contraseña se conserva.
		assertThat(jdbc.queryForObject("SELECT password_hash FROM usuarios", String.class)).isEqualTo("$2a$hash-de-prueba");
	}

	@Test
	void codigoSobreUnRegistroSinVerificarBorraLaPasswordYCierraSusSesiones() throws Exception {
		registrar("juan@mail.com");
		Cookie sesionDelRegistro = cookieDe(login("juan@mail.com", PASSWORD).andExpect(status().isOk()));

		Cookie sesionCodigo = cookieDe(entrarConCodigo("juan@mail.com"));

		login("juan@mail.com", PASSWORD).andExpect(status().isUnauthorized());
		// La nueva primero: reusar la vieja (revocada) revoca toda la familia.
		refresh(sesionCodigo).andExpect(status().isOk());
		refresh(sesionDelRegistro).andExpect(status().isUnauthorized());
		assertThat(jdbc.queryForMap("SELECT email_verificado, password_hash FROM usuarios"))
				.containsEntry("email_verificado", true)
				.containsEntry("password_hash", null);
	}

	@Test
	void codigoIncorrectoSumaUnIntentoQueQuedaGuardado() throws Exception {
		solicitar("ana@mail.com");
		String codigo = envioMails.ultimoCodigo();

		verificar("ana@mail.com", otroCodigo(codigo), false)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value(CODIGO_INVALIDO))
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

		assertThat(jdbc.queryForObject("SELECT intentos FROM tokens_verificacion", Short.class)).isEqualTo((short) 1);
		// Con un intento fallido, el correcto todavía sirve.
		verificar("ana@mail.com", codigo, false).andExpect(status().isOk());
	}

	@Test
	void conCincoIntentosFallidosElCodigoQuedaInvalido() throws Exception {
		solicitar("ana@mail.com");
		String codigo = envioMails.ultimoCodigo();

		for (int i = 0; i < AuthService.MAX_INTENTOS_CODIGO; i++) {
			verificar("ana@mail.com", otroCodigo(codigo), false).andExpect(status().isUnauthorized());
		}

		assertThat(jdbc.queryForObject("SELECT intentos FROM tokens_verificacion", Short.class)).isEqualTo((short) 5);
		verificar("ana@mail.com", codigo, false)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value(CODIGO_INVALIDO));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM usuarios", Integer.class)).isZero();
	}

	@Test
	void codigoVencidoDevuelve401() throws Exception {
		solicitar("ana@mail.com");
		jdbc.update("UPDATE tokens_verificacion SET expira_en = now() - interval '1 minute'");

		verificar("ana@mail.com", envioMails.ultimoCodigo(), false)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value(CODIGO_INVALIDO));
	}

	@Test
	void unCodigoNoSePuedeUsarDosVeces() throws Exception {
		solicitar("ana@mail.com");
		String codigo = envioMails.ultimoCodigo();
		verificar("ana@mail.com", codigo, false).andExpect(status().isOk());

		verificar("ana@mail.com", codigo, false)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value(CODIGO_INVALIDO));
	}

	@Test
	void solicitarUnCodigoNuevoInvalidaElAnterior() throws Exception {
		solicitar("ana@mail.com");
		String anterior = envioMails.ultimoCodigo();
		String nuevo;
		do {
			// Si por azar sale el mismo código, se pide otro: así el test no depende de la suerte.
			solicitar("ana@mail.com");
			nuevo = envioMails.ultimoCodigo();
		} while (nuevo.equals(anterior));

		verificar("ana@mail.com", anterior, false).andExpect(status().isUnauthorized());
		verificar("ana@mail.com", nuevo, false).andExpect(status().isOk());
		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM tokens_verificacion WHERE usado_en IS NULL AND expira_en > now()", Integer.class))
				.isZero();
	}

	@Test
	void solicitarRespondeIgualConMailExistenteEInexistente() throws Exception {
		registrar("juan@mail.com");

		String existente = solicitar("juan@mail.com").andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String inexistente = solicitar("nadie@mail.com").andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertThat(inexistente).isEqualTo(existente);
		assertThat(envioMails.codigos).hasSize(2);
		// En la base solo hay hashes, nunca los códigos.
		assertThat(jdbc.queryForList("SELECT token_hash FROM tokens_verificacion", String.class))
				.doesNotContainAnyElementsOf(envioMails.codigos.stream().map(c -> c.codigo()).toList());
	}

	@Test
	void solicitarConMailInvalidoDevuelve400() throws Exception {
		solicitar("no-es-un-mail")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.email").value("El mail no es válido"));
	}

	@Test
	void verificarConCodigoMalFormadoDevuelve400SinSumarIntentos() throws Exception {
		solicitar("ana@mail.com");

		verificar("ana@mail.com", "12ab", false)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.codigo").value("El código tiene 6 números"));

		assertThat(jdbc.queryForObject("SELECT intentos FROM tokens_verificacion", Short.class)).isZero();
	}

	// --- PATCH /me ---

	@Test
	void patchMeValidaNombreYCelular() throws Exception {
		String access = accessDe(entrarConCodigo("ana@mail.com"));

		patchMe(access, """
				{"nombre":"Ana","telefono":""}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.telefono").value("Ingresá tu celular"));
		patchMe(access, """
				{"nombre":"Ana","telefono":"llamame-ya"}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.telefono").value("El celular no es válido"));
		patchMe(access, """
				{"nombre":"Ana","telefono":"123"}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.telefono").value("El celular no es válido"));
		patchMe(access, """
				{"telefono":"1122334455"}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.nombre").value("Ingresá tu nombre"));

		assertThat(jdbc.queryForMap("SELECT nombre, telefono FROM usuarios"))
				.containsEntry("nombre", null)
				.containsEntry("telefono", null);
	}

	@Test
	void patchMeCompletaElPerfil() throws Exception {
		String access = accessDe(entrarConCodigo("ana@mail.com"));
		me(access).andExpect(jsonPath("$.perfilCompleto").value(false));

		patchMe(access, """
				{"nombre":"  Ana ","apellido":" ","telefono":"+54 9 11 2233-4455"}
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("Ana"))
				.andExpect(jsonPath("$.apellido").isEmpty())
				.andExpect(jsonPath("$.telefono").value("+54 9 11 2233-4455"))
				.andExpect(jsonPath("$.perfilCompleto").value(true));

		me(access)
				.andExpect(jsonPath("$.nombre").value("Ana"))
				.andExpect(jsonPath("$.perfilCompleto").value(true));
	}

	@Test
	void patchMeSinTokenDevuelve401() throws Exception {
		mvc.perform(patch("/api/v1/me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Ana\",\"telefono\":\"1122334455\"}"))
				.andExpect(status().isUnauthorized());
	}

	// --- Aislamiento ---

	@Test
	void elTokenDeUnClienteNoTraeBarberiaNiRol() throws Exception {
		// Hay una barbería con dueño, pero los clientes no son miembros de ninguna.
		Long dueno = insertarUsuario("dueno@mail.com", "Dueño", null, true);
		Long barberiaId = jdbc.queryForObject(
				"INSERT INTO barberias (nombre, slug) VALUES ('Barberia', 'barberia-uno') RETURNING id", Long.class);
		jdbc.update("INSERT INTO miembros_barberia (barberia_id, usuario_id, rol) VALUES (?, ?, 'DUENO')", barberiaId, dueno);
		google.aceptar("token-ana", new DatosGoogle("sub-ana", "ana@gmail.com", "Ana", null));

		String porGoogle = accessDe(loginGoogle("token-ana", false).andExpect(status().isOk()));
		String porCodigo = accessDe(entrarConCodigo("beto@mail.com"));

		for (String access : new String[] { porGoogle, porCodigo }) {
			Jwt jwt = jwtDecoder.decode(access);
			assertThat(jwt.hasClaim("barberiaId")).isFalse();
			assertThat(jwt.hasClaim("rol")).isFalse();
			me(access)
					.andExpect(jsonPath("$.barberiaId").isEmpty())
					.andExpect(jsonPath("$.rol").isEmpty());
		}
	}

	@Test
	void patchMeSoloModificaAlUsuarioDelTokenAunqueMandeOtroId() throws Exception {
		String accessAna = accessDe(entrarConCodigo("ana@mail.com"));
		String accessBeto = accessDe(entrarConCodigo("beto@mail.com"));
		Long idBeto = idDe(accessBeto);

		patchMe(accessAna, """
				{"id":%d,"email":"beto@mail.com","barberiaId":1,"rol":"DUENO",
				 "nombre":"Ana","telefono":"1122334455"}
				""".formatted(idBeto))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(idDe(accessAna)))
				.andExpect(jsonPath("$.email").value("ana@mail.com"))
				.andExpect(jsonPath("$.barberiaId").isEmpty());

		assertThat(jdbc.queryForMap("SELECT email, nombre, telefono FROM usuarios WHERE id = ?", idBeto))
				.containsEntry("email", "beto@mail.com")
				.containsEntry("nombre", null)
				.containsEntry("telefono", null);
	}

	// --- Helpers ---

	private ResultActions loginGoogle(String idToken, boolean mantenerSesion) throws Exception {
		return mvc.perform(postJson("/api/v1/auth/google", """
				{"idToken":"%s","mantenerSesion":%b}
				""".formatted(idToken, mantenerSesion)));
	}

	private ResultActions solicitar(String email) throws Exception {
		return mvc.perform(postJson("/api/v1/auth/codigo/solicitar", """
				{"email":"%s"}
				""".formatted(email)));
	}

	private ResultActions verificar(String email, String codigo, boolean mantenerSesion) throws Exception {
		return mvc.perform(postJson("/api/v1/auth/codigo/verificar", """
				{"email":"%s","codigo":"%s","mantenerSesion":%b}
				""".formatted(email, codigo, mantenerSesion)));
	}

	private ResultActions entrarConCodigo(String email) throws Exception {
		solicitar(email).andExpect(status().isOk());
		return verificar(email, envioMails.ultimoCodigo(), false).andExpect(status().isOk());
	}

	private ResultActions registrar(String email) throws Exception {
		return mvc.perform(postJson("/api/v1/auth/registro", """
				{"nombre":"Juan","email":"%s","telefono":"1122334455","password":"%s"}
				""".formatted(email, PASSWORD))).andExpect(status().isCreated());
	}

	private ResultActions login(String email, String password) throws Exception {
		return mvc.perform(postJson("/api/v1/auth/login", """
				{"email":"%s","password":"%s","mantenerSesion":false}
				""".formatted(email, password)));
	}

	private ResultActions refresh(Cookie cookie) throws Exception {
		return mvc.perform(post("/api/v1/auth/refresh").cookie(cookie));
	}

	private ResultActions me(String access) throws Exception {
		return mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
				.andExpect(status().isOk());
	}

	private ResultActions patchMe(String access, String json) throws Exception {
		return mvc.perform(patch("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private Long insertarUsuario(String email, String nombre, String passwordHash, boolean verificado) {
		return jdbc.queryForObject("""
				INSERT INTO usuarios (email, nombre, password_hash, email_verificado) VALUES (?, ?, ?, ?) RETURNING id
				""", Long.class, email, nombre, passwordHash, verificado);
	}

	private Long idDe(String access) {
		return Long.valueOf(jwtDecoder.decode(access).getSubject());
	}

	private static String otroCodigo(String codigo) {
		return codigo.equals("111111") ? "222222" : "111111";
	}

	private static MockHttpServletRequestBuilder postJson(String url, String json) {
		return post(url).contentType(MediaType.APPLICATION_JSON).content(json);
	}

	private static String accessDe(ResultActions resultado) throws Exception {
		return JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), "$.accessToken");
	}

	private static Cookie cookieDe(ResultActions resultado) throws Exception {
		Cookie cookie = resultado.andReturn().getResponse().getCookie("refresh_token");
		assertThat(cookie).isNotNull();
		return new Cookie(cookie.getName(), cookie.getValue());
	}

}
