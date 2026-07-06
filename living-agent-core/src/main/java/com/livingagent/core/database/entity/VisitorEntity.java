package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "visitors")
public class VisitorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "visitor_id", nullable = false, unique = true, length = 64)
    private String visitorId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String purpose;

    @Column(length = 100)
    private String contact;

    @Column(name = "host_employee_id", length = 100)
    private String hostEmployeeId;

    @Column(nullable = false)
    private Instant checkInTime;

    @Column(name = "check_out_time")
    private Instant checkOutTime;

    @Column(nullable = false, length = 32)
    private String status;

    protected VisitorEntity() {}

    public VisitorEntity(String visitorId, String name, String purpose, String contact,
                         String hostEmployeeId, Instant checkInTime, Instant checkOutTime, String status) {
        this.visitorId = visitorId;
        this.name = name;
        this.purpose = purpose;
        this.contact = contact;
        this.hostEmployeeId = hostEmployeeId;
        this.checkInTime = checkInTime;
        this.checkOutTime = checkOutTime;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getVisitorId() { return visitorId; }
    public String getName() { return name; }
    public String getPurpose() { return purpose; }
    public String getContact() { return contact; }
    public String getHostEmployeeId() { return hostEmployeeId; }
    public Instant getCheckInTime() { return checkInTime; }
    public Instant getCheckOutTime() { return checkOutTime; }
    public String getStatus() { return status; }
    public void setCheckOutTime(Instant checkOutTime) { this.checkOutTime = checkOutTime; }
    public void setStatus(String status) { this.status = status; }
}
