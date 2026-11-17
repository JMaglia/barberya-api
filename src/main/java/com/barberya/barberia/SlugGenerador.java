package com.barberya.barberia;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/** Arma un slug válido a partir del nombre de la barbería: "Peluquería Ñandú" → "peluqueria-nandu". */
public final class SlugGenerador {

	public static final String FORMATO = "^[a-z0-9]+(-[a-z0-9]+)*$";
	public static final int LARGO_MIN = 3;
	public static final int LARGO_MAX = 50;

	private static final String PREFIJO_CORTO = "barberia-";
	private static final String SI_QUEDA_VACIO = "mi-barberia";

	private SlugGenerador() {
	}

	/** Si el slug base está ocupado ({@code ocupado}) o reservado, prueba con -2, -3... */
	public static String generar(String nombre, Predicate<String> ocupado) {
		String base = base(nombre);
		String candidato = base;
		for (int n = 2; SlugsReservados.esReservado(candidato) || ocupado.test(candidato); n++) {
			String sufijo = "-" + n;
			candidato = recortar(base, LARGO_MAX - sufijo.length()) + sufijo;
		}
		return candidato;
	}

	static String base(String nombre) {
		String sinTildes = Normalizer.normalize(nombre, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		String slug = recortar(sinTildes.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-"), LARGO_MAX);
		if (slug.isEmpty()) {
			return SI_QUEDA_VACIO;
		}
		if (slug.length() < LARGO_MIN || SlugsReservados.esReservado(slug)) {
			return recortar(PREFIJO_CORTO + slug, LARGO_MAX);
		}
		return slug;
	}

	/** Trunca y saca los guiones de los extremos. */
	private static String recortar(String slug, int largo) {
		String recortado = slug.length() > largo ? slug.substring(0, largo) : slug;
		return recortado.replaceAll("^-+|-+$", "");
	}

}
