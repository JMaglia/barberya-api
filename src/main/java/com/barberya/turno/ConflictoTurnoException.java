package com.barberya.turno;

/** 409 al reservar o cancelar, con un {@link CodigoConflictoTurno}. */
public class ConflictoTurnoException extends RuntimeException {

	private final CodigoConflictoTurno codigo;

	public ConflictoTurnoException(CodigoConflictoTurno codigo, String mensaje) {
		super(mensaje);
		this.codigo = codigo;
	}

	public CodigoConflictoTurno getCodigo() {
		return codigo;
	}

	static ConflictoTurnoException horarioNoDisponible() {
		return new ConflictoTurnoException(CodigoConflictoTurno.HORARIO_NO_DISPONIBLE, "Ese horario ya no está disponible");
	}

	static ConflictoTurnoException noCancelable() {
		return new ConflictoTurnoException(CodigoConflictoTurno.NO_CANCELABLE, "Este turno ya no se puede cancelar");
	}

}
