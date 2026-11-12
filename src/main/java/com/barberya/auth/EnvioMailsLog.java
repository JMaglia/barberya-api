package com.barberya.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * SOLO DESARROLLO: en vez de mandar el mail, loguea el link de recuperación (sin el mail del usuario).
 * No existe con el perfil {@code prod}: así la app no arranca sin un envío real y los links nunca quedan
 * en los logs de producción.
 */
@Slf4j
@Component
@Profile("!prod")
public class EnvioMailsLog implements EnvioMails {

	@Override
	public void enviarRecuperacionClave(String email, String link) {
		log.info("[DEV] Link para restablecer la contraseña: {}", link);
	}

}
