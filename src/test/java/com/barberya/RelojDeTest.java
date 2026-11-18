package com.barberya;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Clock que los tests pueden fijar. Por defecto anda con la hora real; quien lo fija lo tiene que
 * devolver con {@link #usarReal()} al terminar, porque el bean se comparte entre tests.
 */
public class RelojDeTest extends Clock {

	private volatile Instant fijo;

	public void fijar(Instant instante) {
		this.fijo = instante;
	}

	public void usarReal() {
		this.fijo = null;
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zona) {
		Instant actual = instant();
		return Clock.fixed(actual, zona);
	}

	@Override
	public Instant instant() {
		Instant valor = fijo;
		return valor != null ? valor : Instant.now();
	}

}
