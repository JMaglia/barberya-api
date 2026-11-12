package com.barberya.auth;

public class TokenRecuperacionInvalidoException extends RuntimeException {

	public TokenRecuperacionInvalidoException() {
		super("El link para restablecer la contraseña no es válido o venció");
	}

}
