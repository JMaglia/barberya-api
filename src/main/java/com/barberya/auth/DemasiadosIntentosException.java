package com.barberya.auth;

import java.time.Duration;

/** Se pasó el límite de intentos; {@code reintentarEn} es lo que falta para que se libere. */
public class DemasiadosIntentosException extends RuntimeException {

	private final Duration reintentarEn;

	public DemasiadosIntentosException(Duration reintentarEn) {
		super("Hiciste demasiados intentos");
		this.reintentarEn = reintentarEn;
	}

	public Duration getReintentarEn() {
		return reintentarEn;
	}

}
