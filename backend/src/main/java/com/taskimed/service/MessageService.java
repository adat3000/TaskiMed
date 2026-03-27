package com.taskimed.service;

import java.util.List;
import org.springframework.data.domain.Page;

import com.taskimed.dto.MessageDTO;

import java.util.Map;

public interface MessageService {

    // Enviar mensaje a uno o varios destinatarios
    MessageDTO sendMessage(MessageDTO messageDTO);

    // Obtener mensaje por ID
    MessageDTO getMessageById(Long id, Long userId);

    // Obtener mensajes enviados por un usuario (Outbox)
    List<MessageDTO> getMessagesBySender(Long senderId);

    // Obtener mensajes recibidos por un usuario (Inbox)
    List<MessageDTO> getInboxMessages(Long userId);

    // Eliminar mensaje (elimina tanto message como message_recipients)
    void deleteMessage(Long id);
 // Marcar como leído/no leído para un usuario en particular
    void markMessageStatus(Long messageId, Long userId, String status);
    
 // Nuevo: Actualizar estado y carpetas de un mensaje para un usuario
    MessageDTO updateMessageState(Long messageId, Long userId, MessageDTO messageDTO);
    
    void deleteMessageForUser(Long messageId, Long userId);

    Page<MessageDTO> getPage(int pageNumber, int pageSize, String filtro, String sortField, String sortDir, Map<String, String> customFilters);
    
}
