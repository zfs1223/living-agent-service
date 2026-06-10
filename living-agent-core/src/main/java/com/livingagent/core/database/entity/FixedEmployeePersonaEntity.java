package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "fixed_employee_persona")
public class FixedEmployeePersonaEntity {
    @Id
    @Column(name = "code", length = 16)
    private String code;

    @Column(name = "employee_id", length = 36, unique = true)
    private String employeeId;

    @Column(name = "icon", length = 32)
    private String icon;

    @Column(name = "hair", length = 32)
    private String hair;

    @Column(name = "glasses")
    private boolean glasses;

    @Column(name = "badge_style", length = 32)
    private String badgeStyle;

    @Column(name = "stance", length = 32)
    private String stance;

    @Column(name = "outfit", length = 32)
    private String outfit;

    @Column(name = "accent_color", length = 32)
    private String accentColor;

    @Column(name = "face", length = 32)
    private String face;

    @Column(name = "skin_tone", length = 32)
    private String skinTone;

    @Column(name = "body_shape", length = 32)
    private String bodyShape;

    @Column(name = "clothing_variant", length = 32)
    private String clothingVariant;

    @Column(name = "accessory_variant", length = 32)
    private String accessoryVariant;

    @Column(name = "badge_label", length = 100)
    private String badgeLabel;

    @Column(name = "avatar_style", columnDefinition = "jsonb")
    private String avatarStyle;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public FixedEmployeePersonaEntity() {
        this.icon = "🤖";
        this.hair = "short";
        this.badgeStyle = "classic";
        this.stance = "focused";
        this.outfit = "default";
        this.accentColor = "#58a6ff";
        this.face = "neutral";
        this.skinTone = "#f5d0b1";
        this.bodyShape = "default";
        this.clothingVariant = "standard";
        this.accessoryVariant = "none";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public String getHair() { return hair; }
    public void setHair(String hair) { this.hair = hair; }
    public boolean isGlasses() { return glasses; }
    public void setGlasses(boolean glasses) { this.glasses = glasses; }
    public String getBadgeStyle() { return badgeStyle; }
    public void setBadgeStyle(String badgeStyle) { this.badgeStyle = badgeStyle; }
    public String getStance() { return stance; }
    public void setStance(String stance) { this.stance = stance; }
    public String getOutfit() { return outfit; }
    public void setOutfit(String outfit) { this.outfit = outfit; }
    public String getAccentColor() { return accentColor; }
    public void setAccentColor(String accentColor) { this.accentColor = accentColor; }
    public String getFace() { return face; }
    public void setFace(String face) { this.face = face; }
    public String getSkinTone() { return skinTone; }
    public void setSkinTone(String skinTone) { this.skinTone = skinTone; }
    public String getBodyShape() { return bodyShape; }
    public void setBodyShape(String bodyShape) { this.bodyShape = bodyShape; }
    public String getClothingVariant() { return clothingVariant; }
    public void setClothingVariant(String clothingVariant) { this.clothingVariant = clothingVariant; }
    public String getAccessoryVariant() { return accessoryVariant; }
    public void setAccessoryVariant(String accessoryVariant) { this.accessoryVariant = accessoryVariant; }
    public String getBadgeLabel() { return badgeLabel; }
    public void setBadgeLabel(String badgeLabel) { this.badgeLabel = badgeLabel; }
    public String getAvatarStyle() { return avatarStyle; }
    public void setAvatarStyle(String avatarStyle) { this.avatarStyle = avatarStyle; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
