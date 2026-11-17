package com.barberya.auth;

import java.util.List;
import java.util.Set;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import com.barberya.config.BarberyaProperties;

/**
 * Verifica la firma con las claves públicas de Google (Nimbus las baja y las cachea) y los claims iss, aud, exp y
 * email_verified. El decoder se arma acá adentro y no como {@code @Bean}, para no pisar el JwtDecoder de la API.
 */
@Component
public class VerificadorGoogleNimbus implements VerificadorGoogle {

	private static final String CLAVES_GOOGLE = "https://www.googleapis.com/oauth2/v3/certs";
	private static final Set<String> EMISORES = Set.of("accounts.google.com", "https://accounts.google.com");
	private static final int MAX_NOMBRE = 80;

	private final NimbusJwtDecoder decoder;

	public VerificadorGoogleNimbus(BarberyaProperties props) {
		String clientId = props.google().clientId();
		this.decoder = NimbusJwtDecoder.withJwkSetUri(CLAVES_GOOGLE).build();
		this.decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				new JwtTimestampValidator(),
				new JwtClaimValidator<Object>("iss", iss -> iss != null && EMISORES.contains(iss.toString())),
				new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(clientId)),
				// Google lo manda como boolean; se acepta también el string, por las dudas.
				new JwtClaimValidator<Object>("email_verified", v -> v != null && "true".equals(v.toString()))));
	}

	@Override
	public DatosGoogle verificar(String idToken) {
		Jwt jwt;
		try {
			jwt = decoder.decode(idToken);
		} catch (JwtException e) {
			throw new GoogleInvalidoException();
		}
		String email = jwt.getClaimAsString("email");
		if (jwt.getSubject() == null || email == null || email.isBlank()) {
			throw new GoogleInvalidoException();
		}
		String nombre = jwt.getClaimAsString("given_name");
		if (nombre == null || nombre.isBlank()) {
			nombre = jwt.getClaimAsString("name");
		}
		return new DatosGoogle(jwt.getSubject(), email, recortar(nombre), recortar(jwt.getClaimAsString("family_name")));
	}

	private static String recortar(String valor) {
		if (valor == null || valor.isBlank()) {
			return null;
		}
		String limpio = valor.trim();
		return limpio.length() > MAX_NOMBRE ? limpio.substring(0, MAX_NOMBRE) : limpio;
	}

}
