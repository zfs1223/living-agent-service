package com.livingagent.gateway.controller;

import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.security.AccessGateService;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.AuthContext;
import com.livingagent.core.security.Department;
import com.livingagent.core.security.DepartmentAccessService;
import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthSession;
import com.livingagent.gateway.service.DepartmentChatService;
import com.livingagent.gateway.service.DepartmentChatService.DepartmentChatResult;
import com.livingagent.gateway.service.OrganizationQueryService;
import com.livingagent.gateway.service.OrganizationQueryService.BrainSummary;
import com.livingagent.gateway.service.OrganizationQueryService.DepartmentSummary;
import com.livingagent.core.employee.EmployeeService.MemberSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dept")
public class DepartmentApiController {

    private static final Logger log = LoggerFactory.getLogger(DepartmentApiController.class);

    private final UnifiedAuthService authService;
    private final BrainRegistry brainRegistry;
    private final AccessGateService accessGateService;
    private final DepartmentChatService departmentChatService;
    private final OrganizationQueryService organizationQueryService;
    private final DepartmentAccessService departmentAccessService;

    private static final Set<String> VALID_DEPARTMENTS = Set.of(
        "tech", "hr", "finance", "sales", "admin", "cs", "legal", "ops", "main"
    );

    public DepartmentApiController(
            UnifiedAuthService authService,
            BrainRegistry brainRegistry,
            AccessGateService accessGateService,
            DepartmentChatService departmentChatService,
            OrganizationQueryService organizationQueryService,
            DepartmentAccessService departmentAccessService) {
        this.authService = authService;
        this.brainRegistry = brainRegistry;
        this.accessGateService = accessGateService;
        this.departmentChatService = departmentChatService;
        this.organizationQueryService = organizationQueryService;
        this.departmentAccessService = departmentAccessService;
    }

    @PostMapping("/{department}/chat")
    public ResponseEntity<ChatResponse> chat(
            @PathVariable String department,
            @RequestBody ChatRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        
        String message = request.message();
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest()
                .body(new ChatResponse(false, null, null, null, null, null, "BAD_REQUEST", "消息内容不能为空", null, null));
        }

        DepartmentChatResult result = departmentChatService.processDepartmentChat(department, message, authorization);
        
        if (!result.success()) {
            HttpStatus status = switch (result.status()) {
                case "UNAUTHORIZED" -> HttpStatus.UNAUTHORIZED;
                case "FORBIDDEN", "PERMISSION_DENIED" -> HttpStatus.FORBIDDEN;
                default -> HttpStatus.OK;
            };
            return ResponseEntity.status(status)
                .body(ChatResponse.fromError(result));
        }
        
