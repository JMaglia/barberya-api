package com.barberya.auth;

/** Envío de mails a los usuarios. En producción tiene que haber una implementación real. */
public interface EnvioMails {

	void enviarRecuperacionClave(String email, String link);

}
