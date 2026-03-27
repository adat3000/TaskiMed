package com.taskimed.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.taskimed.entity.InviteToken;

import java.time.LocalDateTime;
import java.util.Optional;

public interface InviteTokenRepository extends JpaRepository<InviteToken, Long> {

    Optional<InviteToken> findByToken(String token);

    // 👇 Este es el nuevo método: obtiene el token más reciente no usado
    Optional<InviteToken> findTopByEmailAndUsedFalseOrderByCreatedAtDesc(String email);

    // 👇 Este método permitirá eliminar tokens antiguos (si quieres limpieza programada)
    @Modifying
    @Query("DELETE FROM InviteToken i WHERE i.createdAt < :limitDate AND i.used = false")
    void deleteAllExpired(@Param("limitDate") LocalDateTime limitDate);
}
