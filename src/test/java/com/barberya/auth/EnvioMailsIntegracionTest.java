package com.barberya.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.ServerSocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.barberya.IntegracionConLimpiezaTest;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetup;

import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

/**
 * Envío real por SMTP contra GreenMail, sin el doble de mails. Sin {@code @Transactional}: el mail sale después
 * del commit, y en un test transaccional nunca se commitea.
 */
@AutoConfigureMockMvc
class EnvioMailsIntegracionTest extends IntegracionConLimpiezaTest {

	private static final int PUERTO = puertoLibre();

	@RegisterExtension
	static final GreenMailExtension SMTP = new GreenMailExtension(
			new ServerSetup(PUERTO, "127.0.0.1", ServerSetup.PROTOCOL_SMTP)).withPerMethodLifecycle(false);

	@DynamicPropertySource
	static void smtp(DynamicPropertyRegistry registry) {
		registry.add("spring.mail.host", () -> "127.0.0.1");
		registry.add("spring.mail.port", () -> PUERTO);
	}

	@Autowired
	private MockMvc mvc;
	@MockitoSpyBean
	private TokenService tokenService;

	@BeforeEach
	void vaciarBuzon() throws Exception {
		SMTP.purgeEmailFromAllMailboxes();
	}

	@Test
	void solicitarCodigoMandaElCodigoAlDestinatario() throws Exception {
		doReturn("483920").when(tokenService).nuevoCodigo();

		postJson("/api/v1/auth/codigo/solicitar", "Nuevo@Mail.com").andExpect(status().isOk());

		MimeMessage mail = unicoMailRecibido();
		assertThat(mail.getRecipients(Message.RecipientType.TO)).extracting(a -> ((InternetAddress) a).getAddress())
				.containsExactly("nuevo@mail.com");
		assertThat(((InternetAddress) mail.getFrom()[0]).getPersonal()).isEqualTo("BarberYA");
		assertThat(mail.getSubject()).isEqualTo("Tu código para entrar a BarberYA");
		assertThat(parte(mail, "text/plain")).contains("483920", "¡Hola!");
		assertThat(parte(mail, "text/html")).contains("483920", "¡Hola!");
	}

	@Test
	void recuperarMandaElLinkAlUsuarioConSuNombreEscapado() throws Exception {
		jdbc.update("INSERT INTO usuarios (email, nombre) VALUES ('juan@mail.com', 'Juan <b>')");
		doReturn("valor-secreto-de-prueba").when(tokenService).nuevoValorAleatorio();

		postJson("/api/v1/auth/recuperar", "juan@mail.com").andExpect(status().isOk());

		MimeMessage mail = unicoMailRecibido();
		assertThat(mail.getRecipients(Message.RecipientType.TO)).extracting(a -> ((InternetAddress) a).getAddress())
				.containsExactly("juan@mail.com");
		assertThat(mail.getSubject()).isEqualTo("Restablecé tu contraseña de BarberYA");
		String link = "/restablecer?token=valor-secreto-de-prueba";
		assertThat(parte(mail, "text/plain")).contains(link, "¡Hola, Juan <b>!");
		assertThat(parte(mail, "text/html")).contains(link, "¡Hola, Juan &lt;b&gt;!").doesNotContain("Juan <b>");
	}

	@Test
	void recuperarConMailInexistenteNoMandaNada() throws Exception {
		postJson("/api/v1/auth/recuperar", "nadie@mail.com").andExpect(status().isOk());

		assertThat(SMTP.waitForIncomingEmail(1000, 1)).isFalse();
	}

	private MimeMessage unicoMailRecibido() {
		assertThat(SMTP.waitForIncomingEmail(5000, 1)).as("llegó el mail").isTrue();
		MimeMessage[] recibidos = SMTP.getReceivedMessages();
		assertThat(recibidos).hasSize(1);
		return recibidos[0];
	}

	private ResultActions postJson(String url, String email) throws Exception {
		return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s"}
				""".formatted(email)));
	}

	/** El contenido decodificado de la primera parte con ese tipo, buscando en las partes anidadas. */
	static String parte(Part parte, String tipo) throws Exception {
		if (parte.isMimeType(tipo)) {
			return (String) parte.getContent();
		}
		if (parte.getContent() instanceof Multipart multipart) {
			for (int i = 0; i < multipart.getCount(); i++) {
				BodyPart hija = multipart.getBodyPart(i);
				String encontrada = parte(hija, tipo);
				if (encontrada != null) {
					return encontrada;
				}
			}
		}
		return null;
	}

	static int puertoLibre() {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

}
