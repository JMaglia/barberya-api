package com.barberya.config;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuración propia de la app ({@code barberya.*}). */
@ConfigurationProperties(prefix = "barberya")
public record BarberyaProperties(Jwt jwt, String frontUrl, Google google, Mail mail) {

	public BarberyaProperties {
		if (frontUrl == null || frontUrl.isBlank()) {
			throw new IllegalStateException("Falta barberya.front-url (variable FRONT_URL)");
		}
		if (google == null) {
			throw new IllegalStateException("Falta barberya.google.client-id (variable GOOGLE_CLIENT_ID)");
		}
		if (mail == null) {
			throw new IllegalStateException("Falta barberya.mail.remitente (variable MAIL_FROM)");
		}
	}

	/** {@code remitente}: el From de los mails, por ejemplo {@code BarberYA <no-responder@barberya.app>}. */
	public record Mail(String remitente) {

		public Mail {
			if (remitente == null || remitente.isBlank()) {
				throw new IllegalStateException("Falta barberya.mail.remitente (variable MAIL_FROM)");
			}
		}
	}

	/** {@code clientId}: el aud que tienen que traer los ID tokens de Google. */
	public record Google(String clientId) {

		public Google {
			if (clientId == null || clientId.isBlank()) {
				throw new IllegalStateException("Falta barberya.google.client-id (variable GOOGLE_CLIENT_ID)");
			}
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
