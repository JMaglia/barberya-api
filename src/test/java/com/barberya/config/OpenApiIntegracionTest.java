package com.barberya.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.barberya.ApiIntegracionTest;

class OpenApiIntegracionTest extends ApiIntegracionTest {

	@Test
	void apiDocsSinTokenConJwtSoloEnLoProtegido() throws Exception {
		mvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("BarberYA API")))
				.andExpect(jsonPath("$.components.securitySchemes['bearer-jwt'].scheme").value("bearer"))
				.andExpect(jsonPath("$.paths['/api/v1/me'].get.security").exists())
				.andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/v1/public/barberias/{slug}'].get.security").doesNotExist());
	}

	@Test
	void swaggerUiSinToken() throws Exception {
		mvc.perform(get("/swagger-ui/index.html"))
				.andExpect(status().isOk());
		mvc.perform(get("/swagger-ui.html"))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string(HttpHeaders.LOCATION, containsString("/swagger-ui/index.html")));
	}

	@Test
	void endpointProtegidoSigueExigiendoToken() throws Exception {
		mvc.perform(get("/api/v1/me"))
				.andExpect(status().isUnauthorized());
	}

}
