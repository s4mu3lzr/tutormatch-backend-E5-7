package com.tutormatch.ms_core.repository;

import com.tutormatch.ms_core.entity.Inscripcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InscripcionRepository extends JpaRepository<Inscripcion, UUID> {

    /**
     * HU-10/HU-CatalogoResponseDto: Cuenta inscritos CONFIRMADOS por sesión.
     * Usado en SesionService.mapToResponseDto() para el contador de cupo.
     */
    long countBySesionIdAndEstado(UUID sesionId, String estado);

    /**
     * HU-14: Verificar si el alumno YA está inscrito en esa sesión (evitar duplicados).
     * Se verifica solo inscripciones CONFIRMADAS (las CANCELADAS no cuentan como duplicado).
     */
    Optional<Inscripcion> findBySesionIdAndAlumnoIdAndEstado(UUID sesionId, UUID alumnoId, String estado);

    /**
     * Buscar inscripción sin importar el estado. Útil para re-inscribirse tras cancelar.
     */
    Optional<Inscripcion> findBySesionIdAndAlumnoId(UUID sesionId, UUID alumnoId);

    /**
     * HU-15/HU-16: Obtener todas las inscripciones CONFIRMADAS de un alumno.
     * El filtro de fecha futura se aplica en el service consultando la sesión.
     */
    List<Inscripcion> findByAlumnoIdAndEstado(UUID alumnoId, String estado);
}
