package com.barberya.auth;

public class GoogleInvalidoException extends RuntimeException {

	public GoogleInvalidoException() {
		super("No pudimos verificar tu cuenta de Google");
	}

}
