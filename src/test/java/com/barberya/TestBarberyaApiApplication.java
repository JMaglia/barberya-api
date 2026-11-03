package com.barberya;

import org.springframework.boot.SpringApplication;

public class TestBarberyaApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(BarberyaApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
