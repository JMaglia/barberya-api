package com.barberya.auth;

import java.time.Duration;
import java.util.Optional;

/** Cuenta intentos por clave en ventanas fijas. */
public interface LimitadorIntentos {

	/**
	 * Suma un intento a la clave, salvo que ya haya llegado al máximo: en ese caso no suma y devuelve cuánto falta
	 * para que la ventana venza. Es atómico.
	 */
	Optional<Duration> consumir(String clave, Limite limite);

	/** Borra el contador de la clave. */
	void reiniciar(String clave);

}
