package com.livingagent.core.operation.performance;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface PerformanceAssessment {

    String getAssessmentId();
    
    String getEmployeeId();
    
    String getEmployeeName();
    
    AssessmentPeriod getPeriod();
    
    double getOverallScore();
    
    Map<String, Double> getDimensionScores();
    
    List<PerformanceIndicator> getIndicators();
    
    String getGrade();
    
    String getComment();
    
    Instant getAssessedAt();
    
    enum AssessmentPeriod {
        DAILY,
        WEEKLY,
        MONTHLY,
        QUARTERLY,
        YEARLY
    }
    
    enum PerformanceGrade {
        EXCEPTIONAL("S", 95, 100),
        EXCELLENT("A", 85, 94),
        GOOD("B", 70, 84),
        SATISFACTORY("C", 60, 69),
        NEEDS_IMPROVEMENT("D", 40, 59),
        UNSATISFACTORY("F", 0, 39);
        
        private final String code;
        private final int minScore;
        private final int maxScore;
        
        PerformanceGrade(String code, int minScore, int maxScore) {
            this.code = code;
            this.minScore = minScore;
            this.maxScore = maxScore;
        }
        
        public String getCode() { return code; }
        
        public static PerformanceGrade fromScore(double score) {
            for (PerformanceGrade grade : values()) {
                if (score >= grade.minScore && score <= grade.maxScore) {
                    return grade;
                }
            }
            return UNSATISFACTORY;
        }
    }
}
