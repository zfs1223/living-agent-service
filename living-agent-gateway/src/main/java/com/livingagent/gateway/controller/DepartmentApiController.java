package com.livingagent.gateway.controller;

import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.AuthContext;
import com.livingagent.core.security.Department;
import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/dept")
public class DepartmentApiController {

    private static final Logger log = LoggerFactory.getLogger(DepartmentApiController.class);

    private final UnifiedAuthService authService;
    private final BrainRegistry brainRegistry;

    private static final Set<String> VALID_DEPARTMENTS = Set.of(
        "tech", "hr", "finance", "sales", "admin", "cs", "legal", "ops"
    );

    public DepartmentApiController(UnifiedAuthService authService, BrainRegistry brainRegistry) {
        this.authService = authService;
        this.brainRegistry = brainRegistry;
    }

    @PostMapping("/{department}/chat")
    public ResponseEntity<ChatResponse> chat(
            @PathVariable String department,
            @RequestBody ChatRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(401)
                .body(new ChatResponse(null, "请先登录", "UNAUTHORIZED"));
        }
        
        AuthContext ctx = ctxOpt.get();
        
        if (!hasDepartmentAccess(ctx, department)) {
            return ResponseEntity.status(403)
                .body(new ChatResponse(null, "无权访问该部门", "FORBIDDEN"));
        }
        
        String brainName = Department.mapDepartmentToBrain(department);
        log.info("Department chat: dept={}, brain={}, user={}", department, brainName, ctx.getEmployeeId());
        
        return ResponseEntity.ok(new ChatResponse(
            "Processed by " + brainName,
            "Response from " + department + " department",
            "SUCCESS"
        ));
    }

    @GetMapping("/{department}/info")
    public ResponseEntity<DepartmentInfo> getDepartmentInfo(
            @PathVariable String department,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        
        AuthContext ctx = ctxOpt.get();
        
        if (!hasDepartmentAccess(ctx, department)) {
            return ResponseEntity.status(403).build();
        }
        
        if (!VALID_DEPARTMENTS.contains(department.toLowerCase())) {
            return ResponseEntity.notFound().build();
        }
        
        String brainName = Department.mapDepartmentToBrain(department);
        
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
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        
        AuthContext ctx = ctxOpt.get();
        
        if (!hasDepartmentAccess(ctx, department)) {
            return ResponseEntity.status(403).build();
        }
        
        List<DepartmentMember> members = new ArrayList<>();
        members.add(new DepartmentMember("emp_001", "示例员工", department, "在线"));
        
        return ResponseEntity.ok(members);
    }

    @GetMapping("/{department}/brains")
    public ResponseEntity<List<BrainInfo>> getDepartmentBrains(
            @PathVariable String department,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        
        AuthContext ctx = ctxOpt.get();
        
        if (!hasDepartmentAccess(ctx, department)) {
            return ResponseEntity.status(403).build();
        }
        
        String brainName = Department.mapDepartmentToBrain(department);
        List<BrainInfo> brains = new ArrayList<>();
        
        brains.add(new BrainInfo(
            brainName,
            Department.mapBrainToDepartment(brainName) + "大脑",
            true,
            department
        ));
        
        return ResponseEntity.ok(brains);
    }

    @GetMapping("/my")
    public ResponseEntity<MyDepartmentInfo> getMyDepartment(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        
        AuthContext ctx = ctxOpt.get();
        
        if (ctx.getDepartment() == null) {
            return ResponseEntity.ok(new MyDepartmentInfo(null, null, null, ctx.getAccessLevel().name()));
        }
        
        String dept = ctx.getDepartment().toLowerCase();
        String brainName = Department.mapDepartmentToBrain(dept);
        
        return ResponseEntity.ok(new MyDepartmentInfo(
            dept,
            Department.mapBrainToDepartment(brainName),
            brainName,
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

    private boolean hasDepartmentAccess(AuthContext ctx, String department) {
        if (ctx.getAccessLevel() == AccessLevel.FULL) {
            return true;
        }
        
        if (ctx.getAccessLevel() == AccessLevel.CHAT_ONLY) {
            return false;
        }
        
        String userDept = ctx.getDepartment();
        if (userDept == null) {
            return false;
        }
        
        return userDept.equalsIgnoreCase(department);
    }

    public record ChatRequest(String message, Map<String, Object> context) {
        public ChatRequest {
            context = context != null ? context : new HashMap<>();
        }
    }

    public record ChatResponse(String response, String message, String status) {}

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
        String status
    ) {}

    public record BrainInfo(
        String name,
        String displayName,
        boolean available,
        String department
    ) {}

    public record MyDepartmentInfo(
        String code,
        String name,
        String brain,
        String accessLevel
    ) {}
}
