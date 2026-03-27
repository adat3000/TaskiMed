package com.taskimed.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.Date;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

@Entity
@Table(name = "message_recipients")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Builder.Default
    @Column(name = "folder", nullable = false)
    private String folder = "inbox";

    @Builder.Default
    @Column(name = "status", nullable = false)
    private String status = "unread";

    // ----------------------------------------------------
    //  NUEVOS CAMPOS (Star / Archive)
    // ----------------------------------------------------
    @Builder.Default
    @Column(name = "is_starred", nullable = false)
    private Boolean isStarred = false;

    @Builder.Default
    @Column(name = "is_archived", nullable = false)
    private Boolean isArchived = false;
    // ----------------------------------------------------

    @Column(name = "created_at", updatable = false)
    @CreatedDate
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    @Column(name = "updated_at")
    @LastModifiedDate
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;

    @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}