package com.barberya.config;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuración propia de la app ({@code barberya.*}). */
@ConfigurationProperties(prefix = "barberya")
public record BarberyaProperties(Jwt jwt, String frontUrl) {

	public BarberyaProperties {
		if (frontUrl == null || frontUrl.isBlank()) {
			throw new IllegalStateException("Falta barberya.front-url (variable FRONT_URL)");
		}
	}

	public record Jwt(String secret, int accessMinutos) {

		/** HS256 necesita una clave de al menos 256 bits. */
		private static final int MIN_BYTES_SECRETO = 32;

		public Jwt {
			if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_BYTES_SECRETO) {
				throw new IllegalStateException(
						"JWT_SECRET falta o es muy corto: tiene que tener al menos " + MIN_BYTES_SECRETO + " bytes");
			}
			if (accessMinutos <= 0) {
				throw new IllegalStateException("barberya.jwt.access-minutos tiene que ser mayor que 0");
			}
		}

		public byte[] secretBytes() {
			return secret.getBytes(StandardCharsets.UTF_8);
		}
	}

}
