package com.tutormatch.ms_core.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "sesiones", schema = "schema_core")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Sesion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // El tutor_id se extrae del JWT, no es FK física (desacoplamiento entre microservicios)
    @Column(name = "tutor_id", nullable = false)
    private UUID tutorId;

    /**
     * Nombre del tutor guardado al momento de crear la sesión.
     * Se extrae del claim "nombre" del JWT para evitar llamadas HTTP a ms-usuarios.
     * Permite filtrar en el catálogo por nombre del tutor (HU-13).
     */
    @Column(name = "tutor_nombre", length = 255)
    private String tutorNombre;

    @Column(name = "titulo", nullable = false, length = 255)
    private String titulo;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @Column(name = "cupo_maximo", nullable = false)
    private Integer cupoMaximo;

    @Column(name = "cupo_disponible", nullable = false)
    private Integer cupoDisponible;

    /** Lugar / ubicación donde se impartirá la tutoría (aula, sala, en línea, etc.) */
    @Column(name = "lugar", length = 255)
    private String lugar;

    /**
     * Estado lógico de la sesión.
     * Valores posibles: ACTIVA | CANCELADA
     * HU-12: Al cancelar se cambia a CANCELADA (borrado lógico, no se elimina la fila).
     */
    @Column(name = "estado", nullable = false, length = 50)
    private String estado = "ACTIVA";

    @Column(name = "creado_en", updatable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    protected void onCreate() {
        this.creadoEn = LocalDateTime.now();
        if (this.estado == null) {
            this.estado = "ACTIVA";
        }
    }
}
