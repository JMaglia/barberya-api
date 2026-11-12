package com.barberya.gasto;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaGastoRepository extends JpaRepository<CategoriaGasto, Long> {

	Optional<CategoriaGasto> findByIdAndBarberiaId(Long id, Long barberiaId);

}
