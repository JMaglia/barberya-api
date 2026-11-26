package com.barberya.turno;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import com.barberya.barberia.Barberia;
import com.barberya.servicio.Servicio;

/** Arma a mano el iCalendar (RFC 5545) de un turno: un VEVENT con alarma 2 horas antes. */
final class CalendarioIcs {

	private static final String CRLF = "\r\n";
	private static final int MAX_OCTETOS = 75;
	private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
			.withZone(ZoneOffset.UTC);

	private CalendarioIcs() {
	}

	/** {@code ahora} va en DTSTAMP (obligatorio): cuándo se generó el archivo. */
	static String generar(Turno turno, Instant ahora) {
		Barberia barberia = turno.getBarberia();
		Servicio servicio = turno.getServicio();
		String resumen = servicio.getNombre() + " en " + barberia.getNombre();
		String descripcion = "Servicio: " + servicio.getNombre();
		if (tieneTexto(barberia.getTelefono())) {
			descripcion += "\nTeléfono de la barbería: " + barberia.getTelefono();
		}

		StringBuilder ics = new StringBuilder();
		linea(ics, "BEGIN:VCALENDAR");
		linea(ics, "VERSION:2.0");
		linea(ics, "PRODID:-//BarberYA//Mis turnos//ES");
		linea(ics, "CALSCALE:GREGORIAN");
		linea(ics, "METHOD:PUBLISH");
		linea(ics, "BEGIN:VEVENT");
		linea(ics, "UID:turno-" + turno.getId() + "@barberya.app");
		linea(ics, "DTSTAMP:" + UTC.format(ahora));
		linea(ics, "DTSTART:" + UTC.format(turno.getInicio()));
		linea(ics, "DTEND:" + UTC.format(turno.getFin()));
		linea(ics, "STATUS:" + (turno.getEstado() == EstadoTurno.CANCELADO ? "CANCELLED" : "CONFIRMED"));
		linea(ics, "SUMMARY:" + escapar(resumen));
		if (tieneTexto(barberia.getDireccion())) {
			linea(ics, "LOCATION:" + escapar(barberia.getDireccion()));
		}
		linea(ics, "DESCRIPTION:" + escapar(descripcion));
		linea(ics, "BEGIN:VALARM");
		linea(ics, "ACTION:DISPLAY");
		linea(ics, "DESCRIPTION:" + escapar(resumen));
		linea(ics, "TRIGGER:-PT2H");
		linea(ics, "END:VALARM");
		linea(ics, "END:VEVENT");
		linea(ics, "END:VCALENDAR");
		return ics.toString();
	}

	/** Escape de valores TEXT (RFC 5545 §3.3.11): barra invertida, punto y coma, coma y saltos de línea. */
	static String escapar(String texto) {
		StringBuilder sb = new StringBuilder(texto.length());
		for (int i = 0; i < texto.length(); i++) {
			char c = texto.charAt(i);
			switch (c) {
				case '\\' -> sb.append("\\\\");
				case ';' -> sb.append("\\;");
				case ',' -> sb.append("\\,");
				case '\n' -> sb.append("\\n");
				case '\r' -> { }
				default -> sb.append(c);
			}
		}
		return sb.toString();
	}

	/**
	 * Plegado de líneas (RFC 5545 §3.1): ningún renglón supera 75 octetos en UTF-8; los siguientes empiezan con un
	 * espacio. No corta un carácter multibyte.
	 */
	static String plegar(String linea) {
		StringBuilder sb = new StringBuilder(linea.length() + 8);
		int octetos = 0;
		int i = 0;
		while (i < linea.length()) {
			int punto = linea.codePointAt(i);
			String caracter = new String(Character.toChars(punto));
			int largo = caracter.getBytes(StandardCharsets.UTF_8).length;
			if (octetos + largo > MAX_OCTETOS) {
				sb.append(CRLF).append(' ');
				octetos = 1;
			}
			sb.append(caracter);
			octetos += largo;
			i += Character.charCount(punto);
		}
		return sb.toString();
	}

	private static void linea(StringBuilder ics, String contenido) {
		ics.append(plegar(contenido)).append(CRLF);
	}

	private static boolean tieneTexto(String valor) {
		return valor != null && !valor.isBlank();
	}

}
