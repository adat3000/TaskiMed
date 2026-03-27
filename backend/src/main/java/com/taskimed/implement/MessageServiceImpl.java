package com.taskimed.implement;

import com.taskimed.dto.MessageDTO;
import com.taskimed.entity.Message;
import com.taskimed.entity.MessageRecipient;
import com.taskimed.entity.User;
import com.taskimed.repository.MessageRecipientRepository;
import com.taskimed.repository.MessageRepository;
import com.taskimed.repository.UserRepository;
import com.taskimed.service.MessageService;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.criteria.Expression;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class MessageServiceImpl implements MessageService {

	private final MessageRepository messageRepository;
	private final UserRepository userRepository;
	private final MessageRecipientRepository messageRecipientRepository; // <-- Nuevo

	/**
	 * Enviar mensaje a uno o varios destinatarios
	 */
	@Override
	public MessageDTO sendMessage(MessageDTO messageDTO) {
		// 1. Crear la entidad principal
		Message message = Message.builder().senderId(messageDTO.getSenderId()).subject(messageDTO.getSubject())
				.body(messageDTO.getBody()).createdAt(new Date()).updatedAt(new Date()).build();

		// Guardamos para obtener el ID
		Message savedMessage = messageRepository.save(message);

		// 2. Crear registros para los DESTINATARIOS (Inbox)
		if (messageDTO.getRecipientIds() != null) {
			for (Long recipientId : messageDTO.getRecipientIds()) {
				MessageRecipient mr = MessageRecipient.builder().message(savedMessage).recipientId(recipientId)
						.folder("inbox").status("unread").createdAt(new Date()) // <--- Asignación manual
						.updatedAt(new Date()) // <--- Asignación manual
						.isDeleted(false) // <--- CRUCIAL
						.isStarred(false).isArchived(false).build();
				messageRecipientRepository.save(mr);
			}
		}

		// 3. Crear registro para el REMITENTE (Sent)
		MessageRecipient senderRecord = MessageRecipient.builder().message(savedMessage)
				.recipientId(messageDTO.getSenderId()).folder("sent").status("read").createdAt(new Date()) // <---
																											// Asignación
																											// manual
				.updatedAt(new Date()) // <--- Asignación manual
				.isDeleted(false) // <--- CRUCIAL para que el emisor lo vea
				.isStarred(false).isArchived(false).build();
		messageRecipientRepository.save(senderRecord);

		messageDTO.setId(savedMessage.getId());
		return messageDTO;
	}

	/**
	 * Obtener mensaje por ID según el usuario que lo consulta
	 */
	@Override
	public MessageDTO getMessageById(Long id, Long userId) {
		Message message = messageRepository.findById(id).orElseThrow(() -> new RuntimeException("Message not found"));

		return mapToDTO(message, userId);
	}

	@Override
	public void markMessageStatus(Long messageId, Long userId, String status) {
		// status expected: "read" or "unread"
		int updated = messageRepository.updateRecipientStatus(messageId, userId, status);
		if (updated == 0) {
			// podrías lanzar excepción opcional o loggear
		}
	}

	/**
	 * Mensajes enviados por un usuario (Outbox)
	 */
	@Override
	public List<MessageDTO> getMessagesBySender(Long senderId) {
		List<Message> messages = messageRepository.findSentBySenderId(senderId);

		return messages.stream().map(msg -> mapToDTO(msg, senderId)) // userId = remitente
				.collect(Collectors.toList());
	}

	/**
	 * Mensajes recibidos por un usuario (Inbox)
	 */
	@Override
	public List<MessageDTO> getInboxMessages(Long userId) {
		List<Message> messages = messageRepository.findInboxByRecipientId(userId);

		return messages.stream().map(msg -> mapToDTO(msg, userId)) // userId = destinatario
				.collect(Collectors.toList());
	}

	/**
	 * Eliminar mensaje (cascade en recipients)
	 */
	@Override
	public void deleteMessage(Long id) {
		messageRepository.deleteById(id);
	}

	// ------------------------------------------------------------
	// Mapear Entity -> DTO
	// ------------------------------------------------------------
	private MessageDTO mapToDTO(Message message, Long contextUserId) {

		MessageDTO dto = new MessageDTO();
		dto.setId(message.getId());
		dto.setSenderId(message.getSenderId());

		// Buscar y mapear nombre del remitente
		User sender = userRepository.findById(message.getSenderId()).orElse(null);
		dto.setSenderName(sender != null ? sender.getFirstName() + " " + sender.getLastName() : "Unknown");

		dto.setSubject(message.getSubject());
		dto.setBody(message.getBody());

		// Convertir fecha
		if (message.getCreatedAt() != null) {
			Date createdAt = message.getCreatedAt();
			dto.setCreatedAt(createdAt);
		}

		// IDs de destinatarios (sin incluir al remitente)
		List<Long> recipientIds = messageRepository.findRecipientIdsByMessageId(message.getId());
		List<Long> realRecipients = recipientIds.stream().filter(id -> !id.equals(message.getSenderId()))
				.collect(Collectors.toList());

		dto.setRecipientIds(realRecipients);

		// --------------------------------------------------------
		// Obtener el MessageRecipient del usuario que consulta
		// --------------------------------------------------------
		MessageRecipient mr = message.getRecipients().stream().filter(r -> r.getRecipientId().equals(contextUserId))
				.findFirst().orElse(null);

		if (mr != null) {
			dto.setFolder(mr.getFolder());
			dto.setStatus(mr.getStatus());
			// NUEVO: Mapear Starred y Archived
			dto.setStarred(mr.getIsStarred());
			dto.setArchived(mr.getIsArchived()); // Mapear campo booleano read en DTO frontend
			dto.setRead("read".equalsIgnoreCase(mr.getStatus()));
		} else {
			// Si no es destinatario (p. ej. el remitente viendo su sent), inferir:
			// buscar registro en message_recipients para este userId (si existe)
			// Por simplicidad, si es remitente, marcar como read (o según tu lógica):
			if (message.getSenderId().equals(contextUserId)) {
				dto.setFolder("sent");
				dto.setStatus("read");
				dto.setRead(true);
			} else {
				dto.setRead(false);
			}
		}

		return dto;
	}

	// En MessageServiceImpl
	@Override
	public MessageDTO updateMessageState(Long messageId, Long userId, MessageDTO messageDTO) {
		// Asegurarse de que el registro del destinatario exista
		messageRecipientRepository.findByMessageIdAndRecipientId(messageId, userId).orElseThrow(
				() -> new RuntimeException("MessageRecipient not found for ID: " + messageId + " and User: " + userId));

		// Determinar el nuevo estado 'status' (read/unread)
		String newStatus = messageDTO.getRead() != null && messageDTO.getRead() ? "read" : "unread";

		// Determinar la nueva carpeta 'folder' (manejo de archivado)
		// Lógica simple: si está archivado, la carpeta es 'archive', sino, respeta la
		// actual del DTO o 'inbox'
		String newFolder = messageDTO.getArchived() != null && messageDTO.getArchived() ? "archive"
				: messageDTO.getFolder() != null ? messageDTO.getFolder() : "inbox";

		// El registro de SENT siempre debería mantenerse como 'sent' si es el remitente
		// Para simplificar, asumimos que solo se actualizan mensajes en INBOX/ARCHIVE
		// (En un sistema real, el remitente también podría "archivar" su copia, lo que
		// necesitaría más lógica)

		int updatedRows = messageRecipientRepository.updateStatusAndFolder(messageId, userId, newStatus,
				messageDTO.getStarred() != null ? messageDTO.getStarred() : false,
				messageDTO.getArchived() != null ? messageDTO.getArchived() : false, newFolder);

		if (updatedRows == 0) {
			throw new RuntimeException("Failed to update message status for message " + messageId);
		}

		// Obtener y mapear el mensaje actualizado para devolver al frontend
		Message updatedMessage = messageRepository.findById(messageId)
				.orElseThrow(() -> new RuntimeException("Message not found after update"));

		return mapToDTO(updatedMessage, userId);
	}

	@Override
	@Transactional
	public void deleteMessageForUser(Long messageId, Long userId) {
		// 1. Marcar el mensaje como borrado solo para este usuario
		MessageRecipient mr = messageRecipientRepository.findByMessageIdAndRecipientId(messageId, userId)
				.orElseThrow(() -> new RuntimeException("Registro no encontrado"));

		mr.setIsDeleted(true);
		messageRecipientRepository.save(mr);

		// 2. Verificar si alguien más todavía tiene el mensaje activo
		// Contamos cuántos destinatarios/remitentes NO lo han borrado aún
		long activeRecipients = messageRecipientRepository.countByMessageIdAndIsDeleted(messageId, false);

		// 3. Si nadie más lo tiene activo, borrado físico total
		if (activeRecipients == 0) {
			messageRepository.deleteById(messageId);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public Page<MessageDTO> getPage(int pageNumber, int pageSize, String filtro, String sortField, String sortDir, Map<String, String> customFilters) {
	    try {
	        String safeSortField = (sortField == null || sortField.isEmpty()) ? "createdAt" : sortField;
	        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(safeSortField).ascending() : Sort.by(safeSortField).descending();
	        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);

	        Specification<Message> spec = (root, query, builder) -> {
	            List<Predicate> predicates = new ArrayList<>();
	            
	            // Join con MessageRecipient para filtrar por el usuario logueado
	            Join<Message, MessageRecipient> recipientsJoin = root.join("recipients", JoinType.INNER);

	            // A. Filtros Obligatorios (Seguridad)
	            Long currentUserId = Long.valueOf(customFilters.get("userId"));
	            predicates.add(builder.equal(recipientsJoin.get("recipientId"), currentUserId));
	            predicates.add(builder.equal(recipientsJoin.get("isDeleted"), false));

	            // B. Filtro de Carpeta (Folder)
	            String folder = customFilters.getOrDefault("folder", "INBOX").toUpperCase();
	            if (!"ALL".equals(folder)) {
	                // ... (mantenemos tu switch de carpetas igual)
	                switch (folder) {
	                    case "SENT" -> predicates.add(builder.equal(builder.lower(recipientsJoin.get("folder")), "sent"));
	                    case "STARRED" -> predicates.add(builder.equal(recipientsJoin.get("isStarred"), true));
	                    case "ARCHIVED" -> predicates.add(builder.equal(recipientsJoin.get("isArchived"), true));
	                    case "UNREAD" -> {
	                        predicates.add(builder.equal(builder.lower(recipientsJoin.get("folder")), "inbox"));
	                        predicates.add(builder.equal(builder.lower(recipientsJoin.get("status")), "unread"));
	                    }
	                    default -> predicates.add(builder.equal(builder.lower(recipientsJoin.get("folder")), "inbox"));
	                }
	            }

	            // D. Búsqueda Global Corregida
	            if (filtro != null && !filtro.trim().isEmpty()) {
	                String pattern = "%" + filtro.toLowerCase() + "%";

	                // 1. Subquery para IDs de usuarios que coinciden con el nombre (Lucia)
	                Subquery<Long> userSubquery = query.subquery(Long.class);
	                Root<User> userRoot = userSubquery.from(User.class);
	                Expression<String> fullName = builder.concat(builder.concat(builder.lower(userRoot.get("firstName")), " "), builder.lower(userRoot.get("lastName")));
	                
	                userSubquery.select(userRoot.get("id")).where(builder.or(
	                    builder.like(builder.lower(userRoot.get("firstName")), pattern),
	                    builder.like(builder.lower(userRoot.get("lastName")), pattern),
	                    builder.like(fullName, pattern)
	                ));

	                // 2. Subquery para encontrar mensajes que fueron enviados A alguien llamado "Lucia"
	                // Esto es vital para que los mensajes en SENT aparezcan
	                Subquery<Long> messageIdsSentToLucia = query.subquery(Long.class);
	                Root<MessageRecipient> mrSub = messageIdsSentToLucia.from(MessageRecipient.class);
	                messageIdsSentToLucia.select(mrSub.get("message").get("id"))
	                    .where(mrSub.get("recipientId").in(userSubquery));

	                // 3. Combinamos: Asunto O Cuerpo O (Soy Sender y lo envié a Lucia) O (Lucia es el Sender)
	                predicates.add(builder.or(
	                    builder.like(builder.lower(root.get("subject")), pattern),
	                    builder.like(builder.lower(root.get("body")), pattern),
	                    root.get("senderId").in(userSubquery),           // El remitente es Lucia (Inbox)
	                    root.get("id").in(messageIdsSentToLucia)         // El mensaje fue enviado A Lucia (Sent)
	                ));
	            }

	            query.distinct(true);
	            return builder.and(predicates.toArray(new Predicate[0]));
	        };

	        Page<Message> page = messageRepository.findAll(spec, pageable);
	        Long userId = Long.valueOf(customFilters.get("userId"));
	        
	        return page.map(m -> this.convertToDTO(m, userId));

	    } catch (Exception e) {
	        throw new RuntimeException("Error en paginación: " + e.getMessage(), e);
	    }
	}
	/**
	 * Convierte la entidad Message a MessageDTO. Nota: Requiere acceso al
	 * UserRepository para resolver los nombres de los IDs.
	 */
	private MessageDTO convertToDTO(Message entity, Long currentUserId) {
	    MessageDTO dto = new MessageDTO();
	    dto.setId(entity.getId());
	    dto.setSubject(entity.getSubject());
	    dto.setBody(entity.getBody());
	    dto.setCreatedAt(entity.getCreatedAt());
	    dto.setSenderId(entity.getSenderId());

	    // CARGA FORZADA DE IDs (Para evitar que la lista llegue vacía al Frontend)
	    if (entity.getRecipients() != null) {
	        // Acceder a la lista aquí dentro del Service asegura que Hibernate la cargue
	        List<Long> ids = entity.getRecipients().stream()
	                .map(MessageRecipient::getRecipientId)
	                .collect(Collectors.toList());
	        dto.setRecipientIds(ids);
	        
	        // Opcional: Imprime en consola para depurar si ves que sigue fallando
	        // System.out.println("Mensaje ID: " + entity.getId() + " - Destinatarios: " + ids.size());
	    }

	    // Lógica de carpeta (para resolver la inconsistencia)
	    if (entity.getSenderId().equals(currentUserId)) {
	        dto.setFolder("sent");
	        dto.setRead(true);
	    } else {
	        entity.getRecipients().stream()
	            .filter(r -> r.getRecipientId().equals(currentUserId))
	            .findFirst()
	            .ifPresent(r -> {
	                dto.setFolder(r.getFolder());
	                dto.setRead("read".equalsIgnoreCase(r.getStatus()));
	            });
	    }
	    return dto;
	}
}
