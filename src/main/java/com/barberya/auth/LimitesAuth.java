package com.barberya.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.barberya.auth.LimitesProperties.PorEndpoint;

/**
 * Aplica los límites de intentos de cada endpoint de auth, antes de tocar la base o BCrypt. Los contadores por mail
 * cuentan los pedidos exista o no la cuenta, así el 429 no revela nada.
 */
@Component
public class LimitesAuth {

	private static final Logger log = LoggerFactory.getLogger(LimitesAuth.class);

	private final LimitadorIntentos limitador;
	private final LimitesProperties limites;

	public LimitesAuth(LimitadorIntentos limitador, LimitesProperties limites) {
		this.limitador = limitador;
		this.limites = limites;
	}

	/** Cuenta cada intento por mail; un login exitoso lo reinicia, así quedan los fallidos desde el último éxito. */
	public void login(String email, String ip) {
		porIpYMail("login", limites.login(), ip, email);
	}

	public void loginExitoso(String email) {
		limitador.reiniciar(claveMail("login", email));
	}

	public void solicitarCodigo(String email, String ip) {
		porIpYMail("codigo/solicitar", limites.codigoSolicitar(), ip, email);
	}

	public void verificarCodigo(String email, String ip) {
		porIpYMail("codigo/verificar", limites.codigoVerificar(), ip, email);
	}

	public void recuperar(String email, String ip) {
		porIpYMail("recuperar", limites.recuperar(), ip, email);
	}

	public void registro(String ip) {
		porIp("registro", limites.registro(), ip);
	}

	public void google(String ip) {
		porIp("google", limites.google(), ip);
	}

	private void porIpYMail(String endpoint, PorEndpoint limite, String ip, String email) {
		porIp(endpoint, limite, ip);
		consumir(endpoint, "mail", claveMail(endpoint, email), limite.porMail());
	}

	private void porIp(String endpoint, PorEndpoint limite, String ip) {
		consumir(endpoint, "ip", endpoint + ":ip:" + ip, limite.porIp());
	}

	private void consumir(String endpoint, String tipoClave, String clave, Limite limite) {
		limitador.consumir(clave, limite).ifPresent(espera -> {
			// Sin el mail ni la IP: solo el endpoint y el tipo de clave.
			log.warn("Límite de intentos superado en /auth/{} por {}", endpoint, tipoClave);
			throw new DemasiadosIntentosException(espera);
		});
	}

	private static String claveMail(String endpoint, String email) {
		return endpoint + ":mail:" + AuthService.normalizarEmail(email);
	}

}
