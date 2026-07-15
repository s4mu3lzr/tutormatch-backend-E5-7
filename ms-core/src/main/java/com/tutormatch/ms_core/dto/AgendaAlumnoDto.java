package com.tutormatch.ms_core.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de SALIDA para la Agenda del Alumno (HU-15).
 *
 * A diferencia del CatalogoSesionDto, ESTE SÍ incluye el campo "lugar"
 * porque el alumno ya está inscrito y tiene derecho a conocer el acceso.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AgendaAlumnoDto {

    /** ID de la inscripción — necesario para poder cancelarla (HU-16) */
    private UUID inscripcionId;

    private UUID sesionId;

    private String tutorNombre;

    private String titulo;

    private String descripcion;

    /**
     * LUGAR REVELADO: Solo visible para alumnos inscritos.
     * En el catálogo público este campo se omite.
     */
    private String lugar;

    private LocalDateTime fechaHora;

    private Integer cupoMaximo;

    private Integer cupoDisponible;

    private Integer inscritos;

    /** Fecha en que el alumno se inscribió */
    private LocalDateTime fechaInscripcion;
}
