package com.livingagent.core.model.pool;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class BrainModelAssigner {

    private final BrainModelAssignmentRepository assignmentRepo;
    private final ModelPoolManager modelPoolManager;

    public BrainModelAssigner(BrainModelAssignmentRepository assignmentRepo, ModelPoolManager modelPoolManager) {
        this.assignmentRepo = assignmentRepo;
        this.modelPoolManager = modelPoolManager;
    }

    @Transactional
    public BrainModelAssignment assignModel(String brainId, String brainName,
                                            String brainType, java.util.UUID modelId, String assignedBy) {
        LlmModel model = modelPoolManager.getModelById(modelId);
        if (model == null) {
            throw new IllegalArgumentException("模型不存在: " + modelId);
        }

        BrainModelAssignment assignment = assignmentRepo
            .findByBrainId(brainId)
            .orElse(new BrainModelAssignment());

        assignment.setBrainId(brainId);
        assignment.setBrainName(brainName);
        assignment.setBrainType(brainType);
        assignment.setModelId(modelId);
        assignment.setAssignedBy(assignedBy);
        assignment.setAssignedAt(LocalDateTime.now());
        assignment.setUpdatedAt(LocalDateTime.now());

        return assignmentRepo.save(assignment);
    }

    public LlmModel getModelForBrain(String brainId) {
        return assignmentRepo.findByBrainId(brainId)
            .flatMap(a -> {
                LlmModel model = modelPoolManager.getModelById(a.getModelId());
                return model != null ? java.util.Optional.of(model) : java.util.Optional.empty();
            })
            .orElse(getDefaultModelForBrain(brainId));
    }

    public BrainModelAssignment getAssignment(String brainId) {
        return assignmentRepo.findByBrainId(brainId).orElse(null);
    }

    public List<BrainModelAssignment> getAllAssignments() {
        return assignmentRepo.findAll();
    }

    @Transactional
    public void clearAssignment(String brainId) {
        assignmentRepo.findByBrainId(brainId).ifPresent(assignmentRepo::delete);
    }

    private LlmModel getDefaultModelForBrain(String brainId) {
        List<LlmModel> allModels = modelPoolManager.getAllModels();
        if (allModels.isEmpty()) {
            return null;
        }

        return allModels.stream()
            .filter(LlmModel::isRecommended)
            .findFirst()
            .orElse(allModels.get(0));
    }
}
