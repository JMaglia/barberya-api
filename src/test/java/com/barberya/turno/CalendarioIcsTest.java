package com.barberya.turno;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class CalendarioIcsTest {

	@Test
	void escapaBarraPuntoYComaComaYSaltosDeLinea() {
		assertThat(CalendarioIcs.escapar("a\\b;c,d\r\ne\nf")).isEqualTo("a\\\\b\\;c\\,d\\ne\\nf");
	}

	@Test
	void unaLineaCortaNoSePliega() {
		assertThat(CalendarioIcs.plegar("SUMMARY:Corte en Barbería A")).isEqualTo("SUMMARY:Corte en Barbería A");
	}

	@Test
	void plegaA75OctetosSinCortarCaracteresMultibyte() {
		String linea = "LOCATION:" + "Peñaloza ñandú ".repeat(12);

		String plegada = CalendarioIcs.plegar(linea);

		String[] renglones = plegada.split("\r\n");
		assertThat(renglones).hasSizeGreaterThan(1);
		for (int i = 0; i < renglones.length; i++) {
			assertThat(renglones[i].getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
			assertThat(renglones[i]).doesNotContain("�");
			if (i > 0) {
				assertThat(renglones[i]).startsWith(" ");
			}
		}
		// Desplegar (sacar CRLF + espacio) devuelve el original.
		assertThat(plegada.replace("\r\n ", "")).isEqualTo(linea);
	}

}
