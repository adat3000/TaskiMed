package com.taskimed.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.taskimed.entity.Team;

import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long>, JpaSpecificationExecutor<Team> {
    
    Optional<Team> findByNameIgnoreCase(String name);

    /**
     * Recupera un equipo por su ID cargando inmediatamente la colección de miembros.
     * Esto evita el error 'failed to lazily initialize a collection' al cerrar la sesión.
     */
    @Query("SELECT t FROM Team t LEFT JOIN FETCH t.members WHERE t.id = :id")
    Optional<Team> findByIdWithMembers(@Param("id") Long id);
}