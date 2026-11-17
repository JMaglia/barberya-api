package com.barberya.error;

/** 404. También para datos de otra barbería, para no revelar que existen. */
public class RecursoNoEncontradoException extends RuntimeException {

	public RecursoNoEncontradoException(String mensaje) {
		super(mensaje);
	}

}
