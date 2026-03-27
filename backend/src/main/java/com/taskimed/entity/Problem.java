package com.taskimed.entity;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "problems")
@Getter // Mejor que @Data para evitar efectos secundarios en JPA
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class Problem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include // 👈 ESTO es lo que arregla que el conteo no sea siempre 1
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String alias;

    @Builder.Default
    @ManyToMany(mappedBy = "problems", fetch = FetchType.LAZY)
    @JsonIgnore // 👈 VITAL: Detiene el bucle infinito de Jackson
    @ToString.Exclude // 👈 VITAL: Detiene el bucle infinito del log/consola
    private Set<Patient> patients = new HashSet<>();
}