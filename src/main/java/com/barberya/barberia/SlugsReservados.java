package com.barberya.barberia;

import java.util.Set;

/**
 * Slugs que no puede usar una barbería. El link público es {@code barberya.app/{slug}}: comparte espacio con las
 * rutas del front y del backend. Para reservar uno nuevo, sumarlo acá (en minúsculas).
 */
public final class SlugsReservados {

	private static final Set<String> RESERVADOS = Set.of(
			// Backend e infraestructura
			"api", "admin", "login", "logout", "registro", "registrarse", "app", "www", "public", "auth", "me",
			"static", "assets", "swagger-ui",
			// Rutas del front
			"inicio", "panel", "agenda", "clientes", "gastos", "estadisticas", "configuracion", "perfil", "cuenta",
			"entrar", "salir", "recuperar", "restablecer", "verificar", "google", "reservar", "reserva",
			"suscripcion", "pagos", "notificaciones", "legal", "blog", "nosotros", "mis-turnos", "turnos",
			"barberia", "barberias", "servicios", "ayuda", "soporte", "contacto", "terminos", "privacidad",
			"precios", "planes");

	private SlugsReservados() {
	}

	public static boolean esReservado(String slug) {
		return RESERVADOS.contains(slug);
	}

}
