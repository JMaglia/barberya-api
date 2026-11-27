package com.barberya.cliente;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

	Optional<Cliente> findByIdAndBarberiaId(Long id, Long barberiaId);

	/** La ficha de ese usuario en esa barbería, bloqueada: dos reservas del mismo cliente no se saltean el tope. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT c FROM Cliente c WHERE c.barberia.id = :barberiaId AND c.usuario.id = :usuarioId")
	Optional<Cliente> findParaReservar(@Param("barberiaId") Long barberiaId, @Param("usuarioId") Long usuarioId);

	/**
	 * Fichas activas de la barbería cuyo nombre (sin tildes ni mayúsculas) coincide con {@code patronNombre}, o cuyo
	 * teléfono (solo los dígitos) coincide con {@code patronDigitos}. Patrones LIKE ya escapados con {@code \}; un patrón
	 * vacío no se usa. Ordenadas por nombre. El {@code translate} saca las tildes (minúsculas y mayúsculas, posición por
	 * posición) sin depender de la extensión unaccent; tiene que coincidir con {@code ClienteService.normalizar}.
	 */
	@Query(nativeQuery = true, value = """
			SELECT c.* FROM clientes c
			WHERE c.barberia_id = :barberiaId AND c.activo
			  AND ((:patronNombre <> ''
			        AND lower(translate(c.nombre, 'áàäâãéèëêíìïîóòöôõúùüûñçÁÀÄÂÃÉÈËÊÍÌÏÎÓÒÖÔÕÚÙÜÛÑÇ',
			                                      'aaaaaeeeeiiiiooooouuuuncaaaaaeeeeiiiiooooouuuunc'))
			            LIKE :patronNombre ESCAPE '\\')
			    OR (:patronDigitos <> ''
			        AND regexp_replace(coalesce(c.telefono, ''), '[^0-9]', '', 'g') LIKE :patronDigitos ESCAPE '\\'))
			ORDER BY lower(c.nombre), c.id
			LIMIT :limite
			""")
	List<Cliente> buscar(@Param("barberiaId") Long barberiaId, @Param("patronNombre") String patronNombre,
			@Param("patronDigitos") String patronDigitos, @Param("limite") int limite);

}
