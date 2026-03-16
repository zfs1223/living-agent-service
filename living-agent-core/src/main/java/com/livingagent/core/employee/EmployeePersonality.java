package com.livingagent.core.employee;

import com.livingagent.core.evolution.personality.BrainPersonality;

import java.time.Instant;

public record EmployeePersonality(
    double rigor,
    double creativity,
    double riskTolerance,
    double obedience,
    EmployeePersonality.PersonalitySource source,
    Instant updatedAt
) {
    public enum PersonalitySource {
        TEMPLATE,
        INFERRED,
        DEPARTMENT,
        MANUAL
    }

    public EmployeePersonality {
        rigor = clamp(rigor);
        creativity = clamp(creativity);
        riskTolerance = clamp(riskTolerance);
        obedience = clamp(obedience);
        if (source == null) source = PersonalitySource.TEMPLATE;
        if (updatedAt == null) updatedAt = Instant.now();
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public static EmployeePersonality of(double rigor, double creativity, 
            double riskTolerance, double obedience) {
        return new EmployeePersonality(rigor, creativity, riskTolerance, 
            obedience, PersonalitySource.TEMPLATE, Instant.now());
    }

    public static EmployeePersonality of(double rigor, double creativity, 
            double riskTolerance, double obedience, PersonalitySource source) {
        return new EmployeePersonality(rigor, creativity, riskTolerance, 
            obedience, source, Instant.now());
    }

    public static EmployeePersonality defaultForDepartment(String department) {
        return switch (department.toLowerCase()) {
            case "tech" -> of(0.8, 0.6, 0.5, 0.7, PersonalitySource.DEPARTMENT);
            case "admin" -> of(0.7, 0.4, 0.3, 0.9, PersonalitySource.DEPARTMENT);
            case "sales" -> of(0.5, 0.7, 0.6, 0.6, PersonalitySource.DEPARTMENT);
            case "hr" -> of(0.6, 0.5, 0.4, 0.8, PersonalitySource.DEPARTMENT);
            case "finance" -> of(0.9, 0.3, 0.2, 0.95, PersonalitySource.DEPARTMENT);
            case "cs" -> of(0.6, 0.5, 0.4, 0.7, PersonalitySource.DEPARTMENT);
            case "legal" -> of(0.95, 0.2, 0.1, 0.98, PersonalitySource.DEPARTMENT);
            case "ops" -> of(0.7, 0.5, 0.5, 0.7, PersonalitySource.DEPARTMENT);
            default -> of(0.7, 0.5, 0.4, 0.85, PersonalitySource.DEPARTMENT);
        };
    }

    public EmployeePersonality withRigor(double rigor) {
        return new EmployeePersonality(rigor, creativity, riskTolerance, 
            obedience, source, Instant.now());
    }

    public EmployeePersonality withCreativity(double creativity) {
        return new EmployeePersonality(rigor, creativity, riskTolerance, 
            obedience, source, Instant.now());
    }

    public EmployeePersonality withRiskTolerance(double riskTolerance) {
        return new EmployeePersonality(rigor, creativity, riskTolerance, 
            obedience, source, Instant.now());
    }

    public EmployeePersonality withObedience(double obedience) {
        return new EmployeePersonality(rigor, creativity, riskTolerance, 
            obedience, source, Instant.now());
    }

    public EmployeePersonality withSource(PersonalitySource source) {
        return new EmployeePersonality(rigor, creativity, riskTolerance, 
            obedience, source, Instant.now());
    }

    public double getAverage() {
        return (rigor + creativity + riskTolerance + obedience) / 4.0;
    }

    public boolean isConservative() {
        return rigor > 0.8 && riskTolerance < 0.3;
    }

    public boolean isInnovative() {
        return creativity > 0.7 && riskTolerance > 0.5;
    }

    public boolean isCompliant() {
        return obedience > 0.8;
    }

    public static EmployeePersonality fromBrainPersonality(BrainPersonality brain) {
        if (brain == null) {
            return of(0.7, 0.5, 0.4, 0.85, PersonalitySource.DEPARTMENT);
        }
        return new EmployeePersonality(
            brain.getRigor(),
            brain.getCreativity(),
            brain.getRiskTolerance(),
            brain.getObedience(),
            PersonalitySource.DEPARTMENT,
            Instant.now()
        );
    }

    public static EmployeePersonality fromBrainPersonality(BrainPersonality brain, PersonalitySource source) {
        if (brain == null) {
            return of(0.7, 0.5, 0.4, 0.85, source);
        }
        return new EmployeePersonality(
            brain.getRigor(),
            brain.getCreativity(),
            brain.getRiskTolerance(),
            brain.getObedience(),
            source,
            Instant.now()
        );
    }
}
