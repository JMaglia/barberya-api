package com.barberya.auth;

import java.time.Duration;

/** Como máximo {@code maximo} intentos en cada ventana fija de {@code ventana}. */
public record Limite(int maximo, Duration ventana) {

	public Limite {
		if (maximo <= 0) {
			throw new IllegalStateException("El máximo de un límite de intentos tiene que ser mayor que 0");
		}
		if (ventana == null || ventana.isNegative() || ventana.isZero()) {
			throw new IllegalStateException("La ventana de un límite de intentos tiene que ser mayor que 0");
		}
	}

}
