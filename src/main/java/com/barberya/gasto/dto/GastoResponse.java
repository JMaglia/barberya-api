package com.barberya.gasto.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import com.barberya.gasto.Gasto;
import com.barberya.gasto.Proveedor;

/** {@code precioUnitario} = monto / cantidad, redondeado a 2 decimales (HALF_UP); null si no hay cantidad. No se guarda. */
public record GastoResponse(Long id, String descripcion, BigDecimal monto, LocalDate fecha, Integer cantidad,
		BigDecimal precioUnitario, Categoria categoria, ProveedorResumen proveedor) {

	public record Categoria(Long id, String nombre) {
	}

	public record ProveedorResumen(Long id, String nombre, boolean activo) {
	}

	public static GastoResponse de(Gasto g) {
		Categoria categoria = g.getCategoria() == null ? null
				: new Categoria(g.getCategoria().getId(), g.getCategoria().getNombre());
		Proveedor p = g.getProveedor();
		ProveedorResumen proveedor = p == null ? null : new ProveedorResumen(p.getId(), p.getNombre(), p.isActivo());
		return new GastoResponse(g.getId(), g.getDescripcion(), g.getMonto(), g.getFecha(), g.getCantidad(),
				precioUnitario(g.getMonto(), g.getCantidad()), categoria, proveedor);
	}

	static BigDecimal precioUnitario(BigDecimal monto, Integer cantidad) {
		return cantidad == null ? null : monto.divide(BigDecimal.valueOf(cantidad), 2, RoundingMode.HALF_UP);
	}

}
