package com.barberya.mail;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import lombok.extern.slf4j.Slf4j;

/** Envío asíncrono de mails: un executor chico, aparte, para que un SMTP lento no trabe los pedidos. */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class MailConfig {

	public static final String EXECUTOR = "mailExecutor";

	@Bean(EXECUTOR)
	ThreadPoolTaskExecutor mailExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("mail-");
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(2);
		executor.setQueueCapacity(100);
		// Si la cola se llena, se descarta el mail sin tirar excepción: corre en el afterCommit del pedido
		// y una excepción ahí le devolvería un error al usuario.
		executor.setRejectedExecutionHandler((tarea, pool) -> log.warn("Cola de mails llena: se descartó un mail"));
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(10);
		return executor;
	}

}
