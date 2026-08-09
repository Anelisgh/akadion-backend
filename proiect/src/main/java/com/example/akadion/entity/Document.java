package com.example.akadion.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "documente", indexes = {
        @Index(name = "idx_documente_saptamana", columnList = "id_saptamana")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Document extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_saptamana", nullable = false)
    private Saptamana saptamana;

    @Column(nullable = false, length = 255)
    private String titlu;

    @Column(name = "path_minio", nullable = false, length = 512)
    private String pathMinio;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_index", nullable = false, length = 20)
    private DocumentStatusIndex statusIndex;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activ = true;
}
