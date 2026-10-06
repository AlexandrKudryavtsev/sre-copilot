package com.srecopilot.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, length = 2048)
    private String endpointUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EndpointType endpointType;

    @Column(nullable = false)
    private boolean monitoringEnabled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ConnectionStatus connectionStatus;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Project() {
    }

    public Project(long userId, String name, String description, String endpointUrl, EndpointType endpointType) {
        this.userId = userId;
        this.name = name;
        this.description = description;
        this.endpointUrl = endpointUrl;
        this.endpointType = endpointType;
        this.monitoringEnabled = false;
        this.connectionStatus = ConnectionStatus.UNKNOWN;
    }

    @PrePersist
    void initializeTimestamps() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public EndpointType getEndpointType() {
        return endpointType;
    }

    public boolean isMonitoringEnabled() {
        return monitoringEnabled;
    }

    public ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
