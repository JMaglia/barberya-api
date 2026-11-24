package com.barberya.barberia;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MiembroBarberiaRepository extends JpaRepository<MiembroBarberia, Long> {

	Optional<MiembroBarberia> findByIdAndBarberiaId(Long id, Long barberiaId);

	/** En V1 un usuario es miembro de una sola barbería. */
	Optional<MiembroBarberia> findFirstByUsuarioIdAndActivoTrue(Long usuarioId);

	boolean existsByUsuarioId(Long usuarioId);

	boolean existsByUsuarioIdAndBarberiaIdAndActivoTrue(Long usuarioId, Long barberiaId);

	Optional<MiembroBarberia> findByUsuarioIdAndBarberiaIdAndActivoTrue(Long usuarioId, Long barberiaId);

}
