package com.barberya.suscripcion;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SuscripcionRepository extends JpaRepository<Suscripcion, Long> {

	Optional<Suscripcion> findByIdAndBarberiaId(Long id, Long barberiaId);

}
