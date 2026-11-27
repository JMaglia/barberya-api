package com.barberya.gasto;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GastoRepository extends JpaRepository<Gasto, Long> {

	Optional<Gasto> findByIdAndBarberiaId(Long id, Long barberiaId);

	/** Los gastos fijos no se ven ni se tocan desde los endpoints de gastos variables. */
	Optional<Gasto> findByIdAndBarberiaIdAndEsFijoFalse(Long id, Long barberiaId);

	@EntityGraph(attributePaths = { "categoria", "proveedor" })
	List<Gasto> findByBarberiaIdAndEsFijoFalseAndFechaBetween(Long barberiaId, LocalDate desde, LocalDate hasta,
			Sort orden);

	boolean existsByCategoriaId(Long categoriaId);

}
