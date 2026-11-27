package com.barberya.gasto;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {

	Optional<Proveedor> findByIdAndBarberiaId(Long id, Long barberiaId);

	/** Proveedores que se ofrecen al cargar un gasto nuevo. */
	List<Proveedor> findByBarberiaIdAndActivoTrue(Long barberiaId);

	List<Proveedor> findByBarberiaIdAndActivoTrue(Long barberiaId, Sort orden);

	List<Proveedor> findByBarberiaId(Long barberiaId, Sort orden);

}
