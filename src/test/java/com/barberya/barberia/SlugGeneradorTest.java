package com.barberya.barberia;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class SlugGeneradorTest {

	@Test
	void sacaTildesYEspacios() {
		assertThat(SlugGenerador.generar("  Peluquería Ñandú & Café!! ", s -> false)).isEqualTo("peluqueria-nandu-cafe");
	}

	@Test
	void siEstaOcupadoSumaSufijo() {
		Set<String> ocupados = Set.of("lo-de-juan", "lo-de-juan-2");
		assertThat(SlugGenerador.generar("Lo de Juan", ocupados::contains)).isEqualTo("lo-de-juan-3");
	}

	@Test
	void nombreReservadoOCortoLlevaPrefijo() {
		assertThat(SlugGenerador.generar("Admin", s -> false)).isEqualTo("barberia-admin");
		assertThat(SlugGenerador.generar("JM", s -> false)).isEqualTo("barberia-jm");
		assertThat(SlugGenerador.generar("¡¡!!", s -> false)).isEqualTo("mi-barberia");
	}

	@Test
	void respetaElLargoMaximoConSufijo() {
		String nombre = "a".repeat(60);
		String slug = SlugGenerador.generar(nombre, s -> s.equals("a".repeat(50)));
		assertThat(slug).hasSizeLessThanOrEqualTo(50).endsWith("-2").matches(SlugGenerador.FORMATO);
	}

}
