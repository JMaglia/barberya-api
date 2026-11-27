package com.barberya.gasto;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaGastoRepository extends JpaRepository<CategoriaGasto, Long> {

	Optional<CategoriaGasto> findByIdAndBarberiaId(Long id, Long barberiaId);

	List<CategoriaGasto> findByBarberiaId(Long barberiaId, Sort orden);

	boolean existsByBarberiaIdAndNombreIgnoreCase(Long barberiaId, String nombre);

	boolean existsByBarberiaIdAndNombreIgnoreCaseAndIdNot(Long barberiaId, String nombre, Long id);

}