        return ResponseEntity.ok(ChatResponse.fromSuccess(result));
    }

    @GetMapping("/{department}/info")
    public ResponseEntity<DepartmentInfo> getDepartmentInfo(
            @PathVariable String department,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", Department.mapDepartmentToBrain(department))) {
            return ResponseEntity.status(403).build();
        }
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        
        AuthContext ctx = ctxOpt.get();
        
        if (!departmentAccessService.hasDepartmentAccess(ctx, department)) {
            return ResponseEntity.status(403).build();
        }

        String brainName = Department.mapDepartmentToBrain(department);
        if (!accessGateService.canRoute(ctx.getEmployeeId(), "brain", brainName)) {
            return ResponseEntity.status(403).build();
        }
        
        Optional<DepartmentSummary> deptOpt = organizationQueryService.getDepartmentByCode(department);
        if (deptOpt.isPresent()) {
            DepartmentSummary dept = deptOpt.get();
            return ResponseEntity.ok(new DepartmentInfo(
                dept.code(),
                dept.name(),
                dept.brain(),
                "/api/dept/" + department,
                "/ws/dept/" + department
            ));
        }

        if (!VALID_DEPARTMENTS.contains(department.toLowerCase())) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(new DepartmentInfo(
            department,
            Department.mapBrainToDepartment(brainName),
            brainName,
            "/api/dept/" + department,
            "/ws/dept/" + department
        ));
    }

    @GetMapping("/{department}/members")
    public ResponseEntity<List<DepartmentMember>> getDepartmentMembers(
            @PathVariable String department,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", Department.mapDepartmentToBrain(department))) {
            return ResponseEntity.status(403).build();
        }
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        
        AuthContext ctx = ctxOpt.get();
        
        if (!departmentAccessService.hasDepartmentAccess(ctx, department)) {
            return ResponseEntity.status(403).build();
        }

        if (!accessGateService.canRoute(ctx.getEmployeeId(), "brain", Department.mapDepartmentToBrain(department))) {
            return ResponseEntity.status(403).build();
        }
        
        List<MemberSummary> members = organizationQueryService.getActiveDepartmentMembers(department);
        
        if (members.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<DepartmentMember> result = members.stream()
            .map(m -> new DepartmentMember(
                m.employeeId(),
                m.name(),
                m.departmentCode(),
                m.status(),
                m.origin(),
                m.position(),
                m.avatarUrl()
            ))
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{department}/brains")
    public ResponseEntity<List<BrainInfo>> getDepartmentBrains(
            @PathVariable String department,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", Department.mapDepartmentToBrain(department))) {
            return ResponseEntity.status(403).build();
        }
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        
        AuthContext ctx = ctxOpt.get();
        
        if (!departmentAccessService.hasDepartmentAccess(ctx, department)) {
            return ResponseEntity.status(403).build();
        }

        if (!accessGateService.canRoute(ctx.getEmployeeId(), "brain", Department.mapDepartmentToBrain(department))) {
            return ResponseEntity.status(403).build();
        }
        
        List<BrainSummary> brains = organizationQueryService.getDepartmentBrains(department);
        
        if (brains.isEmpty()) {
            String brainName = Department.mapDepartmentToBrain(department);
            brains = List.of(new BrainSummary(
                brainName,
                Department.mapBrainToDepartment(brainName) + "大脑",
                null,
                Department.mapBrainToDepartment(brainName),
                department,
                false,
                false,
                "STOPPED",
                null
            ));
        }

        List<BrainInfo> result = brains.stream()
            .map(b -> new BrainInfo(
                b.name(),
                b.displayName(),
                b.available(),
                b.running(),
                b.department(),
                b.departmentCode(),
                b.state(),
                b.modelConfigured()
            ))
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/my")
    public ResponseEntity<MyDepartmentInfo> getMyDepartment(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        
        AuthContext ctx = ctxOpt.get();

        if (ctx.getDepartment() == null) {
            return ResponseEntity.ok(new MyDepartmentInfo(
                null, null, null, null, false, ctx.getAccessLevel().name()
            ));
        }
        
        String deptCode = ctx.getDepartment().toLowerCase();

        Optional<DepartmentSummary> deptOpt = organizationQueryService.getDepartmentByCode(deptCode);
        
        if (deptOpt.isPresent()) {
            DepartmentSummary dept = deptOpt.get();
            
            if (!accessGateService.canRoute(ctx.getEmployeeId(), "brain", dept.brain())) {
                return ResponseEntity.status(403).build();
            }

            return ResponseEntity.ok(new MyDepartmentInfo(
                dept.code(),
                dept.name(),
                dept.brain(),
                dept.brainId(),
                dept.brainRunning(),
                ctx.getAccessLevel().name()
            ));
        }

        String brainName = Department.mapDepartmentToBrain(deptCode);
        if (!accessGateService.canRoute(ctx.getEmployeeId(), "brain", brainName)) {
            return ResponseEntity.status(403).build();
        }
        
        return ResponseEntity.ok(new MyDepartmentInfo(
            deptCode,
            Department.mapBrainToDepartment(brainName),
            brainName,
            null,
            false,
            ctx.getAccessLevel().name()
        ));
    }

    private Optional<AuthContext> getAuthContext(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Optional.empty();
        }
        
        String sessionId = authorization.substring(7);
        Optional<AuthSession> sessionOpt = authService.validateSession(sessionId);
        
        return sessionOpt.map(AuthSession::authContext);
    }

    public record ChatRequest(String message, Map<String, Object> context) {
        public ChatRequest {
            context = context != null ? context : new HashMap<>();
        }
    }

    public record ChatResponse(
        boolean success,
        String requestId,
        String department,
        String brain,
        String text,
        String model,
        String status,
        String reason,
        String intent,
        String neuron
    ) {
        public static ChatResponse fromSuccess(DepartmentChatResult result) {
            return new ChatResponse(
                true,
                result.requestId(),
                result.department(),
                result.brain(),
                result.text(),
                result.model(),
                result.status(),
                null,
                result.intent(),
                result.neuron()
            );
        }

        public static ChatResponse fromError(DepartmentChatResult result) {
            return new ChatResponse(
                false,
                result.requestId(),
                result.department(),
                result.brain(),
                null,
                null,
                result.status(),
                result.reason(),
                null,
                null
            );
        }

        @Deprecated
        public ChatResponse(String response, String message, String status) {
            this(false, null, null, null, response, null, status, message, null, null);
        }
    }

    public record DepartmentInfo(
        String code,
        String name,
        String brain,
        String apiPrefix,
        String wsChannel
    ) {}

    public record DepartmentMember(
        String employeeId,
        String name,
        String department,
        String status,
        String origin,
        String title,
        String avatarUrl
    ) {}

    public record BrainInfo(
        String name,
        String displayName,
        boolean available,
        boolean running,
        String department,
        String departmentCode,
        String state,
        String modelConfigured
    ) {}

    public record MyDepartmentInfo(
        String code,
        String name,
        String brain,
        String brainId,
        boolean brainRunning,
        String accessLevel
    ) {}

    @GetMapping("/{department}/conversations")
    public ResponseEntity<?> listConversations(
            @PathVariable String department,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = extractToken(authorization);
        if (token == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Optional<AuthSession> sessionOpt = authService.validateSession(token);
        if (sessionOpt.isEmpty() || sessionOpt.get().isExpired()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String userId = sessionOpt.get().authContext().getEmployeeId();
        log.debug("listConversations API: department={}, userId={}", department, userId);
        List<?> conversations = departmentChatService.listActiveConversations(userId, department);
        return ResponseEntity.ok(conversations);
    }

    @GetMapping("/{department}/conversations/history")
    public ResponseEntity<?> getConversationHistory(
            @PathVariable String department,
            @RequestParam String conversationId,
            @RequestParam(defaultValue = "100") int limit,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = extractToken(authorization);
        if (token == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Optional<AuthSession> sessionOpt = authService.validateSession(token);
        if (sessionOpt.isEmpty() || sessionOpt.get().isExpired()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        List<?> history = departmentChatService.getConversationHistory(conversationId, limit);
        return ResponseEntity.ok(history);
    }

    @DeleteMapping("/{department}/conversations")
    public ResponseEntity<?> deleteConversation(
            @PathVariable String department,
            @RequestParam String conversationId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = extractToken(authorization);
        if (token == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Optional<AuthSession> sessionOpt = authService.validateSession(token);
        if (sessionOpt.isEmpty() || sessionOpt.get().isExpired()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        departmentChatService.softDeleteConversation(conversationId);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    private String extractToken(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return authorization;
    }
}
