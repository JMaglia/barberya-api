package com.barberya.gasto;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.barberya.barberia.BarberiaRepository;
import com.barberya.error.RecursoNoEncontradoException;
import com.barberya.error.Restricciones;
import com.barberya.gasto.dto.CategoriaGastoRequest;
import com.barberya.gasto.dto.CategoriaGastoResponse;

/**
 * Categorías por barbería. No tienen baja lógica (la tabla no tiene {@code activo}): se borran si no tienen gastos.
 * El nombre no se repite en la barbería, sin importar mayúsculas.
 */
@Service
public class CategoriaGastoService {

	/** UNIQUE (barberia_id, nombre) de la V1, con el nombre que le pone Postgres. */
	static final String RESTRICCION_NOMBRE_UNICO = "categorias_gasto_barberia_id_nombre_key";

	private static final Sort POR_NOMBRE = Sort.by(Sort.Order.asc("nombre").ignoreCase());

	private final CategoriaGastoRepository categorias;
	private final GastoRepository gastos;
	private final BarberiaRepository barberias;

	public CategoriaGastoService(CategoriaGastoRepository categorias, GastoRepository gastos,
			BarberiaRepository barberias) {
		this.categorias = categorias;
		this.gastos = gastos;
		this.barberias = barberias;
	}

	@Transactional(readOnly = true)
	public List<CategoriaGastoResponse> listar(Long barberiaId) {
		return categorias.findByBarberiaId(barberiaId, POR_NOMBRE).stream().map(CategoriaGastoResponse::de).toList();
	}

	@Transactional
	public CategoriaGastoResponse crear(Long barberiaId, CategoriaGastoRequest request) {
		String nombre = request.nombre().trim();
		if (categorias.existsByBarberiaIdAndNombreIgnoreCase(barberiaId, nombre)) {
			throw ConflictoGastoException.categoriaRepetida();
		}
		return CategoriaGastoResponse.de(guardar(new CategoriaGasto(barberias.getReferenceById(barberiaId), nombre)));
	}

	@Transactional
	public CategoriaGastoResponse renombrar(Long barberiaId, Long id, CategoriaGastoRequest request) {
		CategoriaGasto categoria = buscar(barberiaId, id);
		String nombre = request.nombre().trim();
		if (categorias.existsByBarberiaIdAndNombreIgnoreCaseAndIdNot(barberiaId, nombre, id)) {
			throw ConflictoGastoException.categoriaRepetida();
		}
		categoria.setNombre(nombre);
		return CategoriaGastoResponse.de(guardar(categoria));
	}

	/** Una categoría con gastos (variables o fijos) no se borra; la FK de la base también lo impide. */
	@Transactional
	public void borrar(Long barberiaId, Long id) {
		CategoriaGasto categoria = buscar(barberiaId, id);
		if (gastos.existsByCategoriaId(id)) {
			throw ConflictoGastoException.categoriaConGastos();
		}
		categorias.delete(categoria);
	}

	private CategoriaGasto buscar(Long barberiaId, Long id) {
		return categorias.findByIdAndBarberiaId(id, barberiaId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No encontramos esa categoría"));
	}

	/** Dos requests que crean o renombran a la vez con el mismo nombre: la segunda choca con el UNIQUE → 409. */
	private CategoriaGasto guardar(CategoriaGasto categoria) {
		try {
			return categorias.saveAndFlush(categoria);
		} catch (DataIntegrityViolationException e) {
			if (Restricciones.es(e, RESTRICCION_NOMBRE_UNICO)) {
				throw ConflictoGastoException.categoriaRepetida();
			}
			throw e;
		}
	}

}
