package br.com.schf.setup;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "instance_setup")
public class InstanceSetup {

    @Id
    private UUID id;

    @Column(nullable = false)
    private boolean completed;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected InstanceSetup() {
    }

    public InstanceSetup(UUID id) {
        this.id = id;
        this.completed = false;
        this.createdAt = OffsetDateTime.now();
    }

    public void complete(UUID organizationId) {
        this.completed = true;
        this.organizationId = organizationId;
        this.completedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public boolean isCompleted() {
        return completed;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }
}
