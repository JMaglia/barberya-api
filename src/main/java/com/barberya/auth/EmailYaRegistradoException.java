package com.barberya.auth;

public class EmailYaRegistradoException extends RuntimeException {

	public EmailYaRegistradoException() {
		super("Ya existe una cuenta con ese mail");
	}

}
