package com.barberya.gasto;

import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.barberya.barberia.BarberiaRepository;
import com.barberya.error.RecursoNoEncontradoException;
import com.barberya.gasto.dto.ActualizarProveedorRequest;
import com.barberya.gasto.dto.CrearProveedorRequest;
import com.barberya.gasto.dto.ProveedorResponse;

/** Todo filtra por la barbería del token; un proveedor de otra barbería da 404. */
@Service
public class ProveedorService {

	private static final Sort POR_NOMBRE = Sort.by(Sort.Order.asc("nombre").ignoreCase());
	private static final Sort ACTIVOS_PRIMERO = Sort.by(Sort.Order.desc("activo"), Sort.Order.asc("nombre").ignoreCase());

	private final ProveedorRepository proveedores;
	private final BarberiaRepository barberias;

	public ProveedorService(ProveedorRepository proveedores, BarberiaRepository barberias) {
		this.proveedores = proveedores;
		this.barberias = barberias;
	}

	@Transactional(readOnly = true)
	public List<ProveedorResponse> listar(Long barberiaId, boolean incluirInactivos) {
		List<Proveedor> lista = incluirInactivos ? proveedores.findByBarberiaId(barberiaId, ACTIVOS_PRIMERO)
				: proveedores.findByBarberiaIdAndActivoTrue(barberiaId, POR_NOMBRE);
		return lista.stream().map(ProveedorResponse::de).toList();
	}

	@Transactional
	public ProveedorResponse crear(Long barberiaId, CrearProveedorRequest request) {
		Proveedor proveedor = new Proveedor(barberias.getReferenceById(barberiaId), request.nombre().trim());
		proveedor.setRubro(textoOpcional(request.rubro()));
		proveedor.setTelefono(textoOpcional(request.telefono()));
		proveedor.setEmail(email(request.email()));
		proveedor.setNotas(textoOpcional(request.notas()));
		return ProveedorResponse.de(proveedores.save(proveedor));
	}

	@Transactional
	public ProveedorResponse actualizar(Long barberiaId, Long id, ActualizarProveedorRequest request) {
		Proveedor proveedor = buscar(barberiaId, id);
		if (request.nombre() != null) {
			proveedor.setNombre(request.nombre().trim());
		}
		if (request.rubro() != null) {
			proveedor.setRubro(textoOpcional(request.rubro()));
		}
		if (request.telefono() != null) {
			proveedor.setTelefono(textoOpcional(request.telefono()));
		}
		if (request.email() != null) {
			proveedor.setEmail(email(request.email()));
		}
		if (request.notas() != null) {
			proveedor.setNotas(textoOpcional(request.notas()));
		}
		return ProveedorResponse.de(proveedor);
	}

	/** Baja lógica: los gastos viejos siguen apuntando al proveedor, pero no se ofrece para gastos nuevos. */
	@Transactional
	public void darDeBaja(Long barberiaId, Long id) {
		buscar(barberiaId, id).setActivo(false);
	}

	@Transactional
	public ProveedorResponse reactivar(Long barberiaId, Long id) {
		Proveedor proveedor = buscar(barberiaId, id);
		proveedor.setActivo(true);
		return ProveedorResponse.de(proveedor);
	}

	private Proveedor buscar(Long barberiaId, Long id) {
		return proveedores.findByIdAndBarberiaId(id, barberiaId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No encontramos ese proveedor"));
	}

	private static String email(String valor) {
		String texto = textoOpcional(valor);
		return texto == null ? null : texto.toLowerCase(Locale.ROOT);
	}

	private static String textoOpcional(String valor) {
		return valor == null || valor.isBlank() ? null : valor.trim();
	}

}
