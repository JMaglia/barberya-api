package com.barberya.mail;

import java.nio.charset.StandardCharsets;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.barberya.config.BarberyaProperties;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;

/**
 * Manda por SMTP los {@link MailSaliente} publicados. Después del commit (si la transacción se revierte, no sale
 * nada) y en otro hilo, así el pedido no espera al SMTP. Sin transacción (por ejemplo, desde una tarea programada)
 * se manda igual.
 */
@Slf4j
@Component
public class DespachadorMails {

	private final JavaMailSender mailSender;
	private final String remitente;

	public DespachadorMails(JavaMailSender mailSender, BarberyaProperties props) {
		this.mailSender = mailSender;
		this.remitente = props.mail().remitente();
	}

	@Async(MailConfig.EXECUTOR)
	@TransactionalEventListener(fallbackExecution = true)
	public void enviar(MailSaliente mail) {
		try {
			MimeMessage mensaje = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, StandardCharsets.UTF_8.name());
			helper.setFrom(remitente);
			helper.setTo(mail.destinatario());
			helper.setSubject(mail.asunto());
			helper.setText(mail.texto(), mail.html());
			mailSender.send(mensaje);
		} catch (Exception e) {
			// Solo la clase: el mensaje de la excepción puede traer la dirección del destinatario.
			log.warn("No se pudo enviar el mail {}: {}", mail.tipo(), e.getClass().getSimpleName());
		}
	}

}
