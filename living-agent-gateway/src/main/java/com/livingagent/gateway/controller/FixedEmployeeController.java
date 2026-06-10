package com.livingagent.gateway.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.database.entity.FixedEmployeePersonaEntity;
import com.livingagent.core.database.entity.FixedEmployeeProfileEntity;
import com.livingagent.core.database.repository.FixedEmployeePersonaRepository;
import com.livingagent.core.database.repository.FixedEmployeeProfileRepository;
import com.livingagent.core.employee.registry.FixedEmployeeRegistry;
import com.livingagent.core.security.AccessGateService;
import com.livingagent.gateway.controller.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class FixedEmployeeController {

    private final FixedEmployeeRegistry fixedEmployeeRegistry;
    private final AccessGateService accessGateService;
    private final FixedEmployeeProfileRepository fixedEmployeeProfileRepository;
    private final FixedEmployeePersonaRepository fixedEmployeePersonaRepository;
    private final ObjectMapper objectMapper;

    public FixedEmployeeController(
            FixedEmployeeRegistry fixedEmployeeRegistry,
            AccessGateService accessGateService,
            FixedEmployeeProfileRepository fixedEmployeeProfileRepository,
            FixedEmployeePersonaRepository fixedEmployeePersonaRepository,
            ObjectMapper objectMapper) {
        this.fixedEmployeeRegistry = fixedEmployeeRegistry;
        this.accessGateService = accessGateService;
        this.fixedEmployeeProfileRepository = fixedEmployeeProfileRepository;
        this.fixedEmployeePersonaRepository = fixedEmployeePersonaRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/fixed-employees/summary")
    public ResponseEntity<ApiResponse<FixedEmployeeSummaryDto>> getSummary(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId == null || employeeId.isBlank() || !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        FixedEmployeeRegistry.FixedEmployeeSummary summary = fixedEmployeeRegistry.getSummary();
        FixedEmployeeSummaryDto dto = new FixedEmployeeSummaryDto(
            summary.totalDefinitions(),
            summary.activeEmployees(),
            summary.departmentCount(),
            summary.countByDepartment()
        );
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @GetMapping("/fixed-employees/definitions")
    public ResponseEntity<ApiResponse<List<FixedEmployeeDefinitionDto>>> getAllDefinitions(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        List<FixedEmployeeDefinitionDto> definitions = fixedEmployeeRegistry.getAllDefinitions().stream()
            .map(this::toDto)
            .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(definitions));
    }

    @GetMapping("/fixed-employees/definitions/{code}")
    public ResponseEntity<ApiResponse<FixedEmployeeDefinitionDto>> getDefinition(
            @PathVariable String code,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        return fixedEmployeeRegistry.getDefinitionByCode(code)
            .map(def -> ResponseEntity.ok(ApiResponse.ok(toDto(def))))
            .orElse(ResponseEntity.status(404)
                .body(ApiResponse.err("not_found", "Fixed employee definition not found: " + code)));
    }

    @GetMapping("/fixed-employees/definitions/by-department/{department}")
    public ResponseEntity<ApiResponse<List<FixedEmployeeDefinitionDto>>> getDefinitionsByDepartment(
            @PathVariable String department,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        List<FixedEmployeeDefinitionDto> definitions = fixedEmployeeRegistry.getDefinitionsByDepartment(department).stream()
            .map(this::toDto)
            .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(definitions));
    }

    @GetMapping("/fixed-employees/grouped")
    public ResponseEntity<ApiResponse<Map<String, List<FixedEmployeeDefinitionDto>>>> getGroupedDefinitions(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        Map<String, List<FixedEmployeeDefinitionDto>> grouped = fixedEmployeeRegistry.getDefinitionsGroupedByDepartment().entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().stream().map(this::toDto).collect(Collectors.toList())
            ));
        return ResponseEntity.ok(ApiResponse.ok(grouped));
    }

    @GetMapping("/fixed-employees/profiles")
    public ResponseEntity<ApiResponse<List<FixedEmployeeProfileDto>>> getProfiles() {
        List<FixedEmployeeProfileDto> profiles = fixedEmployeeProfileRepository.findAll().stream()
            .map(this::toProfileDto)
            .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(profiles));
    }

    @GetMapping("/fixed-employees/profiles/{code}")
    public ResponseEntity<ApiResponse<FixedEmployeeProfileDto>> getProfile(@PathVariable String code) {
        return fixedEmployeeProfileRepository.findById(code.toUpperCase())
            .map(profile -> ResponseEntity.ok(ApiResponse.ok(toProfileDto(profile))))
            .orElse(ResponseEntity.status(404).body(ApiResponse.err("not_found", "Fixed employee profile not found: " + code)));
    }

    @GetMapping("/fixed-employees/personas")
    public ResponseEntity<ApiResponse<List<FixedEmployeePersonaDto>>> getPersonas() {
        List<FixedEmployeePersonaDto> personas = fixedEmployeePersonaRepository.findAll().stream()
            .map(this::toPersonaDto)
            .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(personas));
    }

    @GetMapping("/fixed-employees/personas/{code}")
    public ResponseEntity<ApiResponse<FixedEmployeePersonaDto>> getPersona(@PathVariable String code) {
        return fixedEmployeePersonaRepository.findById(code.toUpperCase())
            .map(persona -> ResponseEntity.ok(ApiResponse.ok(toPersonaDto(persona))))
            .orElse(ResponseEntity.status(404).body(ApiResponse.err("not_found", "Fixed employee persona not found: " + code)));
    }

    private FixedEmployeeDefinitionDto toDto(FixedEmployeeRegistry.FixedEmployeeDefinition def) {
        return new FixedEmployeeDefinitionDto(
            def.code(),
            def.name(),
            def.title(),
            def.department(),
            def.departmentName(),
            def.neuronId(),
            def.roles(),
            def.capabilities(),
            def.tools(),
            def.channel(),
            new PersonalityDto(
                def.personality().rigor(),
                def.personality().creativity(),
                def.personality().riskTolerance(),
                def.personality().obedience()
            ),
            def.icon(),
            def.requiredSkills()
        );
    }

    public record FixedEmployeeSummaryDto(
        int totalDefinitions,
        int activeEmployees,
        int departmentCount,
        Map<String, Integer> countByDepartment
    ) {}

    private FixedEmployeeProfileDto toProfileDto(FixedEmployeeProfileEntity profile) {
        return new FixedEmployeeProfileDto(
            profile.getCode(),
            profile.getEmployeeId(),
            profile.getDisplayNameZh(),
            profile.getDisplayNameEn(),
            profile.getSummaryZh(),
            profile.getSummaryEn(),
            parseJsonList(profile.getTraits()),
            parseJsonList(profile.getToolTags()),
            profile.getCurrentTask(),
            profile.getStatus(),
            Optional.ofNullable(profile.getLastActiveAt()).map(Object::toString).orElse(null)
        );
    }

    private FixedEmployeePersonaDto toPersonaDto(FixedEmployeePersonaEntity persona) {
        return new FixedEmployeePersonaDto(
            persona.getCode(),
            persona.getEmployeeId(),
            persona.getIcon(),
            persona.getHair(),
            persona.isGlasses(),
            persona.getBadgeStyle(),
            persona.getStance(),
            persona.getOutfit(),
            persona.getAccentColor(),
            persona.getFace(),
            persona.getSkinTone(),
            persona.getBodyShape(),
            persona.getClothingVariant(),
            persona.getAccessoryVariant(),
            persona.getBadgeLabel(),
            parseJsonMap(persona.getAvatarStyle())
        );
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    public record FixedEmployeeDefinitionDto(
        String code,
        String name,
        String title,
        String department,
        String departmentName,
        String neuronId,
        List<String> roles,
        List<String> capabilities,
        List<String> tools,
        String channel,
        PersonalityDto personality,
        String icon,
        List<String> requiredSkills
    ) {}

    public record FixedEmployeeProfileDto(
        String code,
        String employeeId,
        String displayNameZh,
        String displayNameEn,
        String summaryZh,
        String summaryEn,
        List<String> traits,
        List<String> toolTags,
        String currentTask,
        String status,
        String lastActiveAt
    ) {}

    public record FixedEmployeePersonaDto(
        String code,
        String employeeId,
        String icon,
        String hair,
        boolean glasses,
        String badgeStyle,
        String stance,
        String outfit,
        String accentColor,
        String face,
        String skinTone,
        String bodyShape,
        String clothingVariant,
        String accessoryVariant,
        String badgeLabel,
        Map<String, Object> avatarStyle
    ) {}

    public record PersonalityDto(
        double rigor,
        double creativity,
        double riskTolerance,
        double obedience
    ) {}
}
