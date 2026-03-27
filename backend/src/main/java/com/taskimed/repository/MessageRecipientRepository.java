package com.taskimed.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.taskimed.entity.MessageRecipient;

import java.util.Optional;

@Repository
public interface MessageRecipientRepository extends JpaRepository<MessageRecipient, Long> {

    // Encontrar el registro específico para un mensaje y un usuario
    Optional<MessageRecipient> findByMessageIdAndRecipientId(Long messageId, Long recipientId);

    // Consulta de actualización genérica para el estado/carpeta
    @Modifying
    @Query("UPDATE MessageRecipient mr SET mr.status = :status, mr.isStarred = :isStarred, mr.isArchived = :isArchived, mr.folder = :folder WHERE mr.message.id = :messageId AND mr.recipientId = :userId")
    int updateStatusAndFolder(
            @Param("messageId") Long messageId,
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("isStarred") Boolean isStarred,
            @Param("isArchived") Boolean isArchived,
            @Param("folder") String folder
    );
    
    long countByMessageIdAndIsDeleted(Long messageId, Boolean isDeleted);
}