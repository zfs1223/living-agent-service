package com.livingagent.core.autonomy.context;

import java.util.List;
import java.util.Optional;

public interface DecisionContextBuilder {
    
    DecisionContext build(String message, String userId, String sessionId);
    
    DecisionContext build(String message, String userId, String sessionId, String department);
    
    DecisionContext buildFull(String message, String userId, String sessionId, BuildOptions options);
    
    record BuildOptions(
        boolean includeEmployees,
        boolean includeTools,
        boolean includeKnowledge,
        boolean includeProject,
        boolean includeApproval,
        String targetDepartment,
        List<String> requiredCapabilities,
        int maxEmployees,
        int maxKnowledge
    ) {
        public static BuildOptions defaults() {
            return new BuildOptions(true, true, false, false, false, null, List.of(), 10, 5);
        }
        
        public static BuildOptions minimal() {
            return new BuildOptions(false, false, false, false, false, null, List.of(), 0, 0);
        }
        
        public static BuildOptions forEmployeeDispatch() {
            return new BuildOptions(true, true, true, false, false, null, List.of(), 20, 3);
        }
        
        public static BuildOptions forTaskPlanning() {
            return new BuildOptions(true, true, true, true, true, null, List.of(), 15, 10);
        }
    }
}
