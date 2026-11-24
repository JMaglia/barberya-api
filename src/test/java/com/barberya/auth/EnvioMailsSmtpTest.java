package com.barberya.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.barberya.mail.MailSaliente;
import com.barberya.mail.PlantillasMail;

/** Plantillas de mail sin Spring: qué se renderiza y que los datos se escapan. */
class EnvioMailsSmtpTest {

	private static final String LINK = "http://localhost:5173/restablecer?token=abc123";

	private final List<Object> publicados = new ArrayList<>();
	private final EnvioMailsSmtp envio = new EnvioMailsSmtp(new PlantillasMail(), publicados::add);

	@Test
	void codigoLoginLlevaElCodigoEnHtmlYTextoYElSaludoConNombre() {
		envio.enviarCodigo("juan@mail.com", "Juan", "483920");

		MailSaliente mail = unicoMail();
		assertThat(mail.tipo()).isEqualTo("codigo-login");
		assertThat(mail.destinatario()).isEqualTo("juan@mail.com");
		assertThat(mail.asunto()).isEqualTo("Tu código para entrar a BarberYA");
		assertThat(mail.html()).contains("483920", "¡Hola, Juan!", "Vence en <span>10</span> minutos");
		assertThat(mail.texto()).contains("483920", "¡Hola, Juan!", "Vence en 10 minutos").doesNotContain("<");
	}

	@Test
	void recuperarClaveLlevaElLinkEnHtmlYTexto() {
		envio.enviarRecuperacionClave("juan@mail.com", "Juan", LINK);

		MailSaliente mail = unicoMail();
		assertThat(mail.tipo()).isEqualTo("recuperar-clave");
		assertThat(mail.asunto()).isEqualTo("Restablecé tu contraseña de BarberYA");
		assertThat(mail.html()).contains("href=\"" + LINK + "\"", ">" + LINK + "<", "30</span> minutos");
		assertThat(mail.texto()).contains(LINK, "30 minutos");
	}

	@Test
	void sinNombreElSaludoEsHola() {
		envio.enviarCodigo("nuevo@mail.com", null, "111111");
		envio.enviarRecuperacionClave("juan@mail.com", "  ", LINK);

		assertThat(publicados).extracting(m -> ((MailSaliente) m).texto())
				.allSatisfy(texto -> assertThat(texto).startsWith("¡Hola!"));
		assertThat(publicados).extracting(m -> ((MailSaliente) m).html())
				.allSatisfy(html -> assertThat(html).contains("¡Hola!").doesNotContain("¡Hola, "));
	}

	@Test
	void elNombreSeEscapaEnElHtml() {
		envio.enviarCodigo("juan@mail.com", "<script>alert('x')</script> & \"Juan\"", "483920");

		String html = unicoMail().html();
		assertThat(html).doesNotContain("<script>")
				.contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; &amp; &quot;Juan&quot;");
	}

	@Test
	void elLinkSeEscapaEnElAtributo() {
		envio.enviarRecuperacionClave("juan@mail.com", "Juan", "http://x/?a=1&b=\"><script>");

		assertThat(unicoMail().html()).doesNotContain("\"><script>").contains("a=1&amp;b=&quot;&gt;&lt;script&gt;");
	}

	@Test
	void elEventoNoMuestraDestinatarioNiCuerpo() {
		envio.enviarCodigo("juan@mail.com", "Juan", "483920");

		assertThat(unicoMail().toString()).isEqualTo("MailSaliente[tipo=codigo-login]");
	}

	private MailSaliente unicoMail() {
		assertThat(publicados).hasSize(1);
		return (MailSaliente) publicados.getFirst();
	}

}
