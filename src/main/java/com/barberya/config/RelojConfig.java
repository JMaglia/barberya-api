package com.barberya.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** "Ahora" inyectable, para poder fijarlo en los tests. */
@Configuration(proxyBeanMethods = false)
public class RelojConfig {

	@Bean
	Clock reloj() {
		return Clock.systemUTC();
	}

}
