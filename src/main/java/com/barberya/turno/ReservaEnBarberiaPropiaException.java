package com.barberya.turno;

/** 400: un miembro no reserva como cliente en su propia barbería; sus turnos los carga desde la agenda. */
public class ReservaEnBarberiaPropiaException extends RuntimeException {

	public ReservaEnBarberiaPropiaException() {
		super("Para cargar turnos en tu barbería usá la agenda");
	}

}
