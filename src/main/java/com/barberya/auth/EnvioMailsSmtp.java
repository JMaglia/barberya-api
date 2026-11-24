package com.barberya.auth;

import java.util.HashMap;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.barberya.mail.MailSaliente;
import com.barberya.mail.PlantillasMail;

/**
 * Renderiza el mail y lo publica como {@link MailSaliente}; lo manda {@code DespachadorMails} después del commit
 * y en otro hilo. Para sumar un mail: una plantilla {@code .html} + {@code .txt} y un método como estos.
 */
@Component
public class EnvioMailsSmtp implements EnvioMails {

	static final String PLANTILLA_CODIGO = "codigo-login";
	static final String PLANTILLA_RECUPERAR = "recuperar-clave";

	private final PlantillasMail plantillas;
	private final ApplicationEventPublisher eventos;

	public EnvioMailsSmtp(PlantillasMail plantillas, ApplicationEventPublisher eventos) {
		this.plantillas = plantillas;
		this.eventos = eventos;
	}

	@Override
	public void enviarCodigo(String email, String nombre, String codigo) {
		Map<String, Object> datos = datosBase(nombre);
		datos.put("codigo", codigo);
		datos.put("minutos", AuthService.CODIGO_LOGIN.toMinutes());
		publicar(PLANTILLA_CODIGO, email, "Tu código para entrar a BarberYA", datos);
	}

	@Override
	public void enviarRecuperacionClave(String email, String nombre, String link) {
		Map<String, Object> datos = datosBase(nombre);
		datos.put("link", link);
		datos.put("minutos", AuthService.TOKEN_RECUPERACION.toMinutes());
		publicar(PLANTILLA_RECUPERAR, email, "Restablecé tu contraseña de BarberYA", datos);
	}

	private void publicar(String plantilla, String email, String asunto, Map<String, Object> datos) {
		PlantillasMail.Contenido contenido = plantillas.renderizar(plantilla, datos);
		eventos.publishEvent(new MailSaliente(plantilla, email, asunto, contenido.html(), contenido.texto()));
	}

	private static Map<String, Object> datosBase(String nombre) {
		Map<String, Object> datos = new HashMap<>();
		datos.put("saludo", nombre == null || nombre.isBlank() ? "¡Hola!" : "¡Hola, " + nombre.trim() + "!");
		return datos;
	}

}
