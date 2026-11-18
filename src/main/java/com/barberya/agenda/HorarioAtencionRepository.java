package com.barberya.agenda;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** No hay barberia_id: se filtra siempre por el miembro (y, en lo público, por su barbería). */
public interface HorarioAtencionRepository extends JpaRepository<HorarioAtencion, Long> {

	List<HorarioAtencion> findByMiembroIdOrderByDiaSemanaAscHoraInicioAsc(Long miembroId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from HorarioAtencion h where h.miembro.id = :miembroId")
	int borrarDeMiembro(@Param("miembroId") Long miembroId);

	/** Horario que se muestra en la página de la barbería: el de sus miembros activos que atienden. */
	@Query("""
			select h from HorarioAtencion h
			where h.miembro.barberia.id = :barberiaId and h.miembro.activo = true and h.miembro.atiendeTurnos = true
			order by h.diaSemana, h.horaInicio, h.horaFin
			""")
	List<HorarioAtencion> buscarPublicosDeBarberia(@Param("barberiaId") Long barberiaId);

}
