package com.tutormatch.ms_core.repository;

import com.tutormatch.ms_core.entity.Sesion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface SesionRepository extends JpaRepository<Sesion, UUID> {

    /**
     * HU-09/Validación: Busca sesiones ACTIVAS futuras de un tutor para detectar cruce de horarios.
     */
    List<Sesion> findByTutorIdAndEstadoAndFechaHoraAfter(UUID tutorId, String estado, LocalDateTime fecha);

    /**
     * HU-10: Sesiones ACTIVAS futuras del tutor, ordenadas cronológicamente.
     */
    List<Sesion> findByTutorIdAndEstadoAndFechaHoraAfterOrderByFechaHoraAsc(
            UUID tutorId, String estado, LocalDateTime fecha);

    /**
     * HU-13: Catálogo público con filtros opcionales.
     * Usa nativeQuery=true para evitar el bug de Hibernate que traduce
     * LOWER() sobre columnas JPA como lower(bytea) en lugar de lower(text).
     *
     * Los filtros son opcionales (NULL = sin filtro):
     *   :materia  → búsqueda parcial en título (case-insensitive)
     *   :tutor    → búsqueda parcial en nombre del tutor (case-insensitive)
     *   :fecha    → fecha exacta (yyyy-MM-dd)
     */
    @Query(value = """
            SELECT * FROM schema_core.sesiones s
            WHERE s.estado = 'ACTIVA'
              AND s.fecha_hora > :ahora
              AND s.cupo_disponible > 0
              AND (:materia IS NULL OR lower(s.titulo\\:\\:text) LIKE lower(('%' || :materia || '%')))
              AND (:tutor   IS NULL OR lower(s.tutor_nombre\\:\\:text) LIKE lower(('%' || :tutor   || '%')))
              AND (:fecha   IS NULL OR cast(s.fecha_hora as date) = cast(:fecha as date))
            ORDER BY s.fecha_hora ASC
            """, nativeQuery = true)
    List<Sesion> findCatalogo(
            @Param("ahora")   LocalDateTime ahora,
            @Param("materia") String materia,
            @Param("tutor")   String tutor,
            @Param("fecha")   String fecha);   // String para compatibilidad nativeQuery
}
