package com.tutormatch.auth.entity;

import jakarta.persistence.*;
import java.util.Set;
import java.util.UUID;
import lombok.Data;

@Entity
@Data
@Table(name = "usuarios", schema = "schema_usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String nombre;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "usuario_roles", schema = "schema_usuarios", joinColumns = @JoinColumn(name = "usuario_id"), inverseJoinColumns = @JoinColumn(name = "rol_id"))
    private Set<Rol> roles;
}