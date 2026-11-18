package com.barberya.turno;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TurnoRepository extends JpaRepository<Turno, Long> {

	Optional<Turno> findByIdAndBarberiaId(Long id, Long barberiaId);

	/** Turnos del miembro en ese estado que se pisan con [desde, hasta). Los contiguos no cuentan. */
	long countByMiembroIdAndEstadoAndInicioLessThanAndFinGreaterThan(Long miembroId, EstadoTurno estado, Instant hasta,
			Instant desde);

}
