package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "artifact_records")
public class ArtifactRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "artifact_id", nullable = false, unique = true, length = 100)
    private String artifactId;

    @Column(name = "execution_id", nullable = false, length = 500)
    private String executionId;

    @Column(name = "department", length = 50)
    private String department;

    @Column(name = "owner_employee_code", length = 100)
    private String ownerEmployeeCode;

    @Column(name = "owner_employee_neuron_id", length = 200)
    private String ownerEmployeeNeuronId;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "path", nullable = false, length = 500)
    private String path;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "sha256", length = 64)
    private String sha256;

    @Column(name = "task_id", length = 100)
    private String taskId;

    @Column(name = "project_id", length = 100)
    private String projectId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "JSONB")
    private String metadataJson;

    /* ============ 访问权限字段（HERMES_COMPARISON_AND_BORROWING_PLAN.md §6.18）============ */

    /**
     * 可见性：PRIVATE/DEPARTMENT/PUBLIC/RESTRICTED
     * 详细规则参考 §6.18.1
     */
    @Column(name = "visibility", nullable = false, length = 20)
    private String visibility = "DEPARTMENT";

    /**
     * 创建者 userId（Human/Digital 员工对应 userId）
     */
    @Column(name = "created_by", length = 100)
    private String createdBy;

    /**
     * 参与者 userId 列表（逗号分隔）
     */
    @Column(name = "participant_ids", columnDefinition = "TEXT")
    private String participantIds;

    /**
     * 额外可查看的部门列表（逗号分隔，仅 RESTRICTED 有效）
     */
    @Column(name = "viewer_departments", columnDefinition = "TEXT")
    private String viewerDepartments;

    /**
     * 部门领导是否可见（默认 TRUE）
     */
    @Column(name = "visible_to_leader", nullable = false)
    private Boolean visibleToLeader = Boolean.TRUE;

    public ArtifactRecordEntity() {
        this.createdAt = Instant.now();
    }

    public ArtifactRecordEntity(String artifactId, String executionId, String department,
                               String ownerEmployeeCode, String ownerEmployeeNeuronId,
                               String type, String path, String name, String summary,
                               Long sizeBytes, String sha256, String metadataJson) {
        this.artifactId = artifactId;
        this.executionId = executionId;
        this.department = department;
        this.ownerEmployeeCode = ownerEmployeeCode;
        this.ownerEmployeeNeuronId = ownerEmployeeNeuronId;
        this.type = type;
        this.path = path;
        this.name = name;
        this.summary = summary;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.createdAt = Instant.now();
        this.metadataJson = metadataJson;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getArtifactId() { return artifactId; }
    public void setArtifactId(String artifactId) { this.artifactId = artifactId; }

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getOwnerEmployeeCode() { return ownerEmployeeCode; }
    public void setOwnerEmployeeCode(String ownerEmployeeCode) { this.ownerEmployeeCode = ownerEmployeeCode; }

    public String getOwnerEmployeeNeuronId() { return ownerEmployeeNeuronId; }
    public void setOwnerEmployeeNeuronId(String ownerEmployeeNeuronId) { this.ownerEmployeeNeuronId = ownerEmployeeNeuronId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }

    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }

    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getParticipantIds() { return participantIds; }
    public void setParticipantIds(String participantIds) { this.participantIds = participantIds; }

    public String getViewerDepartments() { return viewerDepartments; }
    public void setViewerDepartments(String viewerDepartments) { this.viewerDepartments = viewerDepartments; }

    public Boolean getVisibleToLeader() { return visibleToLeader; }
    public void setVisibleToLeader(Boolean visibleToLeader) { this.visibleToLeader = visibleToLeader; }
}
