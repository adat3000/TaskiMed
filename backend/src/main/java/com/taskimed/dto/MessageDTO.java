package com.taskimed.dto;

import lombok.*;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageDTO implements Serializable{
	private static final long serialVersionUID = 1L;
    private Long id;

    private Long senderId;
    private String senderName;   // lo agregaste y está bien

    private String subject;
    private String body;

    private Date createdAt;
    private Date updatedAt;
    
    // ===== CAMPOS NUEVOS PARA COINCIDIR CON EL FRONTEND =====
    private Boolean read; // Mensaje leído
    private Boolean starred; // Destacado
    private Boolean archived; // Archivado

    private String folder;  // inbox | sent | archive
    private String status;  // read | unread

    // Lista de destinatarios
    private List<Long> recipientIds;
    private String recipientNames;
}
