package com.tutormatch.ms_core.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de SALIDA con los datos completos de una sesión.
 * Nunca se devuelve la Entity directamente en el controlador.
 * Incluye `inscritos` (calculado en el service) para mostrar el contador en HU-10.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SesionResponseDto {

    private UUID id;
    private UUID tutorId;
    private String tutorNombre;
    private String titulo;
    private String descripcion;
    private String lugar;
    private LocalDateTime fechaHora;
    private Integer cupoMaximo;
    private Integer cupoDisponible;
    /** Cantidad de alumnos con inscripción CONFIRMADA en esta sesión */
    private Integer inscritos;
    /** Estado lógico: ACTIVA | CANCELADA */
    private String estado;
    private LocalDateTime creadoEn;
}
