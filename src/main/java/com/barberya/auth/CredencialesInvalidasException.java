package com.barberya.auth;

/** Mensaje genérico: no revela si el mail existe. */
public class CredencialesInvalidasException extends RuntimeException {

	public CredencialesInvalidasException() {
		super("Mail o contraseña incorrectos");
	}

}
