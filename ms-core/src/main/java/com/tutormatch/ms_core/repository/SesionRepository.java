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
     * Solo muestra sesiones ACTIVAS, futuras y con cupo > 0.
     * Filtros opcionales: materia (LIKE en titulo), tutor (LIKE en tutorNombre), fecha (date match).
     */
    @Query("""
            SELECT s FROM Sesion s
            WHERE s.estado = 'ACTIVA'
            AND s.fechaHora > :ahora
            AND s.cupoDisponible > 0
            AND (:materia IS NULL OR LOWER(s.titulo) LIKE LOWER(CONCAT('%', :materia, '%')))
            AND (:tutor IS NULL OR LOWER(s.tutorNombre) LIKE LOWER(CONCAT('%', :tutor, '%')))
            AND (:fecha IS NULL OR CAST(s.fechaHora AS date) = :fecha)
            ORDER BY s.fechaHora ASC
            """)
    List<Sesion> findCatalogo(
            @Param("ahora") LocalDateTime ahora,
            @Param("materia") String materia,
            @Param("tutor") String tutor,
            @Param("fecha") LocalDate fecha);
}
