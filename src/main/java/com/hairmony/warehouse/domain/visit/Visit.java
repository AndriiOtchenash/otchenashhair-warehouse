package com.hairmony.warehouse.domain.visit;

import com.hairmony.warehouse.domain.appointment.Appointment;
import com.hairmony.warehouse.domain.client.Client;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "visits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Visit {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "visits_seq")
    @SequenceGenerator(name = "visits_seq", sequenceName = "visits_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Column(name = "complaint", columnDefinition = "TEXT")
    private String complaint;

    @Column(name = "scalp_condition", columnDefinition = "TEXT")
    private String scalpCondition;

    @Column(name = "recommendations", columnDefinition = "TEXT")
    private String recommendations;

    @Column(name = "next_visit_date")
    private LocalDate nextVisitDate;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "next_appointment_id")
    private Appointment nextAppointment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
