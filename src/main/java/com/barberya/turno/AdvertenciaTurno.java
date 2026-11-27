package com.barberya.turno;

/** Avisos al cargar un turno a mano que no impiden guardarlo: el barbero es dueño de su agenda. */
public enum AdvertenciaTurno {
	/** No entra entero en una franja de atención de ese día. */
	FUERA_DE_HORARIO,
	/** Se superpone con un bloqueo de agenda. */
	PISA_BLOQUEO
}
