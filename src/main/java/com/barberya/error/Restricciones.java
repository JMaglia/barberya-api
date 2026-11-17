package com.barberya.error;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/** Para mapear una violación de la base a un error claro según la restricción que saltó. */
public final class Restricciones {

	private Restricciones() {
	}

	public static boolean es(DataIntegrityViolationException e, String restriccion) {
		return e.getCause() instanceof ConstraintViolationException cve && restriccion.equals(cve.getConstraintName());
	}

}
