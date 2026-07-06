# ExecutionMode 枚举定义

> 本文档定义了执行模式枚举。
> 版本：2026-05-20

---

## 枚举定义

```java
public enum ExecutionMode {
    ARTIFACT_ONLY("只生成产物"),
    DOCKER_SANDBOX("Docker沙箱执行"),
    LOCAL_RESTRICTED("受限本地执行"),
    HUMAN_REVIEW_REQUIRED("需人工审核"),
    APPROVAL_REQUIRED("需审批"),
    NO_EXECUTION("只回答/澄清");

    private final String description;

    ExecutionMode(String description) {
        this.description = description;
    }

    public String getDescription() { return description; }
}
```

---

## 枚举说明

| 枚举值 | 描述 | 说明 |
| --- | --- | --- |
| `ARTIFACT_ONLY` | 只生成产物 | 不执行代码，只生成文件 |
| `DOCKER_SANDBOX` | Docker沙箱执行 | 在沙箱中构建/测试/运行 |
| `LOCAL_RESTRICTED` | 受限本地执行 | 本地受限环境执行 |
| `HUMAN_REVIEW_REQUIRED` | 需人工审核 | 生成方案后必须人工审核 |
| `APPROVAL_REQUIRED` | 需审批 | 必须审批后执行 |
| `NO_EXECUTION` | 只回答/澄清 | 不执行，只回答问题 |

---

## 与其他枚举的关系

| ExecutionCapability | 推荐 ExecutionMode |
| --- | --- |
| WEB_APP_BUILD | ARTIFACT_ONLY 或 DOCKER_SANDBOX |
| CODE_CHANGE | DOCKER_SANDBOX |
| CODE_REVIEW | HUMAN_REVIEW_REQUIRED |
| DOCUMENT_GENERATION | ARTIFACT_ONLY |
| DATA_ANALYSIS | LOCAL_RESTRICTED |
| LEGAL_REVIEW | HUMAN_REVIEW_REQUIRED |
| APPROVAL_REQUIRED | APPROVAL_REQUIRED |
| CUSTOMER_SUPPORT | ARTIFACT_ONLY |

---

## 相关文件

| 文件 | 说明 |
| --- | --- |
| `living-agent-core/src/main/java/com/livingagent/core/autonomy/ExecutionMode.java` | 执行模式枚举 |
| `living-agent-core/src/main/java/com/livingagent/core/autonomy/ExecutionCapability.java` | 执行能力枚举 |
| `living-agent-core/src/main/java/com/livingagent/core/autonomy/ArtifactType.java` | 产物类型枚举 |
