package com.barberya.servicio;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ServicioRepository extends JpaRepository<Servicio, Long> {

	Optional<Servicio> findByIdAndBarberiaId(Long id, Long barberiaId);

}
