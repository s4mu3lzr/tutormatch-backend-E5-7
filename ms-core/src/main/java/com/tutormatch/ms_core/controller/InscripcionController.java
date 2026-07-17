package com.tutormatch.ms_core.controller;

import com.tutormatch.ms_core.dto.AgendaAlumnoDto;
import com.tutormatch.ms_core.dto.InscripcionRequestDto;
import com.tutormatch.ms_core.entity.Inscripcion;
import com.tutormatch.ms_core.service.InscripcionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/core/inscripciones")
public class InscripcionController {

    private final InscripcionService inscripcionService;

    public InscripcionController(InscripcionService inscripcionService) {
        this.inscripcionService = inscripcionService;
    }

    // -----------------------------------------------------------------------
    // HU-14: POST — Inscribirse a una sesión
    // -----------------------------------------------------------------------

    /**
     * POST /api/core/inscripciones
     * Solo accesible por ROLE_ALUMNO.
     * El alumnoId se extrae del JWT, nunca del body.
     */
    @PostMapping
    @PreAuthorize("hasRole('ROLE_ALUMNO')")
    public ResponseEntity<Inscripcion> inscribirse(
            @RequestBody InscripcionRequestDto dto,
            @AuthenticationPrincipal Jwt jwt) {

        String correoAlumno = jwt.getClaimAsString("email");
        UUID alumnoId = UUID.fromString(jwt.getClaimAsString("usuario_id"));
        Inscripcion inscripcion = inscripcionService.inscribirse(correoAlumno, dto, alumnoId);
        return ResponseEntity.status(HttpStatus.CREATED).body(inscripcion);
    }

    // -----------------------------------------------------------------------
    // HU-15: GET — Agenda del Alumno (sesiones inscritas con lugar revelado)
    // -----------------------------------------------------------------------

    /**
     * GET /api/core/inscripciones/mi-agenda
     * Retorna las sesiones futuras a las que el alumno autenticado está inscrito.
     * Este endpoint SÍ revela el campo "lugar" (a diferencia del catálogo).
     */
    @GetMapping("/mi-agenda")
    @PreAuthorize("hasRole('ROLE_ALUMNO')")
    public ResponseEntity<List<AgendaAlumnoDto>> getMiAgendaAlumno(
            @AuthenticationPrincipal Jwt jwt) {

        UUID alumnoId = UUID.fromString(jwt.getClaimAsString("usuario_id"));
        return ResponseEntity.ok(inscripcionService.getAgendaAlumno(alumnoId));
    }

    @GetMapping("/sesiones/{sesionId}/estado")
    @PreAuthorize("hasRole('ROLE_ALUMNO')")
    public ResponseEntity<Boolean> verificarInscripcion(
            @PathVariable UUID sesionId,
            @AuthenticationPrincipal Jwt jwt) {

        UUID alumnoId = UUID.fromString(jwt.getClaimAsString("usuario_id"));
        return ResponseEntity.ok(inscripcionService.estaInscrito(sesionId, alumnoId));
    }

    // -----------------------------------------------------------------------
    // HU-16: DELETE — Cancelar inscripción
    // -----------------------------------------------------------------------

    /**
     * DELETE /api/core/inscripciones/{id}
     * Cancela la inscripción del alumno autenticado.
     * Incrementa el cupo de la sesión en 1.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ALUMNO')")
    public ResponseEntity<Void> cancelarInscripcion(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        String correoAlumno = jwt.getClaimAsString("email");
        UUID alumnoId = UUID.fromString(jwt.getClaimAsString("usuario_id"));
        inscripcionService.cancelarInscripcion(correoAlumno, id, alumnoId);
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
