package com.barberya.barberia.dto;

import com.barberya.auth.dto.TokenResponse;

/** El token nuevo ya trae barberiaId y rol, para que el front no tenga que volver a loguearse. */
public record BarberiaCreadaResponse(BarberiaResponse barberia, TokenResponse token) {
}
