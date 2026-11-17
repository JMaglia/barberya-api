package com.barberya.servicio;

public class ServicioDuplicadoException extends RuntimeException {

	public ServicioDuplicadoException() {
		super("Ya tenés un servicio activo con ese nombre");
	}

}
