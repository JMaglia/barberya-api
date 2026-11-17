package com.barberya.auth;

/** Dos altas simultáneas del mismo mail (o de la misma cuenta de Google): la segunda choca con el UNIQUE. */
public class CuentaDuplicadaException extends RuntimeException {

	public CuentaDuplicadaException() {
		super("Ya existe una cuenta con ese mail, probá entrar de nuevo");
	}

}
