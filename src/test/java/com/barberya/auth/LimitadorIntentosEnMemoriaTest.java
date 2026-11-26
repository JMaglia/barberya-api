package com.barberya.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.barberya.RelojDeTest;

class LimitadorIntentosEnMemoriaTest {

	private static final Instant AHORA = Instant.parse("2026-11-26T15:00:00Z");
	private static final Limite TRES_EN_15_MIN = new Limite(3, Duration.ofMinutes(15));

	private final RelojDeTest reloj = new RelojDeTest();
	private LimitadorIntentosEnMemoria limitador;

	@BeforeEach
	void crear() {
		reloj.fijar(AHORA);
		limitador = new LimitadorIntentosEnMemoria(Duration.ofHours(1), reloj);
	}

	@Test
	void dejaHastaElMaximoYDespuesCortaConElTiempoQueFalta() {
		for (int i = 0; i < 3; i++) {
			assertThat(limitador.consumir("clave", TRES_EN_15_MIN)).isEmpty();
		}
		reloj.fijar(AHORA.plus(Duration.ofMinutes(5)));

		assertThat(limitador.consumir("clave", TRES_EN_15_MIN)).contains(Duration.ofMinutes(10));
	}

	@Test
	void seLiberaAlVencerLaVentana() {
		for (int i = 0; i < 3; i++) {
			limitador.consumir("clave", TRES_EN_15_MIN);
		}
		reloj.fijar(AHORA.plus(Duration.ofMinutes(15)).minusSeconds(1));
		assertThat(limitador.consumir("clave", TRES_EN_15_MIN)).contains(Duration.ofSeconds(1));

		reloj.fijar(AHORA.plus(Duration.ofMinutes(15)));
		assertThat(limitador.consumir("clave", TRES_EN_15_MIN)).isEmpty();
		// Ventana nueva: arrancó en 1 y le quedan 2.
		assertThat(limitador.consumir("clave", TRES_EN_15_MIN)).isEmpty();
		assertThat(limitador.consumir("clave", TRES_EN_15_MIN)).isEmpty();
		assertThat(limitador.consumir("clave", TRES_EN_15_MIN)).isPresent();
	}

	@Test
	void reiniciarVuelveADejar() {
		for (int i = 0; i < 3; i++) {
			limitador.consumir("login:mail:ana@mail.com", TRES_EN_15_MIN);
		}
		assertThat(limitador.consumir("login:mail:ana@mail.com", TRES_EN_15_MIN)).isPresent();

		limitador.reiniciar("login:mail:ana@mail.com");

		assertThat(limitador.consumir("login:mail:ana@mail.com", TRES_EN_15_MIN)).isEmpty();
	}

	@Test
	void clavesDistintasNoSeMezclan() {
		for (int i = 0; i < 3; i++) {
			limitador.consumir("login:ip:10.0.0.1", TRES_EN_15_MIN);
		}
		assertThat(limitador.consumir("login:ip:10.0.0.1", TRES_EN_15_MIN)).isPresent();

		assertThat(limitador.consumir("login:ip:10.0.0.2", TRES_EN_15_MIN)).isEmpty();
		assertThat(limitador.consumir("registro:ip:10.0.0.1", TRES_EN_15_MIN)).isEmpty();
	}

	@Test
	void siElRelojVuelveParaAtrasEmpiezaUnaVentanaNueva() {
		for (int i = 0; i < 3; i++) {
			limitador.consumir("clave", TRES_EN_15_MIN);
		}
		reloj.fijar(AHORA.minus(Duration.ofDays(1)));

		assertThat(limitador.consumir("clave", TRES_EN_15_MIN)).isEmpty();
	}

}
