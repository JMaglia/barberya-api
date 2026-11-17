package com.barberya.barberia;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BarberiaRepository extends JpaRepository<Barberia, Long> {

	boolean existsBySlug(String slug);

	boolean existsBySlugAndIdNot(String slug, Long id);

	Optional<Barberia> findBySlugAndActivaTrue(String slug);

}
