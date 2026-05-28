package com.hairmony.warehouse.domain.appointment;

import com.hairmony.warehouse.domain.client.Client;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "appointments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "appointments_seq")
    @SequenceGenerator(name = "appointments_seq", sequenceName = "appointments_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AppointmentStatus status;

    @Column(name = "service_id")
    private Long serviceId;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "reminder_48h_sent_at")
    private LocalDateTime reminder48hSentAt;

    @Column(name = "reminder_24h_sent_at")
    private LocalDateTime reminder24hSentAt;

    @Column(name = "reminder_2h_sent_at")
    private LocalDateTime reminder2hSentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) this.status = AppointmentStatus.PLANNED;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
