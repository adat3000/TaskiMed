package com.taskimed.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import com.taskimed.entity.Message;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long>, JpaSpecificationExecutor<Message> {

    // Encontrar mensajes enviados directamente por senderId (opcional, no usa folder)
    List<Message> findBySenderId(Long senderId);

    // Inbox → mensajes donde el usuario es destinatario
    @Query("SELECT mr.message FROM MessageRecipient mr WHERE mr.recipientId = :recipientId AND mr.folder = 'inbox' AND mr.isDeleted = false")
    List<Message> findInboxByRecipientId(@Param("recipientId") Long recipientId);

    // Outbox → mensajes enviados por un usuario
    @Query("SELECT mr.message FROM MessageRecipient mr WHERE mr.message.senderId = :senderId AND mr.folder = 'sent' AND mr.isDeleted = false")
    List<Message> findSentBySenderId(@Param("senderId") Long senderId);

    // Obtener IDs de destinatarios de un mensaje
    @Query("SELECT mr.recipientId FROM MessageRecipient mr WHERE mr.message.id = :messageId")
    List<Long> findRecipientIdsByMessageId(@Param("messageId") Long messageId);

    // Insertar destinatario a un mensaje
    @Modifying
    @Query(value = "INSERT INTO message_recipients (message_id, recipient_id, folder, status) VALUES (:messageId, :recipientId, :folder, :status)", nativeQuery = true)
    void addRecipient(@Param("messageId") Long messageId,
                      @Param("recipientId") Long recipientId,
                      @Param("folder") String folder,
                      @Param("status") String status);
    @Modifying
    @Transactional
    @Query(value = "UPDATE message_recipients SET status = :status, updated_at = CURRENT_TIMESTAMP WHERE message_id = :messageId AND recipient_id = :recipientId", nativeQuery = true)
    int updateRecipientStatus(@Param("messageId") Long messageId,
                              @Param("recipientId") Long recipientId,
                              @Param("status") String status);

    @Query("SELECT DISTINCT m FROM Message m " +
            "LEFT JOIN m.recipients r " +
            "WHERE (" +
            "  (:folder = 'SENT' AND m.senderId = :uId) OR " + 
            "  (r.recipientId = :uId AND r.folder = :folder)" +
            ") " +
            "AND (:filter IS NULL OR :filter = '' " +
            "     OR LOWER(m.subject) LIKE LOWER(CONCAT('%', :filter, '%')) " +
            "     OR LOWER(m.body) LIKE LOWER(CONCAT('%', :filter, '%'))" +
            ")")
     Page<Message> searchMessages(
             @Param("uId") Long uId, // Cambié el nombre del parámetro a uId para evitar conflictos
             @Param("folder") String folder, 
             @Param("filter") String filter, 
             Pageable pageable);
}