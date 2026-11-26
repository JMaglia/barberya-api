package com.barberya.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Contadores en memoria: alcanza con una sola instancia de la app. El tiempo de las ventanas sale del {@link Clock};
 * la expiración de Caffeine (con su propio reloj) solo limpia memoria, por eso dura lo mismo que la ventana más larga.
 */
@Component
public class LimitadorIntentosEnMemoria implements LimitadorIntentos {

	private static final long MAX_CLAVES = 100_000;

	private final Cache<String, Ventana> ventanas;
	private final Clock reloj;

	@Autowired
	public LimitadorIntentosEnMemoria(LimitesProperties limites, Clock reloj) {
		this(limites.ventanaMasLarga(), reloj);
	}

	LimitadorIntentosEnMemoria(Duration ventanaMasLarga, Clock reloj) {
		this.ventanas = Caffeine.newBuilder()
				.expireAfterAccess(ventanaMasLarga)
				.maximumSize(MAX_CLAVES)
				.build();
		this.reloj = reloj;
	}

	@Override
	public Optional<Duration> consumir(String clave, Limite limite) {
		Instant ahora = reloj.instant();
		AtomicReference<Duration> espera = new AtomicReference<>();
		ventanas.asMap().compute(clave, (k, actual) -> {
			if (actual == null || actual.vencida(ahora)) {
				return new Ventana(ahora, limite.ventana(), 1);
			}
			if (actual.cuenta() >= limite.maximo()) {
				espera.set(Duration.between(ahora, actual.fin()));
				return actual;
			}
			return actual.sumar();
		});
		return Optional.ofNullable(espera.get());
	}

	@Override
	public void reiniciar(String clave) {
		ventanas.invalidate(clave);
	}

	/** Para los tests: vacía todos los contadores. */
	public void limpiar() {
		ventanas.invalidateAll();
	}

	private record Ventana(Instant inicio, Duration duracion, int cuenta) {

		Instant fin() {
			return inicio.plus(duracion);
		}

		/** También si el reloj volvió para atrás (pasa en los tests). */
		boolean vencida(Instant ahora) {
			return !ahora.isBefore(fin()) || ahora.isBefore(inicio);
		}

		Ventana sumar() {
			return new Ventana(inicio, duracion, cuenta + 1);
		}
	}

}
