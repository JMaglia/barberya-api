package com.barberya.config;

import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * API stateless: el access token (JWT HS256) viaja en el header Authorization.
 * El refresh token va en una cookie httpOnly limitada a /api/v1/auth.
 */
@Configuration
@EnableConfigurationProperties(BarberyaProperties.class)
public class SecurityConfig {

	public static final String EMISOR_JWT = "barberya-api";

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http,
			@Qualifier("corsConfigurationSource") CorsConfigurationSource cors,
			JwtAuthenticationConverter jwtAuthenticationConverter,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) throws Exception {
		// Los errores de seguridad los responde ManejadorErrores, en ProblemDetail y en español.
		AuthenticationEntryPoint sinSesion = (request, response, error) -> resolver.resolveException(request, response, null, error);
		AccessDeniedHandler sinPermiso = (request, response, error) -> resolver.resolveException(request, response, null, error);

		http
				// Sin sesión ni login por cookie: el único endpoint con cookie (refresh/logout) queda cubierto
				// por SameSite=Lax y CORS.
				.csrf(AbstractHttpConfigurer::disable)
				.cors(c -> c.configurationSource(cors))
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/api/v1/auth/**", "/api/v1/public/**").permitAll()
						.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(o -> o
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
						.authenticationEntryPoint(sinSesion)
						.accessDeniedHandler(sinPermiso))
				.exceptionHandling(e -> e.authenticationEntryPoint(sinSesion).accessDeniedHandler(sinPermiso));
		return http.build();
	}

	@Bean
	SecretKey claveJwt(BarberyaProperties props) {
		return new SecretKeySpec(props.jwt().secretBytes(), "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey claveJwt) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(claveJwt));
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey claveJwt) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(claveJwt).macAlgorithm(MacAlgorithm.HS256).build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(EMISOR_JWT));
		return decoder;
	}

	/** El claim {@code rol} (DUENO, BARBERO) pasa a ROLE_DUENO, ROLE_BARBERO. */
	@Bean
	JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
		roles.setAuthoritiesClaimName("rol");
		roles.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(roles);
		return converter;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(BarberyaProperties props) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(List.of(props.frontUrl()));
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		// Para que el navegador mande la cookie del refresh.
		config.setAllowCredentials(true);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", config);
		return source;
	}

}
