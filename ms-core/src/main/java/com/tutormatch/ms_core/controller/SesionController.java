package com.tutormatch.ms_core.controller;

import com.tutormatch.ms_core.dto.CatalogoSesionDto;
import com.tutormatch.ms_core.dto.SesionRequestDto;
import com.tutormatch.ms_core.dto.SesionResponseDto;
import com.tutormatch.ms_core.dto.SesionUpdateDto;
import com.tutormatch.ms_core.service.SesionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/core/sesiones-tutorias")
public class SesionController {

    private final SesionService sesionService;

    public SesionController(SesionService sesionService) {
        this.sesionService = sesionService;
    }

    // -----------------------------------------------------------------------
    // HU-13: GET — Catálogo público (sin autenticación requerida)
    // -----------------------------------------------------------------------

    /**
     * GET /api/core/sesiones-tutorias/catalogo
     * Endpoint público. Retorna sesiones ACTIVAS, futuras y con cupo > 0.
     * Acepta filtros opcionales por query param. El campo "lugar" NO se incluye.
     *
     * @param materia Texto para buscar en el título de la sesión
     * @param tutor   Texto para buscar en el nombre del tutor
     * @param fecha   Fecha exacta (formato yyyy-MM-dd)
     */
    @GetMapping("/catalogo")
    public ResponseEntity<List<CatalogoSesionDto>> getCatalogo(
            @RequestParam(required = false) String materia,
            @RequestParam(required = false) String tutor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {

        return ResponseEntity.ok(sesionService.getCatalogo(materia, tutor, fecha));
    }

    // -----------------------------------------------------------------------
    // HU-09: POST — Publicar nueva sesión de tutoría
    // -----------------------------------------------------------------------

    /**
     * POST /api/core/sesiones-tutorias
     * Solo accesible por ROLE_TUTOR.
     * tutorId y tutorNombre se extraen del JWT (evita suplantación).
     */
    @PostMapping
    @PreAuthorize("hasRole('ROLE_TUTOR')")
    public ResponseEntity<SesionResponseDto> publicarSesion(
            @RequestBody SesionRequestDto dto,
            @AuthenticationPrincipal Jwt jwt) {

        UUID tutorId = UUID.fromString(jwt.getClaimAsString("usuario_id"));
        String tutorNombre = jwt.getClaimAsString("nombre");

        SesionResponseDto sesionCreada = sesionService.publicarSesion(dto, tutorId, tutorNombre);
        return ResponseEntity.status(HttpStatus.CREATED).body(sesionCreada);
    }

    // -----------------------------------------------------------------------
    // HU-10: GET — Agenda del Tutor
    // -----------------------------------------------------------------------

    @GetMapping("/mi-agenda")
    @PreAuthorize("hasRole('ROLE_TUTOR')")
    public ResponseEntity<List<SesionResponseDto>> obtenerMiAgenda(
            @AuthenticationPrincipal Jwt jwt) {

        UUID tutorId = UUID.fromString(jwt.getClaimAsString("usuario_id"));
        return ResponseEntity.ok(sesionService.obtenerAgendaTutor(tutorId));
    }

    // -----------------------------------------------------------------------
    // HU-11: PUT — Editar sesión
    // -----------------------------------------------------------------------

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_TUTOR')")
    public ResponseEntity<SesionResponseDto> actualizarSesion(
            @PathVariable UUID id,
            @RequestBody SesionUpdateDto dto,
            @AuthenticationPrincipal Jwt jwt) {

        UUID tutorId = UUID.fromString(jwt.getClaimAsString("usuario_id"));
        return ResponseEntity.ok(sesionService.actualizarSesion(id, dto, tutorId));
    }

    // -----------------------------------------------------------------------
    // HU-12: DELETE — Cancelar sesión (borrado lógico)
    // -----------------------------------------------------------------------

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_TUTOR')")
    public ResponseEntity<Void> cancelarSesion(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        UUID tutorId = UUID.fromString(jwt.getClaimAsString("usuario_id"));
        sesionService.cancelarSesion(id, tutorId);
        return ResponseEntity.noContent().build();
    }

    // -----------------------------------------------------------------------
    // Manejo de errores
    // -----------------------------------------------------------------------

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleValidationError(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<String> handleSecurityError(SecurityException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
    }
}
