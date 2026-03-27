package com.taskimed.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MessageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @EqualsAndHashCode.Include
    private Long id;

    private Long senderId;
    private String senderName;

    private String subject;
    private String body;

    private Date createdAt;
    private Date updatedAt;

    private Boolean read;
    private Boolean starred;
    private Boolean archived;

    private String folder;
    private String status;

    private List<Long> recipientIds;
    
    /**
     * Campo vital para el buscador global (globalFilter).
     * El backend lo envía concatenado para que la búsqueda sea eficiente.
     */
    private String recipientNames;

    // ==================================================
    //         Formato de Fechas para el Frontend
    // ==================================================
    
    @JsonIgnore
    public String getFormattedCreatedAt() {
        if (createdAt == null) return "";
        // Usamos el formato solicitado en el XHTML
        return new SimpleDateFormat("MM/dd/yyyy").format(createdAt);
    }

    @JsonIgnore
    public String getFormattedUpdatedAt() {
        if (updatedAt == null) return "";
        return new SimpleDateFormat("MM/dd/yyyy HH:mm").format(updatedAt);
    }

    // ==================================================
    //              Métodos Utilitarios (Vista)
    // ==================================================

    @JsonIgnore
    public boolean isUnread() {
        return Boolean.FALSE.equals(read);
    }

    /**
     * Permite a PrimeFaces manejar la selección múltiple de IDs en el diálogo de "Nuevo Mensaje"
     */
    @JsonIgnore
    public String getRecipientIdsString() {
        if (recipientIds == null || recipientIds.isEmpty()) return "";
        return recipientIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    // ==================================================
    //           Lógica de Interfaz de Usuario
    // ==================================================

    @JsonIgnore
    public void toggleStar() {
        this.starred = (this.starred == null) ? true : !this.starred;
    }

    @JsonIgnore
    public void toggleArchived() {
        this.archived = (this.archived == null) ? true : !this.archived;
    }

    @JsonIgnore
    public void markAsRead() {
        this.read = true;
        this.status = "read";
    }

    @JsonIgnore
    public void markAsUnread() {
        this.read = false;
        this.status = "unread";
    }
}