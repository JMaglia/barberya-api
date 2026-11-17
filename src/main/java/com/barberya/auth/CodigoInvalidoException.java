package com.barberya.auth;

public class CodigoInvalidoException extends RuntimeException {

	public CodigoInvalidoException() {
		super("El código no es válido o venció");
	}

}
