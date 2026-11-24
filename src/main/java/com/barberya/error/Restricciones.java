package com.barberya.error;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/** Para mapear una violación de la base a un error claro según la restricción que saltó. */
public final class Restricciones {

	private Restricciones() {
	}

	/**
	 * Hibernate no extrae el nombre de todas las restricciones (por ejemplo, las de exclusión, SQLState 23P01): en ese
	 * caso se busca en el mensaje de Postgres, que lo trae entre comillas.
	 */
	public static boolean es(DataIntegrityViolationException e, String restriccion) {
		if (!(e.getCause() instanceof ConstraintViolationException cve)) {
			return false;
		}
		if (cve.getConstraintName() != null) {
			return restriccion.equals(cve.getConstraintName());
		}
		String mensaje = cve.getSQLException() != null ? cve.getSQLException().getMessage() : null;
		return mensaje != null && mensaje.contains("\"" + restriccion + "\"");
	}

}
