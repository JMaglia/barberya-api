package com.barberya.turno;

/** Va en la propiedad {@code codigo} de los 409 de turnos, para que el front sepa qué hacer sin leer el texto. */
public enum CodigoConflictoTurno {
	PERFIL_INCOMPLETO, TOPE_TURNOS, HORARIO_NO_DISPONIBLE, FUERA_DE_PLAZO, NO_CANCELABLE
}
