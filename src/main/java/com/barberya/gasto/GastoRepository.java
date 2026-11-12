package com.barberya.gasto;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GastoRepository extends JpaRepository<Gasto, Long> {

	Optional<Gasto> findByIdAndBarberiaId(Long id, Long barberiaId);

}
