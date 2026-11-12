package com.barberya.cobro;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CobroRepository extends JpaRepository<Cobro, Long> {

	Optional<Cobro> findByIdAndBarberiaId(Long id, Long barberiaId);

}
