package com.barberya.config;

import java.util.List;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Doc de la API en {@code /swagger-ui.html}. El botón "Authorize" recibe el access token;
 * los endpoints públicos quedan sin candado. En el perfil {@code prod} springdoc está apagado.
 */
@Configuration
public class OpenApiConfig {

	static final String ESQUEMA_JWT = "bearer-jwt";

	/** Tienen que coincidir con las rutas con permitAll de SecurityConfig. */
	private static final List<String> RUTAS_PUBLICAS = List.of("/api/v1/auth/", "/api/v1/public/");

	@Bean
	OpenAPI openApi() {
		return new OpenAPI()
				.info(new Info()
						.title("BarberYA API")
						.version("v1")
						.description("Turnos online para barberías: login, barbería, servicios, horarios y datos públicos de reserva."))
				.components(new Components().addSecuritySchemes(ESQUEMA_JWT, new SecurityScheme()
						.type(SecurityScheme.Type.HTTP)
						.scheme("bearer")
						.bearerFormat("JWT")));
	}

	/** Candado en todo lo que no sea público, así cada endpoint nuevo lo trae solo. */
	@Bean
	OpenApiCustomizer seguridadPorRuta() {
		return openApi -> {
			if (openApi.getPaths() == null) {
				return;
			}
			openApi.getPaths().forEach((ruta, item) -> {
				if (RUTAS_PUBLICAS.stream().noneMatch(ruta::startsWith)) {
					item.readOperations().forEach(op -> op.addSecurityItem(new SecurityRequirement().addList(ESQUEMA_JWT)));
				}
			});
		};
	}

}
