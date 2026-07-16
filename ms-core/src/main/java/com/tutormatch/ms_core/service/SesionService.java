package com.tutormatch.ms_core.service;

import com.tutormatch.ms_core.dto.*;
import com.tutormatch.ms_core.entity.Sesion;
import com.tutormatch.ms_core.repository.InscripcionRepository;
import com.tutormatch.ms_core.repository.SesionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SesionService {

    private static final String ESTADO_ACTIVA    = "ACTIVA";
    private static final String ESTADO_CANCELADA = "CANCELADA";
    private static final String INSCRIPCION_CONFIRMADA = "CONFIRMADA";

    private final SesionRepository sesionRepository;
    private final InscripcionRepository inscripcionRepository;

    public SesionService(SesionRepository sesionRepository,
                         InscripcionRepository inscripcionRepository) {
        this.sesionRepository = sesionRepository;
        this.inscripcionRepository = inscripcionRepository;
    }

    // =========================================================================
    // HU-09: Publicar nueva sesión
    // =========================================================================

    @Transactional
    public SesionResponseDto publicarSesion(SesionRequestDto dto, UUID tutorId, String tutorNombre) {

        // --- VALIDACIÓN 1: Fecha futura (mínimo 1 hora) ---
        LocalDateTime limiteMinimo = LocalDateTime.now().plusHours(1);
        if (dto.getFechaHora() == null || dto.getFechaHora().isBefore(limiteMinimo)) {
            throw new IllegalArgumentException(
                "La fecha de la sesión debe ser al menos 1 hora en el futuro."
            );
        }

        // --- VALIDACIÓN 2: Cupo positivo ---
        if (dto.getCupoMaximo() == null || dto.getCupoMaximo() < 1) {
            throw new IllegalArgumentException("El cupo máximo debe ser al menos 1 alumno.");
        }

        // --- VALIDACIÓN 3: Cruce de horarios con sesiones ACTIVAS (ventana ±2h) ---
        List<Sesion> sesionesFuturas = sesionRepository.findByTutorIdAndEstadoAndFechaHoraAfter(
            tutorId, ESTADO_ACTIVA, LocalDateTime.now()
        );

        LocalDateTime nuevaFecha = dto.getFechaHora();
        for (Sesion existente : sesionesFuturas) {
            LocalDateTime inicio = existente.getFechaHora().minusHours(2);
            LocalDateTime fin    = existente.getFechaHora().plusHours(2);
            if (nuevaFecha.isAfter(inicio) && nuevaFecha.isBefore(fin)) {
                throw new IllegalArgumentException(
                    "Ya tienes una sesión programada cerca de ese horario (" +
                    existente.getFechaHora() + "). Debe haber al menos 2 horas de diferencia."
                );
            }
        }

        // --- GUARDAR ---
        Sesion nueva = new Sesion();
        nueva.setTutorId(tutorId);
        nueva.setTutorNombre(tutorNombre != null ? tutorNombre : "Tutor");
        nueva.setTitulo(dto.getTitulo());
        nueva.setDescripcion(dto.getDescripcion());
        nueva.setLugar(dto.getLugar());
        nueva.setFechaHora(dto.getFechaHora());
        nueva.setCupoMaximo(dto.getCupoMaximo());
        nueva.setCupoDisponible(dto.getCupoMaximo());
        nueva.setEstado(ESTADO_ACTIVA);

        return mapToResponseDto(sesionRepository.save(nueva));
    }

    // =========================================================================
    // HU-10: Agenda del Tutor — sesiones futuras ordenadas
    // =========================================================================

    public List<SesionResponseDto> obtenerAgendaTutor(UUID tutorId) {
        return sesionRepository
            .findByTutorIdAndEstadoAndFechaHoraAfterOrderByFechaHoraAsc(
                tutorId, ESTADO_ACTIVA, LocalDateTime.now()
            )
            .stream()
            .map(this::mapToResponseDto)
            .collect(Collectors.toList());
    }

    // =========================================================================
    // HU-11: Editar sesión
    // =========================================================================

    @Transactional
    public SesionResponseDto actualizarSesion(UUID sesionId, SesionUpdateDto dto, UUID tutorId) {

        Sesion sesion = sesionRepository.findById(sesionId)
            .orElseThrow(() -> new IllegalArgumentException("Sesión no encontrada."));

        if (!sesion.getTutorId().equals(tutorId)) {
            throw new SecurityException("No tienes permiso para editar esta sesión.");
        }
        if (ESTADO_CANCELADA.equals(sesion.getEstado())) {
            throw new IllegalArgumentException("No se puede editar una sesión cancelada.");
        }

        long inscritos = inscripcionRepository.countBySesionIdAndEstado(sesionId, INSCRIPCION_CONFIRMADA);

        if (dto.getFechaHora() != null && !dto.getFechaHora().equals(sesion.getFechaHora())) {
            if (inscritos > 0) {
                throw new IllegalArgumentException(
                    "No puedes cambiar la fecha/hora porque ya hay " + inscritos + " alumno(s) inscrito(s)."
                );
            }
            if (dto.getFechaHora().isBefore(LocalDateTime.now().plusHours(1))) {
                throw new IllegalArgumentException("La nueva fecha debe ser al menos 1 hora en el futuro.");
            }
            sesion.setFechaHora(dto.getFechaHora());
        }

        if (dto.getCupoMaximo() != null) {
            if (dto.getCupoMaximo() < 1) {
                throw new IllegalArgumentException("El cupo máximo debe ser al menos 1.");
            }
            if (dto.getCupoMaximo() < inscritos) {
                throw new IllegalArgumentException(
                    "El nuevo cupo (" + dto.getCupoMaximo() + ") no puede ser menor " +
                    "al número de alumnos ya inscritos (" + inscritos + ")."
                );
            }
            int diferencia = dto.getCupoMaximo() - sesion.getCupoMaximo();
            sesion.setCupoMaximo(dto.getCupoMaximo());
            sesion.setCupoDisponible(Math.max(0, sesion.getCupoDisponible() + diferencia));
        }

        if (dto.getTitulo()      != null) sesion.setTitulo(dto.getTitulo());
        if (dto.getDescripcion() != null) sesion.setDescripcion(dto.getDescripcion());
        if (dto.getLugar()       != null) sesion.setLugar(dto.getLugar());

        return mapToResponseDto(sesionRepository.save(sesion));
    }

    // =========================================================================
    // HU-12: Cancelar sesión (borrado lógico)
    // =========================================================================

    @Transactional
    public void cancelarSesion(UUID sesionId, UUID tutorId) {
        Sesion sesion = sesionRepository.findById(sesionId)
            .orElseThrow(() -> new IllegalArgumentException("Sesión no encontrada."));

        if (!sesion.getTutorId().equals(tutorId)) {
            throw new SecurityException("No tienes permiso para cancelar esta sesión.");
        }
        if (ESTADO_CANCELADA.equals(sesion.getEstado())) {
            throw new IllegalArgumentException("La sesión ya está cancelada.");
        }

        sesion.setEstado(ESTADO_CANCELADA);
        sesionRepository.save(sesion);

        long inscritos = inscripcionRepository.countBySesionIdAndEstado(sesionId, INSCRIPCION_CONFIRMADA);
        if (inscritos > 0) {
            System.out.println("[EP-06 PENDIENTE] Sesión " + sesionId + " cancelada con " +
                inscritos + " alumno(s) inscritos. Se debe notificar por correo.");
        }
    }

    // =========================================================================
    // HU-13: Catálogo público con filtros opcionales
    // =========================================================================

    /**
     * Retorna el catálogo de sesiones disponibles para el público.
     * Los parámetros son opcionales (null = sin filtro).
     * IMPORTANTE: El DTO resultante (CatalogoSesionDto) NO incluye el campo "lugar".
     *
     * @param materia  Texto parcial para buscar en el título de la sesión
     * @param tutor    Texto parcial para buscar en el nombre del tutor
     * @param fecha    Fecha exacta (yyyy-MM-dd) para filtrar
     */
    public List<CatalogoSesionDto> getCatalogo(String materia, String tutor, LocalDate fecha) {
        // Convertimos LocalDate a String para la native query (evita el bug lower(bytea))
        String fechaStr = (fecha != null) ? fecha.toString() : null;
        return sesionRepository
            .findCatalogo(LocalDateTime.now(), materia, tutor, fechaStr)
            .stream()
            .map(this::mapToCatalogoDto)
            .collect(Collectors.toList());
    }

    // =========================================================================
    // Métodos auxiliares: Entity → DTO
    // =========================================================================

    public SesionResponseDto mapToResponseDto(Sesion sesion) {
        int inscritos = (int) inscripcionRepository
            .countBySesionIdAndEstado(sesion.getId(), INSCRIPCION_CONFIRMADA);

        return new SesionResponseDto(
            sesion.getId(),
            sesion.getTutorId(),
            sesion.getTutorNombre(),
            sesion.getTitulo(),
            sesion.getDescripcion(),
            sesion.getLugar(),
            sesion.getFechaHora(),
            sesion.getCupoMaximo(),
            sesion.getCupoDisponible(),
            inscritos,
            sesion.getEstado(),
            sesion.getCreadoEn()
        );
    }

    private CatalogoSesionDto mapToCatalogoDto(Sesion sesion) {
        int inscritos = (int) inscripcionRepository
            .countBySesionIdAndEstado(sesion.getId(), INSCRIPCION_CONFIRMADA);

        return new CatalogoSesionDto(
            sesion.getId(),
            sesion.getTutorNombre(),
            sesion.getTitulo(),
            sesion.getDescripcion(),
            // lugar NO incluido en catálogo (seguridad HU-13)
            sesion.getFechaHora(),
            sesion.getCupoMaximo(),
            sesion.getCupoDisponible(),
            inscritos,
            null   // calificación → null hasta que EP-05 lo implemente
        );
    }

    /**
     * Búsqueda de sesión por ID con acceso público (para InscripcionService).
     */
    public Sesion findById(UUID id) {
        return sesionRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Sesión no encontrada: " + id));
    }
}
