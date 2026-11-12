package com.barberya.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.barberya.auth.dto.TokenResponse;
import com.barberya.barberia.MiembroBarberiaRepository;
import com.barberya.config.BarberyaProperties;
import com.barberya.config.SecurityConfig;

/** Arma los access tokens (JWT) y los valores aleatorios de refresh y recuperación. */
@Service
public class TokenService {

	private static final int BYTES_TOKEN_ALEATORIO = 32;

	private final JwtEncoder jwtEncoder;
	private final MiembroBarberiaRepository miembros;
	private final Duration duracionAccess;
	private final SecureRandom random = new SecureRandom();

	public TokenService(JwtEncoder jwtEncoder, MiembroBarberiaRepository miembros, BarberyaProperties props) {
		this.jwtEncoder = jwtEncoder;
		this.miembros = miembros;
		this.duracionAccess = Duration.ofMinutes(props.jwt().accessMinutos());
	}

	/** {@code barberiaId} y {@code rol} salen de miembros_barberia, nunca del front. */
	public TokenResponse generarAccess(Usuario usuario) {
		Instant ahora = Instant.now();
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
				.issuer(SecurityConfig.EMISOR_JWT)
				.subject(usuario.getId().toString())
				.issuedAt(ahora)
				.expiresAt(ahora.plus(duracionAccess));
		miembros.findFirstByUsuarioIdAndActivoTrue(usuario.getId()).ifPresent(miembro -> claims
				.claim("barberiaId", miembro.getBarberia().getId())
				.claim("rol", miembro.getRol().name()));
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
		return new TokenResponse(token, duracionAccess.toSeconds());
	}

	public String nuevoValorAleatorio() {
		byte[] bytes = new byte[BYTES_TOKEN_ALEATORIO];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** En la base se guarda solo este hash, nunca el valor. */
	public static String sha256(String valor) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 no disponible", e);
		}
	}

}
