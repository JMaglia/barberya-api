package com.barberya.validacion;

/** Validación compartida de teléfonos y celulares (perfil del usuario y barbería). */
public final class Telefono {

	/** Acepta el vacío para que ese caso lo reporte solo {@code @NotBlank}, con su mensaje. */
	public static final String REGEX = "^$|^\\+?[0-9 ()-]{8,29}$";
	public static final String MENSAJE = "El celular no es válido";

	private Telefono() {
	}

}
