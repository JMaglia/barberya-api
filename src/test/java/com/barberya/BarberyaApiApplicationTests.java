package com.barberya;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;

/** Con {@code ddl-auto=validate}, que el contexto levante prueba que cada entidad coincide con la base. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BarberyaApiApplicationTests {

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Test
	void contextLoads() {
	}

	@Test
	void lasDieciochoTablasDeV1TienenSuEntidad() {
		assertThat(entityManagerFactory.getMetamodel().getEntities())
				.extracting(EntityType::getName)
				.containsExactlyInAnyOrder(
						"Plan", "Barberia", "Suscripcion", "PagoSuscripcion",
						"Usuario", "RefreshToken", "TokenVerificacion", "MiembroBarberia", "Cliente",
						"Servicio", "HorarioAtencion", "BloqueoAgenda", "Turno",
						"Cobro", "CategoriaGasto", "GastoFijo", "Proveedor", "Gasto");
	}

}
