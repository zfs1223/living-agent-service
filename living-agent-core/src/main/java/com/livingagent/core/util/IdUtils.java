package com.livingagent.core.util;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IdUtils {

    private IdUtils() {}

    private static final String EMPLOYEE_PREFIX = "employee://";
    private static final String NEURON_PREFIX = "neuron://";
    private static final String CHANNEL_PREFIX = "channel://";

    private static final Pattern EMPLOYEE_HUMAN_PATTERN = 
        Pattern.compile("^employee://human/(dingtalk|feishu|wecom|oa)/([^/]+)$");
    private static final Pattern EMPLOYEE_DIGITAL_PATTERN = 
        Pattern.compile("^employee://digital/([^/]+)/([^/]+)/([^/]+)$");
    private static final Pattern NEURON_PATTERN = 
        Pattern.compile("^neuron://([^/]+)/([^/]+)/([^/]+)$");
    private static final Pattern CHANNEL_PATTERN = 
        Pattern.compile("^channel://([^/]+)/(.+)$");

    public enum EmployeeType {
        HUMAN,
        DIGITAL
    }

    public enum AuthProvider {
        DINGTALK,
        FEISHU,
        WECOM,
        OA,
        SYSTEM
    }

    public static String generateHumanEmployeeId(AuthProvider provider, String accountId) {
        Objects.requireNonNull(provider, "provider cannot be null");
        Objects.requireNonNull(accountId, "accountId cannot be null");
        return String.format("%shuman/%s/%s", EMPLOYEE_PREFIX, 
            provider.name().toLowerCase(), accountId);
    }

    public static String generateDigitalEmployeeId(String department, String role, String instance) {
        Objects.requireNonNull(department, "department cannot be null");
        Objects.requireNonNull(role, "role cannot be null");
        Objects.requireNonNull(instance, "instance cannot be null");
        return String.format("%sdigital/%s/%s/%s", EMPLOYEE_PREFIX, 
            department.toLowerCase(), role.toLowerCase(), instance);
    }

    public static String generateNeuronId(String department, String role, String instance) {
        Objects.requireNonNull(department, "department cannot be null");
        Objects.requireNonNull(role, "role cannot be null");
        Objects.requireNonNull(instance, "instance cannot be null");
        return String.format("%s%s/%s/%s", NEURON_PREFIX, 
            department.toLowerCase(), role.toLowerCase(), instance);
    }

    public static String generateChannelId(String scope, String name) {
        Objects.requireNonNull(scope, "scope cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        return String.format("%s%s/%s", CHANNEL_PREFIX, scope.toLowerCase(), name);
    }

    public static String employeeToNeuronId(String employeeId) {
        if (!isDigitalEmployeeId(employeeId)) {
            throw new IllegalArgumentException("Not a digital employee ID: " + employeeId);
        }
        return employeeId.replace(EMPLOYEE_PREFIX + "digital/", NEURON_PREFIX);
    }

    public static String neuronToEmployeeId(String neuronId) {
        if (!isNeuronId(neuronId)) {
            throw new IllegalArgumentException("Not a neuron ID: " + neuronId);
        }
        return neuronId.replace(NEURON_PREFIX, EMPLOYEE_PREFIX + "digital/");
    }

    public static boolean isEmployeeId(String id) {
        if (id == null) return false;
        return id.startsWith(EMPLOYEE_PREFIX);
    }

    public static boolean isHumanEmployeeId(String id) {
        if (id == null) return false;
        return EMPLOYEE_HUMAN_PATTERN.matcher(id).matches();
    }

    public static boolean isDigitalEmployeeId(String id) {
        if (id == null) return false;
        return EMPLOYEE_DIGITAL_PATTERN.matcher(id).matches();
    }

    public static boolean isNeuronId(String id) {
        if (id == null) return false;
        return NEURON_PATTERN.matcher(id).matches();
    }

    public static boolean isChannelId(String id) {
        if (id == null) return false;
        return CHANNEL_PATTERN.matcher(id).matches();
    }

    public static EmployeeType getEmployeeType(String employeeId) {
        if (isHumanEmployeeId(employeeId)) return EmployeeType.HUMAN;
        if (isDigitalEmployeeId(employeeId)) return EmployeeType.DIGITAL;
        throw new IllegalArgumentException("Invalid employee ID: " + employeeId);
    }

    public static ParsedEmployeeId parseEmployeeId(String employeeId) {
        if (isHumanEmployeeId(employeeId)) {
            Matcher m = EMPLOYEE_HUMAN_PATTERN.matcher(employeeId);
            if (m.matches()) {
                return new ParsedEmployeeId(
                    EmployeeType.HUMAN,
                    AuthProvider.valueOf(m.group(1).toUpperCase()),
                    m.group(2),
                    null,
                    null
                );
            }
        }
        if (isDigitalEmployeeId(employeeId)) {
            Matcher m = EMPLOYEE_DIGITAL_PATTERN.matcher(employeeId);
            if (m.matches()) {
                return new ParsedEmployeeId(
                    EmployeeType.DIGITAL,
                    AuthProvider.SYSTEM,
                    null,
                    m.group(1),
                    m.group(2),
                    m.group(3)
                );
            }
        }
        throw new IllegalArgumentException("Invalid employee ID: " + employeeId);
    }

    public static ParsedNeuronId parseNeuronId(String neuronId) {
        Matcher m = NEURON_PATTERN.matcher(neuronId);
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid neuron ID: " + neuronId);
        }
        return new ParsedNeuronId(m.group(1), m.group(2), m.group(3));
    }

    public static ParsedChannelId parseChannelId(String channelId) {
        Matcher m = CHANNEL_PATTERN.matcher(channelId);
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid channel ID: " + channelId);
        }
        return new ParsedChannelId(m.group(1), m.group(2));
    }

    public static final class ParsedEmployeeId {
        private final EmployeeType type;
        private final AuthProvider authProvider;
        private final String accountId;
        private final String department;
        private final String role;
        private final String instance;

        public ParsedEmployeeId(EmployeeType type, AuthProvider authProvider, 
                String accountId, String department, String role, String instance) {
            this.type = type;
            this.authProvider = authProvider;
            this.accountId = accountId;
            this.department = department;
            this.role = role;
            this.instance = instance;
        }

        public ParsedEmployeeId(EmployeeType type, AuthProvider authProvider, 
                String accountId, String department, String role) {
            this(type, authProvider, accountId, department, role, null);
        }

        public EmployeeType getType() { return type; }
        public AuthProvider getAuthProvider() { return authProvider; }
        public String getAccountId() { return accountId; }
        public String getDepartment() { return department; }
        public String getRole() { return role; }
        public String getInstance() { return instance; }
    }

    public static final class ParsedNeuronId {
        private final String department;
        private final String role;
        private final String instance;

        public ParsedNeuronId(String department, String role, String instance) {
            this.department = department;
            this.role = role;
            this.instance = instance;
        }

        public String getDepartment() { return department; }
        public String getRole() { return role; }
        public String getInstance() { return instance; }
    }

    public static final class ParsedChannelId {
        private final String scope;
        private final String name;

        public ParsedChannelId(String scope, String name) {
            this.scope = scope;
            this.name = name;
        }

        public String getScope() { return scope; }
        public String getName() { return name; }
    }
}
