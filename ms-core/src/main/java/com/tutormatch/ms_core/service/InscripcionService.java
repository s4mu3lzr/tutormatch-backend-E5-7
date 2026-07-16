package com.tutormatch.ms_core.service;

import com.tutormatch.ms_core.dto.AgendaAlumnoDto;
import com.tutormatch.ms_core.dto.InscripcionRequestDto;
import com.tutormatch.ms_core.entity.Inscripcion;
import com.tutormatch.ms_core.entity.Sesion;
import com.tutormatch.ms_core.repository.InscripcionRepository;
import com.tutormatch.ms_core.repository.SesionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InscripcionService {

    private static final String INSCRIPCION_CONFIRMADA = "CONFIRMADA";
    private static final String INSCRIPCION_CANCELADA  = "CANCELADA";
    private static final String SESION_ACTIVA          = "ACTIVA";

    private final InscripcionRepository inscripcionRepository;
    private final SesionRepository sesionRepository;
    private final SesionService sesionService;

    public InscripcionService(InscripcionRepository inscripcionRepository,
                              SesionRepository sesionRepository,
                              SesionService sesionService) {
        this.inscripcionRepository = inscripcionRepository;
        this.sesionRepository = sesionRepository;
        this.sesionService = sesionService;
    }

    // =========================================================================
    // HU-14: Inscribirse a una sesión
    // =========================================================================

    /**
     * Inscribe a un alumno en una sesión.
     *
     * Validaciones:
     * 1. La sesión existe y está ACTIVA.
     * 2. El alumno no está ya inscrito (no hay duplicado CONFIRMADO).
     * 3. Todavía hay cupo disponible (> 0).
     *
     * Al inscribirse: cupoDisponible -= 1.
     */
    @Transactional
    public Inscripcion inscribirse(InscripcionRequestDto dto, UUID alumnoId) {

        // 1. Verificar que la sesión existe y está activa
        Sesion sesion = sesionRepository.findById(dto.getSesionId())
            .orElseThrow(() -> new IllegalArgumentException("La sesión no existe."));

        if (!SESION_ACTIVA.equals(sesion.getEstado())) {
            throw new IllegalArgumentException("Esta sesión ya no está disponible.");
        }

        if (sesion.getFechaHora().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("No puedes inscribirte a una sesión que ya pasó.");
        }

        // 2. Buscar si ya existe un registro de inscripción (confirmada o cancelada)
        Inscripcion inscripcion = inscripcionRepository
            .findBySesionIdAndAlumnoId(dto.getSesionId(), alumnoId)
            .orElse(new Inscripcion());

        if (INSCRIPCION_CONFIRMADA.equals(inscripcion.getEstado())) {
            throw new IllegalArgumentException("Ya estás inscrito en esta sesión.");
        }

        // 3. Verificar cupo
        if (sesion.getCupoDisponible() <= 0) {
            throw new IllegalArgumentException("Esta sesión ya no tiene cupo disponible.");
        }

        // 4. Crear o actualizar inscripción
        inscripcion.setSesionId(dto.getSesionId());
        inscripcion.setAlumnoId(alumnoId);
        inscripcion.setEstado(INSCRIPCION_CONFIRMADA);
        inscripcion.setFechaInscripcion(LocalDateTime.now());

        // 5. Decrementar cupo
        sesion.setCupoDisponible(sesion.getCupoDisponible() - 1);
        sesionRepository.save(sesion);

        return inscripcionRepository.save(inscripcion);
    }

    // =========================================================================
    // HU-15: Agenda del Alumno — sesiones futuras inscritas (con lugar revelado)
    // =========================================================================

    /**
     * Retorna las sesiones futuras a las que el alumno está inscrito (estado CONFIRMADA).
     * A diferencia del catálogo, el DTO de respuesta SÍ incluye el campo "lugar".
     * Las sesiones se ordenan cronológicamente (la más próxima primero).
     *
     * @param alumnoId UUID del alumno extraído del JWT
     */
    public List<AgendaAlumnoDto> getAgendaAlumno(UUID alumnoId) {
        List<Inscripcion> inscripciones = inscripcionRepository
            .findByAlumnoIdAndEstado(alumnoId, INSCRIPCION_CONFIRMADA);

        LocalDateTime ahora = LocalDateTime.now();

        return inscripciones.stream()
            // Solo sesiones futuras
            .filter(insc -> {
                Sesion s = sesionRepository.findById(insc.getSesionId()).orElse(null);
                return s != null && SESION_ACTIVA.equals(s.getEstado()) && s.getFechaHora().isAfter(ahora);
            })
            // Ordenar cronológicamente
            .sorted((a, b) -> {
                Sesion sA = sesionRepository.findById(a.getSesionId()).orElseThrow();
                Sesion sB = sesionRepository.findById(b.getSesionId()).orElseThrow();
                return sA.getFechaHora().compareTo(sB.getFechaHora());
            })
            .map(insc -> {
                Sesion sesion = sesionRepository.findById(insc.getSesionId()).orElseThrow();
                int inscritos = (int) inscripcionRepository
                    .countBySesionIdAndEstado(sesion.getId(), INSCRIPCION_CONFIRMADA);

                return new AgendaAlumnoDto(
                    insc.getId(),
                    sesion.getId(),
                    sesion.getTutorNombre(),
                    sesion.getTitulo(),
                    sesion.getDescripcion(),
                    sesion.getLugar(),   // LUGAR REVELADO al inscrito
                    sesion.getFechaHora(),
                    sesion.getCupoMaximo(),
                    sesion.getCupoDisponible(),
                    inscritos,
                    insc.getFechaInscripcion()
                );
            })
            .collect(Collectors.toList());
    }

    // =========================================================================
    // HU-16: Cancelar inscripción
    // =========================================================================

    /**
     * Cancela la inscripción del alumno en una sesión.
     *
     * Al cancelar:
     * - El estado de la inscripción pasa a CANCELADA.
     * - El cupoDisponible de la sesión se incrementa en 1.
     * - La sesión vuelve a aparecer en el catálogo (si aún está activa y es futura).
     *
     * @param inscripcionId ID de la inscripción a cancelar
     * @param alumnoId      UUID del alumno autenticado (del JWT)
     */
    @Transactional
    public void cancelarInscripcion(UUID inscripcionId, UUID alumnoId) {

        Inscripcion inscripcion = inscripcionRepository.findById(inscripcionId)
            .orElseThrow(() -> new IllegalArgumentException("Inscripción no encontrada."));

        // Solo el alumno propietario puede cancelar su inscripción
        if (!inscripcion.getAlumnoId().equals(alumnoId)) {
            throw new SecurityException("No tienes permiso para cancelar esta inscripción.");
        }

        // Ya está cancelada
        if (INSCRIPCION_CANCELADA.equals(inscripcion.getEstado())) {
            throw new IllegalArgumentException("Esta inscripción ya está cancelada.");
        }

        // Cambiar estado de la inscripción
        inscripcion.setEstado(INSCRIPCION_CANCELADA);
        inscripcionRepository.save(inscripcion);

        // Incrementar cupo de la sesión
        Sesion sesion = sesionRepository.findById(inscripcion.getSesionId()).orElse(null);
        if (sesion != null && SESION_ACTIVA.equals(sesion.getEstado())) {
            sesion.setCupoDisponible(sesion.getCupoDisponible() + 1);
            sesionRepository.save(sesion);
        }
    }
}
