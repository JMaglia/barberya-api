package com.barberya.gasto;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GastoFijoRepository extends JpaRepository<GastoFijo, Long> {

	Optional<GastoFijo> findByIdAndBarberiaId(Long id, Long barberiaId);

}
