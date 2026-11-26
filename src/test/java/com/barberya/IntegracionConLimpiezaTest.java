package com.barberya;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.barberya.auth.LimitadorIntentosEnMemoria;

/**
 * Base para tests de integración sin {@code @Transactional}: vacía la base antes y después de cada test,
 * así no quedan datos commiteados en el contenedor compartido.
 * Truncar las tres tablas raíz con CASCADE alcanza a todas las de V1, porque las demás dependen de ellas.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
public abstract class IntegracionConLimpiezaTest {

	@Autowired
	protected JdbcTemplate jdbc;

	@Autowired
	private LimitadorIntentosEnMemoria limitador;

	/** También vacía los límites de intentos: los tests hacen muchos pedidos desde la misma IP de MockMvc. */
	@BeforeEach
	@AfterEach
	void limpiarBase() {
		jdbc.execute("TRUNCATE barberias, usuarios, planes RESTART IDENTITY CASCADE");
		limitador.limpiar();
	}

}
