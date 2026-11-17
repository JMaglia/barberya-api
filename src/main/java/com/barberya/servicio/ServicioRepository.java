package com.barberya.servicio;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServicioRepository extends JpaRepository<Servicio, Long> {

	Optional<Servicio> findByIdAndBarberiaId(Long id, Long barberiaId);

	List<Servicio> findByBarberiaId(Long barberiaId, Sort orden);

	List<Servicio> findByBarberiaIdAndActivoTrue(Long barberiaId, Sort orden);

	boolean existsByBarberiaIdAndActivoTrueAndNombreIgnoreCase(Long barberiaId, String nombre);

	boolean existsByBarberiaIdAndActivoTrueAndNombreIgnoreCaseAndIdNot(Long barberiaId, String nombre, Long id);

}
