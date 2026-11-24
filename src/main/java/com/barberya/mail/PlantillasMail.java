package com.barberya.mail;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Renderiza las plantillas de {@code templates/mail/}: cada mail tiene un {@code .html} y un {@code .txt} con el
 * mismo nombre. En el HTML, todo dato va con {@code th:text} / {@code th:href} (escapan); nunca {@code th:utext}.
 */
@Component
public class PlantillasMail {

	private static final String CARPETA = "templates/mail/";
	private static final Locale ESPANOL = Locale.forLanguageTag("es-AR");

	private final TemplateEngine motor = new TemplateEngine();

	public PlantillasMail() {
		motor.addTemplateResolver(resolver(TemplateMode.HTML, "*.html", 1));
		motor.addTemplateResolver(resolver(TemplateMode.TEXT, "*.txt", 2));
	}

	public Contenido renderizar(String plantilla, Map<String, Object> datos) {
		Context contexto = new Context(ESPANOL, datos);
		return new Contenido(motor.process(plantilla + ".html", contexto), motor.process(plantilla + ".txt", contexto));
	}

	private static ClassLoaderTemplateResolver resolver(TemplateMode modo, String patron, int orden) {
		ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
		resolver.setPrefix(CARPETA);
		resolver.setTemplateMode(modo);
		resolver.setResolvablePatterns(Set.of(patron));
		resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
		resolver.setCheckExistence(true);
		resolver.setOrder(orden);
		return resolver;
	}

	/** El cuerpo puede tener códigos o links: no se muestra en {@code toString}. */
	public record Contenido(String html, String texto) {

		@Override
		public String toString() {
			return "Contenido[]";
		}
	}

}
