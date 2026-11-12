package com.barberya.turno;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TurnoRepository extends JpaRepository<Turno, Long> {

	Optional<Turno> findByIdAndBarberiaId(Long id, Long barberiaId);

}
