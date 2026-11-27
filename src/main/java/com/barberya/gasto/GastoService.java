package com.barberya.gasto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.barberya.barberia.BarberiaRepository;
import com.barberya.error.DatosInvalidosException;
import com.barberya.error.RecursoNoEncontradoException;
import com.barberya.gasto.dto.ActualizarGastoRequest;
import com.barberya.gasto.dto.CrearGastoRequest;
import com.barberya.gasto.dto.GastoResponse;

/**
 * Gastos variables ({@code es_fijo = false}). Los gastos fijos no se crean, no se listan y no se tocan desde acá:
 * buscarlos por id da 404, igual que un gasto de otra barbería.
 */
@Service
public class GastoService {

	/** Días que puede abarcar el listado, contando desde y hasta. */
	static final int DIAS_MAXIMOS = 366;

	private static final Sort RECIENTES_PRIMERO = Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("id"));

	private final GastoRepository gastos;
	private final CategoriaGastoRepository categorias;
	private final ProveedorRepository proveedores;
	private final BarberiaRepository barberias;

	public GastoService(GastoRepository gastos, CategoriaGastoRepository categorias, ProveedorRepository proveedores,
			BarberiaRepository barberias) {
		this.gastos = gastos;
		this.categorias = categorias;
		this.proveedores = proveedores;
		this.barberias = barberias;
	}

	@Transactional(readOnly = true)
	public List<GastoResponse> listar(Long barberiaId, LocalDate desde, LocalDate hasta) {
		if (hasta.isBefore(desde)) {
			throw new DatosInvalidosException("hasta", "La fecha \"hasta\" no puede ser anterior a \"desde\"");
		}
		if (ChronoUnit.DAYS.between(desde, hasta) + 1 > DIAS_MAXIMOS) {
			throw new DatosInvalidosException("hasta", "El rango puede ser de hasta " + DIAS_MAXIMOS + " días");
		}
		return gastos.findByBarberiaIdAndEsFijoFalseAndFechaBetween(barberiaId, desde, hasta, RECIENTES_PRIMERO).stream()
				.map(GastoResponse::de)
				.toList();
	}

	@Transactional
	public GastoResponse crear(Long barberiaId, CrearGastoRequest request) {
		Gasto gasto = new Gasto(barberias.getReferenceById(barberiaId), request.descripcion().trim(),
				plata(request.monto()), request.fecha());
		gasto.setEsFijo(false);
		gasto.setCategoria(categoria(barberiaId, request.categoriaId()));
		if (request.proveedorId() != null) {
			gasto.setProveedor(proveedorActivo(barberiaId, request.proveedorId()));
		}
		gasto.setCantidad(request.cantidad());
		return GastoResponse.de(gastos.save(gasto));
	}

	@Transactional
	public GastoResponse actualizar(Long barberiaId, Long id, ActualizarGastoRequest request) {
		boolean quitarProveedor = Boolean.TRUE.equals(request.quitarProveedor());
		boolean quitarCantidad = Boolean.TRUE.equals(request.quitarCantidad());
		if (quitarProveedor && request.proveedorId() != null) {
			throw new DatosInvalidosException("quitarProveedor", "No podés elegir un proveedor y quitarlo a la vez");
		}
		if (quitarCantidad && request.cantidad() != null) {
			throw new DatosInvalidosException("quitarCantidad", "No podés cargar una cantidad y quitarla a la vez");
		}

		Gasto gasto = buscar(barberiaId, id);
		if (request.descripcion() != null) {
			gasto.setDescripcion(request.descripcion().trim());
		}
		if (request.monto() != null) {
			gasto.setMonto(plata(request.monto()));
		}
		if (request.fecha() != null) {
			gasto.setFecha(request.fecha());
		}
		if (request.categoriaId() != null) {
			gasto.setCategoria(categoria(barberiaId, request.categoriaId()));
		}
		if (quitarProveedor) {
			gasto.setProveedor(null);
		}
		else if (request.proveedorId() != null && !esElProveedorActual(gasto, request.proveedorId())) {
			gasto.setProveedor(proveedorActivo(barberiaId, request.proveedorId()));
		}
		if (quitarCantidad) {
			gasto.setCantidad(null);
		}
		else if (request.cantidad() != null) {
			gasto.setCantidad(request.cantidad());
		}
		return GastoResponse.de(gasto);
	}

	@Transactional
	public void borrar(Long barberiaId, Long id) {
		gastos.delete(buscar(barberiaId, id));
	}

	private Gasto buscar(Long barberiaId, Long id) {
		return gastos.findByIdAndBarberiaIdAndEsFijoFalse(id, barberiaId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No encontramos ese gasto"));
	}

	private CategoriaGasto categoria(Long barberiaId, Long id) {
		return categorias.findByIdAndBarberiaId(id, barberiaId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No encontramos esa categoría"));
	}

	/** Un proveedor dado de baja no se puede elegir para un gasto. */
	private Proveedor proveedorActivo(Long barberiaId, Long id) {
		return proveedores.findByIdAndBarberiaId(id, barberiaId)
				.filter(Proveedor::isActivo)
				.orElseThrow(() -> new RecursoNoEncontradoException("No encontramos ese proveedor"));
	}

	/** Un gasto viejo puede conservar su proveedor aunque se haya dado de baja después. */
	private static boolean esElProveedorActual(Gasto gasto, Long proveedorId) {
		return gasto.getProveedor() != null && proveedorId.equals(gasto.getProveedor().getId());
	}

	/** Ya validado con @Digits(fraction = 2): el redondeo no cambia el valor, solo normaliza la escala. */
	private static BigDecimal plata(BigDecimal valor) {
		return valor.setScale(2, RoundingMode.HALF_UP);
	}

}
