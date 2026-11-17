package com.barberya.auth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Dobles de los tests de auth: no mandan mails ni llaman a Google. Las clases que los importan comparten
 * el mismo contexto de Spring; cada test tiene que llamar a {@code limpiar()} antes de empezar.
 */
@TestConfiguration(proxyBeanMethods = false)
public class AuthFalsosConfig {

	@Bean
	@Primary
	EnvioMailsFalso envioMailsFalso() {
		return new EnvioMailsFalso();
	}

	@Bean
	@Primary
	VerificadorGoogleFalso verificadorGoogleFalso() {
		return new VerificadorGoogleFalso();
	}

	/** Guarda los links y los códigos en vez de mandarlos, para que el test los use. */
	public static class EnvioMailsFalso implements EnvioMails {

		public final List<String> links = new ArrayList<>();
		public final List<CodigoEnviado> codigos = new ArrayList<>();

		@Override
		public void enviarRecuperacionClave(String email, String link) {
			links.add(link);
		}

		@Override
		public void enviarCodigo(String email, String codigo) {
			codigos.add(new CodigoEnviado(email, codigo));
		}

		public String ultimoCodigo() {
			return codigos.getLast().codigo();
		}

		public void limpiar() {
			links.clear();
			codigos.clear();
		}

		public record CodigoEnviado(String email, String codigo) {
		}
	}

	/** Solo acepta los ID tokens registrados con {@code aceptar}; cualquier otro es inválido. */
	public static class VerificadorGoogleFalso implements VerificadorGoogle {

		private final Map<String, DatosGoogle> tokens = new HashMap<>();

		public void aceptar(String idToken, DatosGoogle datos) {
			tokens.put(idToken, datos);
		}

		@Override
		public DatosGoogle verificar(String idToken) {
			DatosGoogle datos = tokens.get(idToken);
			if (datos == null) {
				throw new GoogleInvalidoException();
			}
			return datos;
		}

		public void limpiar() {
			tokens.clear();
		}
	}

}
