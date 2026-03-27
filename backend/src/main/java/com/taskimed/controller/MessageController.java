package com.taskimed.controller;

import com.taskimed.dto.MessageDTO;
import com.taskimed.service.MessageService;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    /**
     * Enviar un mensaje
     */
    @PostMapping
    public ResponseEntity<MessageDTO> sendMessage(@RequestBody MessageDTO messageDTO) {
        MessageDTO sentMessage = messageService.sendMessage(messageDTO);
        return ResponseEntity.ok(sentMessage);
    }

    /**
     * Obtener mensaje por ID
     */
    @GetMapping("/{id}/{userId}")
    public ResponseEntity<MessageDTO> getMessage(@PathVariable Long id, @PathVariable Long userId) {
        MessageDTO message = messageService.getMessageById(id, userId);
        return ResponseEntity.ok(message);
    }

    /**
     * Obtener todos los mensajes enviados por un usuario (Outbox)
     */
    @GetMapping("/sent/{userId}")
    public ResponseEntity<List<MessageDTO>> getSentMessages(@PathVariable Long userId) {
        List<MessageDTO> sentMessages = messageService.getMessagesBySender(userId);
        return ResponseEntity.ok(sentMessages);
    }

    /**
     * Obtener todos los mensajes recibidos por un usuario (Inbox)
     */
    @GetMapping("/inbox/{userId}")
    public ResponseEntity<List<MessageDTO>> getInboxMessages(@PathVariable Long userId) {
        List<MessageDTO> inboxMessages = messageService.getInboxMessages(userId);
        return ResponseEntity.ok(inboxMessages);
    }

    /**
     * Eliminar un mensaje (también se puede manejar borrado lógico por usuario)
     */
    @DeleteMapping("/{id}/{userId}")
    public ResponseEntity<Void> deleteMessage(@PathVariable Long id, @PathVariable Long userId) {
        // Ahora pasamos ambos parámetros al servicio
        messageService.deleteMessageForUser(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/mark-read/{userId}")
    public ResponseEntity<Void> markAsRead(@PathVariable("id") Long id, @PathVariable("userId") Long userId) {
        messageService.markMessageStatus(id, userId, "read");
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/mark-unread/{userId}")
    public ResponseEntity<Void> markAsUnread(@PathVariable("id") Long id, @PathVariable("userId") Long userId) {
        messageService.markMessageStatus(id, userId, "unread");
        return ResponseEntity.noContent().build();
    }

    /**
     * Actualizar estado del mensaje (Read/Unread, Starred, Archived)
     */
    @PutMapping("/{id}/{userId}")
    public ResponseEntity<MessageDTO> updateMessageState(@PathVariable Long id, 
                                                         @PathVariable Long userId,
                                                         @RequestBody MessageDTO messageDTO) {
        MessageDTO updatedMessage = messageService.updateMessageState(id, userId, messageDTO);
        return ResponseEntity.ok(updatedMessage);
    }

    @GetMapping("/paginated")
    public ResponseEntity<Map<String, Object>> getPage(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "filter", required = false) String filter,
            @RequestParam(name = "sort", required = false, defaultValue = "createdAt,desc") String sort,
            @RequestParam Map<String, String> allParams
    ) {
        try {
            // 1. Sincronizar Número de Página y Tamaño (Prioridad a lo que viene en el mapa)
            // Revisamos "pageNumber" porque es lo que detectamos en tu Debug
            if (allParams.containsKey("pageNumber")) {
                page = Integer.parseInt(allParams.get("pageNumber"));
            } else if (allParams.containsKey("page")) {
                page = Integer.parseInt(allParams.get("page"));
            }

            if (allParams.containsKey("pageSize")) {
                size = Integer.parseInt(allParams.get("pageSize"));
            } else if (allParams.containsKey("size")) {
                size = Integer.parseInt(allParams.get("size"));
            }

            // 2. Procesar el ordenamiento
            String sortField = "createdAt";
            String sortDir = "desc";
            if (sort != null && sort.contains(",")) {
                String[] parts = sort.split(",");
                sortField = parts[0];
                sortDir = parts[1];
            } else if (allParams.containsKey("sortField")) {
                sortField = allParams.get("sortField");
                sortDir = allParams.getOrDefault("sortDir", "desc");
            }

            // 3. Sincronizar el filtro global
            if (allParams.containsKey("globalFilter")) {
                filter = allParams.get("globalFilter");
            } else if (allParams.containsKey("filter")) {
                filter = allParams.get("filter");
            }

            // 4. LIMPIEZA TOTAL del mapa antes de enviarlo al Service
            // Quitamos los parámetros de control para que solo queden los de negocio (userId, folder, etc.)
            allParams.remove("page");
            allParams.remove("size");
            allParams.remove("pageNumber");
            allParams.remove("pageSize");
            allParams.remove("filter");
            allParams.remove("globalFilter");
            allParams.remove("sort");
            allParams.remove("sortField");
            allParams.remove("sortDir");

            // 5. Llamada al servicio con los valores sincronizados
            Page<MessageDTO> resultPage = messageService.getPage(
                    page,
                    size,
                    filter,
                    sortField,
                    sortDir,
                    allParams
            );

            // 6. Respuesta para EntityLazyBean
            Map<String, Object> response = new HashMap<>();
            response.put("data", resultPage.getContent());
            response.put("total", resultPage.getTotalElements());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace(); // Importante para ver errores de parseo en consola
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
