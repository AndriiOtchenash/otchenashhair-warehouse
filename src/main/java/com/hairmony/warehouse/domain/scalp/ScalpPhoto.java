package com.hairmony.warehouse.domain.scalp;

import com.hairmony.warehouse.domain.client.Client;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "scalp_photos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScalpPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "scalp_photos_seq")
    @SequenceGenerator(name = "scalp_photos_seq", sequenceName = "scalp_photos_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "drive_file_id", length = 100)
    private String driveFileId;

    @Column(name = "drive_url", nullable = false, length = 500)
    private String driveUrl;

    @Column(name = "taken_at", nullable = false)
    private LocalDate takenAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone", nullable = false, length = 30)
    private ScalpZone zone;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
