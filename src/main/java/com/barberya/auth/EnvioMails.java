package com.barberya.auth;

/**
 * Envío de mails a los usuarios. La implementación real es {@link EnvioMailsSmtp}.
 * {@code nombre} puede ser null (cuenta sin nombre todavía o mail sin cuenta): el saludo queda en "Hola".
 */
public interface EnvioMails {

	void enviarRecuperacionClave(String email, String nombre, String link);

	void enviarCodigo(String email, String nombre, String codigo);

}
