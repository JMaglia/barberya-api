package com.barberya.mail;

/**
 * Evento con un mail ya renderizado, listo para mandar. Lo manda {@link DespachadorMails} después del commit.
 *
 * @param tipo nombre de la plantilla; es lo único que se loguea
 */
public record MailSaliente(String tipo, String destinatario, String asunto, String html, String texto) {

	/** Sin destinatario ni cuerpo: tienen datos personales, códigos o links. */
	@Override
	public String toString() {
		return "MailSaliente[tipo=" + tipo + "]";
	}

}
