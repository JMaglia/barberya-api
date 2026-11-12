package com.barberya.barberia;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MiembroBarberiaRepository extends JpaRepository<MiembroBarberia, Long> {

	Optional<MiembroBarberia> findByIdAndBarberiaId(Long id, Long barberiaId);

}
