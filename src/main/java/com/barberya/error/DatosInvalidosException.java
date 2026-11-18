package com.barberya.error;

/** 400 para reglas que cruzan campos y no entran en Bean Validation. Se responde con el mismo formato ({@code errores}). */
public class DatosInvalidosException extends RuntimeException {

	private final String campo;

	public DatosInvalidosException(String campo, String mensaje) {
		super(mensaje);
		this.campo = campo;
	}

	public String getCampo() {
		return campo;
	}

}
