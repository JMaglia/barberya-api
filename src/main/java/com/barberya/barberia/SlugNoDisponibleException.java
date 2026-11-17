package com.barberya.barberia;

public class SlugNoDisponibleException extends RuntimeException {

	public static SlugNoDisponibleException ocupado() {
		return new SlugNoDisponibleException("Ese link ya está en uso");
	}

	public static SlugNoDisponibleException reservado() {
		return new SlugNoDisponibleException("Ese link no está disponible");
	}

	private SlugNoDisponibleException(String mensaje) {
		super(mensaje);
	}

}
