package com.barberya.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.barberya.IntegracionConLimpiezaTest;

/**
 * SMTP caído (un puerto donde no escucha nadie): la respuesta es la de siempre y el error se loguea sin el
 * destinatario, el código ni el link.
 */
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class EnvioMailsSmtpCaidoIntegracionTest extends IntegracionConLimpiezaTest {

	private static final int PUERTO_SIN_SMTP = EnvioMailsIntegracionTest.puertoLibre();

	@DynamicPropertySource
	static void smtp(DynamicPropertyRegistry registry) {
		registry.add("spring.mail.host", () -> "127.0.0.1");
		registry.add("spring.mail.port", () -> PUERTO_SIN_SMTP);
	}

	@Autowired
	private MockMvc mvc;
	@MockitoSpyBean
	private TokenService tokenService;

	@Test
	void solicitarCodigoResponde200YNoLogueaElCodigoNiElMail(CapturedOutput salida) throws Exception {
		doReturn("483920").when(tokenService).nuevoCodigo();

		postJson("/api/v1/auth/codigo/solicitar", "secreto@mail.com")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mensaje").value("Si el mail es válido, te enviamos un código para entrar"));

		esperarLog(salida, "No se pudo enviar el mail codigo-login");
		assertThat(salida.getAll()).doesNotContain("483920", "secreto@mail.com");
	}

	@Test
	void recuperarResponde200YNoLogueaElLinkNiElMail(CapturedOutput salida) throws Exception {
		jdbc.update("INSERT INTO usuarios (email, nombre) VALUES ('secreto@mail.com', 'Juan')");
		doReturn("valor-secreto-de-prueba").when(tokenService).nuevoValorAleatorio();

		postJson("/api/v1/auth/recuperar", "secreto@mail.com")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mensaje")
						.value("Si el mail está registrado, te enviamos un link para restablecer la contraseña"));

		esperarLog(salida, "No se pudo enviar el mail recuperar-clave");
		assertThat(salida.getAll()).doesNotContain("valor-secreto-de-prueba", "restablecer?token=", "secreto@mail.com");
	}

	/** El envío es asíncrono: se espera a que aparezca el error en el log. */
	private static void esperarLog(CapturedOutput salida, String texto) throws InterruptedException {
		Instant limite = Instant.now().plus(Duration.ofSeconds(15));
		while (!salida.getAll().contains(texto) && Instant.now().isBefore(limite)) {
			Thread.sleep(50);
		}
		assertThat(salida.getAll()).contains(texto);
	}

	private ResultActions postJson(String url, String email) throws Exception {
		return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s"}
				""".formatted(email)));
	}

}
