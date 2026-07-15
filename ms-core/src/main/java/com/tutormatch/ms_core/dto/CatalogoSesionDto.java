package com.tutormatch.ms_core.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de SALIDA para el Catálogo público de sesiones (HU-13).
 *
 * IMPORTANTE SEGURIDAD: Este DTO NO incluye el campo "lugar".
 * El lugar solo se revela a los alumnos inscritos en AgendaAlumnoDto (HU-15).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CatalogoSesionDto {

    private UUID id;

    /** Nombre del tutor — visible en el catálogo para búsqueda/filtro */
    private String tutorNombre;

    private String titulo;

    private String descripcion;

    /** OMITIDO: lugar — solo se revela a inscritos (HU-13 seguridad) */

    private LocalDateTime fechaHora;

    private Integer cupoMaximo;

    private Integer cupoDisponible;

    /** Alumnos inscritos con estado CONFIRMADA */
    private Integer inscritos;

    /**
     * Calificación promedio del tutor (de ms-evaluaciones).
     * null → se muestra como "Nuevo" en el frontend.
     * Por ahora siempre es null hasta que EP-05 lo implemente.
     */
    private Double calificacion;
}
