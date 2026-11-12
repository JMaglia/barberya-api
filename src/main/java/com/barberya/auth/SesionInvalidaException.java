package com.barberya.auth;

/** Refresh token ausente, vencido, revocado o desconocido. */
public class SesionInvalidaException extends RuntimeException {

	public SesionInvalidaException() {
		super("Tu sesión venció, volvé a iniciar sesión");
	}

}
