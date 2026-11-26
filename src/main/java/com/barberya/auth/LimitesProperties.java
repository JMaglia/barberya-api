package com.barberya.auth;

import java.time.Duration;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Límites de intentos de los endpoints de auth ({@code barberya.limites.*}). */
@ConfigurationProperties(prefix = "barberya.limites")
public record LimitesProperties(PorEndpoint login, PorEndpoint codigoSolicitar, PorEndpoint codigoVerificar,
		PorEndpoint recuperar, PorEndpoint registro, PorEndpoint google) {

	public LimitesProperties {
		exigirAmbos("login", login);
		exigirAmbos("codigo-solicitar", codigoSolicitar);
		exigirAmbos("codigo-verificar", codigoVerificar);
		exigirAmbos("recuperar", recuperar);
		exigirIp("registro", registro);
		exigirIp("google", google);
	}

	Duration ventanaMasLarga() {
		return Stream.of(login, codigoSolicitar, codigoVerificar, recuperar, registro, google)
				.flatMap(p -> Stream.of(p.porMail(), p.porIp()))
				.filter(Objects::nonNull)
				.map(Limite::ventana)
				.max(Duration::compareTo)
				.orElseThrow();
	}

	private static void exigirAmbos(String nombre, PorEndpoint endpoint) {
		exigirIp(nombre, endpoint);
		if (endpoint.porMail() == null) {
			throw new IllegalStateException("Falta barberya.limites." + nombre + ".por-mail");
		}
	}

	private static void exigirIp(String nombre, PorEndpoint endpoint) {
		if (endpoint == null || endpoint.porIp() == null) {
			throw new IllegalStateException("Falta barberya.limites." + nombre + ".por-ip");
		}
	}

	/** {@code porMail} es null en los endpoints que solo se limitan por IP. */
	public record PorEndpoint(Limite porMail, Limite porIp) {
	}

}
