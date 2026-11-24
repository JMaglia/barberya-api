package com.barberya.turno;

/**
 * 409: dos reservas simultáneas del mismo usuario intentaron crear su ficha a la vez. Con el lock del usuario no
 * debería pasar; queda para no responder 500 si pasa.
 */
public class FichaEnCursoException extends RuntimeException {

	public FichaEnCursoException() {
		super("Ya estamos procesando otra reserva tuya, probá de nuevo");
	}

}
