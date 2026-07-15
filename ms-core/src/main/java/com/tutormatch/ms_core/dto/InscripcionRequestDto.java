package com.tutormatch.ms_core.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO de ENTRADA para inscribirse a una sesión (HU-14).
 * El alumnoId NO viene aquí — se extrae del JWT en el service.
 */
@Getter
@Setter
@NoArgsConstructor
public class InscripcionRequestDto {

    private UUID sesionId;
}
