package com.barberya;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.barberya.auth.AuthFalsosConfig;
import com.barberya.auth.TokenService;
import com.barberya.auth.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * Base de los tests de endpoints con login: usuarios insertados por JDBC y tokens firmados con el
 * {@link TokenService} real. Importa {@link AuthFalsosConfig} para compartir el contexto con los tests de auth.
 */
@AutoConfigureMockMvc
@Import(AuthFalsosConfig.class)
public abstract class ApiIntegracionTest extends IntegracionConLimpiezaTest {

	@Autowired
	protected MockMvc mvc;
	@Autowired
	private TokenService tokenService;
	@Autowired
	private UsuarioRepository usuarios;

	protected Long insertarUsuario(String email) {
		return jdbc.queryForObject("INSERT INTO usuarios (email, nombre, email_verificado) VALUES (?, 'Test', TRUE) RETURNING id",
				Long.class, email);
	}

	/** Access token con lo que haya hoy en la base (barberiaId y rol si es miembro). */
	protected String tokenDe(Long usuarioId) {
		return tokenService.generarAccess(usuarios.findById(usuarioId).orElseThrow()).accessToken();
	}

	/** Crea un usuario y su barbería por la API; devuelve el token nuevo, que ya trae barberiaId. */
	protected String duenoConBarberia(String email, String nombreBarberia) throws Exception {
		String token = tokenDe(insertarUsuario(email));
		String respuesta = crearBarberia(token, """
				{"nombre":"%s","telefono":"1122334455"}
				""".formatted(nombreBarberia))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(respuesta, "$.token.accessToken");
	}

	protected ResultActions crearBarberia(String token, String json) throws Exception {
		return mvc.perform(conToken(post("/api/v1/barberias"), token).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	protected ResultActions getCon(String token, String url) throws Exception {
		return mvc.perform(conToken(get(url), token));
	}

	protected ResultActions postCon(String token, String url, String json) throws Exception {
		return mvc.perform(conToken(post(url), token).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	protected ResultActions postCon(String token, String url) throws Exception {
		return mvc.perform(conToken(post(url), token));
	}

	protected ResultActions patchCon(String token, String url, String json) throws Exception {
		return mvc.perform(conToken(patch(url), token).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	protected ResultActions putCon(String token, String url, String json) throws Exception {
		return mvc.perform(conToken(put(url), token).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	protected ResultActions deleteCon(String token, String url) throws Exception {
		return mvc.perform(conToken(delete(url), token));
	}

	protected Long barberiaIdDeSlug(String slug) {
		return jdbc.queryForObject("SELECT id FROM barberias WHERE slug = ?", Long.class, slug);
	}

	protected Long miembroIdDeSlug(String slug) {
		return jdbc.queryForObject("""
				SELECT m.id FROM miembros_barberia m JOIN barberias b ON b.id = m.barberia_id WHERE b.slug = ?
				""", Long.class, slug);
	}

	private static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder request, String token) {
		return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}

}
