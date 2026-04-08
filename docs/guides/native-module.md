# Living Agent Native 模块说明文档

> Rust 高性能原生组件库 - 调试与开发指南

---

## 一、模块概述

### 1.1 定位与职责

`living-agent-native` 是 Living Agent Service 的 **Rust 原生高性能组件库**，通过 JNI 与 Java 主服务集成，承担以下职责：

- **音频处理**: 实时音频编解码、语音活动检测
- **并发通信**: 高性能消息通道实现
- **本地存储**: 记忆和知识的 SQLite 本地存储
- **安全验证**: 命令和路径的安全校验

### 1.2 技术栈

```
living-agent-native (Rust 2021 Edition)
├── 音频处理
│   ├── opus 0.3.1          - Opus 编解码
│   ├── rubato 1.0.1        - 音频重采样
│   └── dasp 0.11           - 数字音频信号处理
├── 并发通信
│   ├── crossbeam 0.8.4     - 高性能并发原语
│   ├── parking_lot 0.12.5  - 高效锁实现
│   └── tokio 1.35          - 异步运行时
├── 数据存储
│   └── rusqlite 0.38.0     - SQLite 绑定 (bundled)
├── JNI 绑定
│   └── jni 0.22.3          - Java Native Interface
├── 序列化
│   ├── serde 1.0.228       - 序列化框架
│   └── serde_json 1.0.149  - JSON 支持
├── 安全
│   ├── sha2 0.10.9         - SHA 哈希
│   ├── base64 0.22.1       - Base64 编解码
│   └── regex 1.12.3        - 正则表达式
└── 其他
    ├── chrono 0.4.44       - 时间处理
    ├── uuid 1.6.1          - UUID 生成
    ├── lru 0.16.3          - LRU 缓存
    ├── thiserror 2.0.18    - 错误派生
    └── anyhow 1.0.102      - 错误处理
```

### 1.3 目录结构

```
living-agent-native/
├── Cargo.toml              # 项目配置
├── Cargo.lock              # 依赖锁定
├── src/
│   ├── lib.rs              # 库入口
│   ├── audio/              # 音频处理模块
│   │   ├── mod.rs
│   │   ├── opus_codec.rs   # Opus 编解码
│   │   ├── vad.rs          # 语音活动检测
│   │   ├── resampler.rs    # 重采样器
│   │   └── processor.rs    # 音频处理器
│   ├── channel/            # 通道模块
│   │   ├── mod.rs
│   │   ├── mpsc_channel.rs # MPSC 通道
│   │   ├── broadcast_channel.rs # 广播通道
│   │   └── message.rs      # 消息类型
│   ├── memory/             # 记忆模块
│   │   ├── mod.rs
│   │   ├── backend.rs      # SQLite 后端
│   │   ├── entry.rs        # 记忆条目
│   │   └── query.rs        # 查询接口
│   ├── knowledge/          # 知识模块
│   │   ├── mod.rs
│   │   ├── types.rs        # 知识类型定义
│   │   ├── sqlite_backend.rs # SQLite 后端
│   │   ├── vector_store.rs # 向量存储
│   │   ├── similarity.rs   # 相似度计算
│   │   └── cache.rs        # LRU 缓存
│   ├── security/           # 安全模块
│   │   ├── mod.rs
│   │   ├── validator.rs    # 安全验证器
│   │   ├── policy.rs       # 安全策略
│   │   └── sandbox.rs      # 沙箱配置
│   └── jni/                # JNI 接口
│       ├── mod.rs
│       ├── audio_jni.rs
│       ├── channel_jni.rs
│       ├── memory_jni.rs
│       ├── knowledge_jni.rs
│       └── security_jni.rs
├── benches/                # 性能基准测试
│   └── audio_benchmark.rs
├── target/                 # 编译输出
└── .cargo/                 # Cargo 配置
    └── registry/           # 离线依赖缓存
```

---

## 二、模块详解

### 2.1 音频处理模块 (audio)

#### 2.1.1 核心组件

| 组件 | 文件 | 功能 |
|------|------|------|
| `OpusEncoder` | opus_codec.rs | Opus 音频编码器 |
| `OpusDecoder` | opus_codec.rs | Opus 音频解码器 |
| `VadDetector` | vad.rs | 语音活动检测器 |
| `Resampler` | resampler.rs | 音频重采样器 |
| `AudioProcessor` | processor.rs | 统一音频处理器 |

#### 2.1.2 配置参数

```rust
// Opus 编解码配置
pub struct OpusConfig {
    pub sample_rate: u32,    // 采样率，默认 48000
    pub channels: u8,        // 声道数，默认 1 (Mono)
    pub frame_size_ms: u32,  // 帧大小(毫秒)，默认 20
    pub bitrate: u32,        // 比特率，默认 24000
}

// VAD 配置
pub struct VadConfig {
    pub energy_threshold: f32,      // 能量阈值，默认 0.01
    pub silence_duration_ms: u64,   // 静音判定时长，默认 500ms
    pub speech_duration_ms: u64,    // 语音判定时长，默认 100ms
    pub sample_rate: u32,           // 采样率，默认 16000
}

// 音频处理器配置
pub struct AudioConfig {
    pub sample_rate: u32,       // 采样率，默认 16000
    pub channels: u8,           // 声道数，默认 1
    pub frame_size: usize,      // 帧大小，默认 960
    pub enable_vad: bool,       // 启用 VAD，默认 true
    pub vad_config: VadConfig,  // VAD 配置
}
```

#### 2.1.3 使用示例

```rust
use living_agent_native::{AudioProcessor, AudioConfig};

// 创建音频处理器
let config = AudioConfig::default();
let mut processor = AudioProcessor::new(config)?;

// 解码 Opus 数据
let frame = processor.decode_opus(&opus_data)?;

// 检测语音活动
let is_voice = processor.detect_voice_activity(&frame.samples);

// 编码 PCM 数据
let opus_data = processor.encode_pcm(&pcm_data)?;

// 获取统计信息
let stats = processor.get_stats();
println!("处理帧数: {}", stats.frames_processed);
println!("语音帧: {}", stats.voice_frames);
println!("静音帧: {}", stats.silence_frames);
```

#### 2.1.4 音频帧结构

```rust
pub struct AudioFrame {
    pub samples: Vec<i16>,     // PCM 采样数据
    pub sample_rate: u32,      // 采样率
    pub channels: u8,          // 声道数
    pub timestamp_ms: u64,     // 时间戳
}
```

---

### 2.2 通道模块 (channel)

#### 2.2.1 核心组件

| 组件 | 文件 | 功能 |
|------|------|------|
| `MpscChannel` | mpsc_channel.rs | 多生产者单消费者通道 |
| `BroadcastChannel` | broadcast_channel.rs | 广播通道 (一对多) |
| `ChannelMessage` | message.rs | 消息类型定义 |

#### 2.2.2 通道配置

```rust
pub struct ChannelConfig {
    pub name: String,           // 通道名称
    pub capacity: usize,        // 容量，默认 1024
    pub enable_priority: bool,  // 启用优先级，默认 false
    pub timeout_ms: u64,        // 超时时间，默认 5000ms
}
```

#### 2.2.3 使用示例

**MPSC 通道:**

```rust
use living_agent_native::{MpscChannel, ChannelConfig};

let channel = MpscChannel::new(ChannelConfig::default());
let sender = channel.sender();
let receiver = channel.receiver();

// 发送消息
sender.send("Hello".to_string())?;

// 接收消息
let msg = receiver.recv()?;

// 非阻塞接收
let msg = receiver.try_recv()?;
```

**广播通道:**

```rust
use living_agent_native::{BroadcastChannel, ChannelConfig};

let channel = BroadcastChannel::new(ChannelConfig {
    name: "broadcast".to_string(),
    capacity: 256,
    ..Default::default()
});

let sender = channel.sender();
let receiver1 = channel.subscribe();
let receiver2 = channel.subscribe();

// 广播消息到所有订阅者
let sent_count = sender.broadcast("Hello All".to_string())?;
```

#### 2.2.4 错误类型

```rust
pub enum ChannelError {
    Closed,       // 通道已关闭
    Full,         // 通道已满
    Empty,        // 通道为空
    SendTimeout,  // 发送超时
    RecvTimeout,  // 接收超时
    Io(String),   // IO 错误
}
```

---

### 2.3 记忆模块 (memory)

#### 2.3.1 核心组件

| 组件 | 文件 | 功能 |
|------|------|------|
| `MemoryBackend` | backend.rs | SQLite 存储后端 |
| `MemoryEntry` | entry.rs | 记忆条目 |
| `MemoryQuery` | query.rs | 查询接口 |

#### 2.3.2 数据结构

```rust
// 记忆条目
pub struct MemoryEntry {
    pub id: String,              // 唯一标识
    pub key: String,             // 键
    pub content: String,         // 内容
    pub category: MemoryCategory, // 分类
    pub session_id: Option<String>, // 会话ID
    pub timestamp: u64,          // 时间戳
    pub score: Option<f32>,      // 相关性分数
    pub metadata: HashMap<String, String>, // 元数据
}

// 记忆分类
pub enum MemoryCategory {
    Conversation,   // 对话
    Fact,           // 事实
    Preference,     // 偏好
    Context,        // 上下文
    Temporary,      // 临时
}

// 查询参数
pub struct MemoryQuery {
    pub query: String,           // 查询字符串
    pub session_id: Option<String>, // 会话过滤
    pub category: Option<MemoryCategory>, // 分类过滤
    pub limit: usize,            // 结果限制，默认 10
}
```

#### 2.3.3 使用示例

```rust
use living_agent_native::{MemoryBackend, MemoryConfig, MemoryEntry, MemoryQuery, MemoryCategory};

// 创建后端
let backend = MemoryBackend::new(MemoryConfig {
    db_path: "memory.db".to_string(),
    max_entries: 10000,
    ..Default::default()
})?;

// 存储记忆
let entry = MemoryEntry {
    id: uuid::Uuid::new_v4().to_string(),
    key: "user_preference".to_string(),
    content: "用户喜欢简洁的回答".to_string(),
    category: MemoryCategory::Preference,
    session_id: Some("session-001".to_string()),
    timestamp: SystemTime::now().duration_since(UNIX_EPOCH)?.as_secs(),
    ..Default::default()
};
backend.store(&entry)?;

// 召回记忆
let query = MemoryQuery {
    query: "用户偏好".to_string(),
    session_id: Some("session-001".to_string()),
    limit: 5,
    ..Default::default()
};
let results = backend.recall(&query)?;

// 获取统计
let stats = backend.get_stats()?;
println!("总条目: {}", stats.total_entries);
```

#### 2.3.4 数据库表结构

```sql
CREATE TABLE memories (
    id TEXT PRIMARY KEY,
    key TEXT NOT NULL,
    content TEXT NOT NULL,
    category TEXT NOT NULL,
    session_id TEXT,
    timestamp INTEGER NOT NULL,
    score REAL,
    metadata TEXT
);

CREATE INDEX idx_memories_key ON memories(key);
CREATE INDEX idx_memories_category ON memories(category);
CREATE INDEX idx_memories_session ON memories(session_id);
CREATE INDEX idx_memories_timestamp ON memories(timestamp);
```

---

### 2.4 知识模块 (knowledge)

#### 2.4.1 核心组件

| 组件 | 文件 | 功能 |
|------|------|------|
| `SQLiteKnowledgeBackend` | sqlite_backend.rs | SQLite 存储后端 |
| `KnowledgeEntry` | types.rs | 知识条目 |
| `VectorStore` | vector_store.rs | 向量存储接口 |
| `KnowledgeCache` | cache.rs | LRU 缓存 |

#### 2.4.2 数据结构

```rust
// 知识条目
pub struct KnowledgeEntry {
    pub id: String,               // 唯一标识
    pub key: String,              // 键
    pub content: String,          // 内容
    pub knowledge_type: KnowledgeType, // 知识类型
    pub importance: Importance,   // 重要性
    pub validity: Validity,       // 有效性
    pub metadata: KnowledgeMetadata, // 元数据
    pub vector: Option<Vec<f32>>, // 向量嵌入
    pub created_at: u64,          // 创建时间
    pub updated_at: u64,          // 更新时间
    pub expires_at: Option<u64>,  // 过期时间
}

// 知识类型
pub enum KnowledgeType {
    Fact,          // 事实
    Process,       // 流程
    Experience,    // 经验
    BestPractice,  // 最佳实践
    Temporary,     // 临时
}

// 重要性
pub enum Importance {
    High,    // 高 (权重 1.0)
    Medium,  // 中 (权重 0.6)
    Low,     // 低 (权重 0.3)
}

// 有效性
pub enum Validity {
    Permanent,   // 永久
    LongTerm,    // 长期 (7天)
    ShortTerm,   // 短期 (7天)
    Temporary,   // 临时 (7天)
}

// 元数据
pub struct KnowledgeMetadata {
    pub brain_domain: Option<String>, // 所属大脑/领域
    pub confidence: f32,              // 置信度
    pub source: Option<String>,       // 来源
    pub validated: bool,              // 是否验证
    pub access_count: u32,            // 访问次数
    pub created_at: u64,
    pub updated_at: u64,
}
```

#### 2.4.3 使用示例

```rust
use living_agent_native::{
    SQLiteKnowledgeBackend, KnowledgeEntry, KnowledgeType, Importance, Validity
};

// 创建后端
let backend = SQLiteKnowledgeBackend::new("knowledge.db")?;

// 创建知识条目
let entry = KnowledgeEntry::new(
    "code_review_best_practice".to_string(),
    "代码审查应关注：可读性、性能、安全性、可维护性".to_string(),
    KnowledgeType::BestPractice,
)
.with_brain_domain("tech".to_string())
.with_importance(Importance::High)
.with_confidence(0.95)
.with_validated(true);

// 存储知识
backend.store(&entry)?;

// 检索知识
let result = backend.retrieve("code_review_best_practice")?;

// 搜索知识
let results = backend.search("代码审查", 10)?;

// 清理过期知识
let cleaned = backend.cleanup_expired()?;
```

#### 2.4.4 相似度计算

```rust
use living_agent_native::{cosine_similarity, euclidean_distance, dot_product};

let vec1 = vec![1.0, 2.0, 3.0];
let vec2 = vec![4.0, 5.0, 6.0];

// 余弦相似度
let cos_sim = cosine_similarity(&vec1, &vec2);

// 欧氏距离
let euc_dist = euclidean_distance(&vec1, &vec2);

// 点积
let dot = dot_product(&vec1, &vec2);
```

#### 2.4.5 数据库表结构

```sql
CREATE TABLE knowledge_entries (
    id TEXT PRIMARY KEY,
    key TEXT UNIQUE NOT NULL,
    content TEXT NOT NULL,
    knowledge_type TEXT NOT NULL DEFAULT 'fact',
    importance TEXT NOT NULL DEFAULT 'medium',
    validity TEXT NOT NULL DEFAULT 'long_term',
    brain_domain TEXT,
    metadata TEXT,
    vector BLOB,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    expires_at INTEGER,
    access_count INTEGER DEFAULT 1,
    relevance_score REAL DEFAULT 1.0
);

CREATE INDEX idx_key ON knowledge_entries(key);
CREATE INDEX idx_type ON knowledge_entries(knowledge_type);
CREATE INDEX idx_domain ON knowledge_entries(brain_domain);
CREATE INDEX idx_created ON knowledge_entries(created_at);
```

---

### 2.5 安全模块 (security)

#### 2.5.1 核心组件

| 组件 | 文件 | 功能 |
|------|------|------|
| `SecurityValidator` | validator.rs | 安全验证器 |
| `Sandbox` | sandbox.rs | 沙箱配置 |
| `SecurityContext` | mod.rs | 安全上下文 |

#### 2.5.2 数据结构

```rust
// 安全级别
pub enum SecurityLevel {
    ReadOnly,    // 只读
    Supervised,  // 监督模式 (默认)
    Full,        // 完全权限
}

// 安全上下文
pub struct SecurityContext {
    pub user_id: String,           // 用户ID
    pub session_id: String,        // 会话ID
    pub security_level: SecurityLevel, // 安全级别
    pub allowed_commands: Vec<String>, // 允许的命令
    pub allowed_paths: Vec<String>,    // 允许的路径
}

// 沙箱配置
pub struct SandboxConfig {
    pub enabled: bool,             // 是否启用，默认 true
    pub max_memory_mb: u64,        // 最大内存，默认 512MB
    pub max_cpu_percent: u32,      // 最大CPU，默认 80%
    pub timeout_seconds: u64,      // 超时时间，默认 300s
    pub allowed_networks: Vec<String>, // 允许的网络
    pub read_only_paths: Vec<String>,  // 只读路径
    pub writable_paths: Vec<String>,   // 可写路径
}

// 验证结果
pub struct ValidationResult {
    pub is_valid: bool,            // 是否有效
    pub reason: Option<String>,    // 原因
    pub risk_level: RiskLevel,     // 风险级别
}

// 风险级别
pub enum RiskLevel {
    Safe,      // 安全
    Low,       // 低风险
    Medium,    // 中风险
    High,      // 高风险
    Critical,  // 危险
}
```

#### 2.5.3 危险命令黑名单

```rust
// 默认禁止的命令
[
    "rm", "rmdir", "del", "format", "fdisk", "mkfs",
    "dd", "shred", "wipe", "sudo", "su", "chmod", "chown",
    "passwd", "useradd", "userdel", "usermod",
    "shutdown", "reboot", "halt", "poweroff",
    "iptables", "ufw", "firewall-cmd",
    "curl", "wget", "nc", "netcat", "telnet",
    "python", "python3", "perl", "ruby", "php",
    "bash", "sh", "zsh", "fish", "cmd", "powershell",
    "eval", "exec", "source",
]

// 危险模式正则
[
    r";\s*rm\s",           // 命令注入: ; rm
    r"\|\s*rm\s",          // 管道注入: | rm
    r"`[^`]*`",            // 命令替换
    r"\$\([^)]*\)",        // 命令替换 $(...)
    r"\$\{[^}]*\}",        // 变量替换
    r">\s*/dev/",          // 重定向到设备
    r"<\s*/dev/",          // 从设备读取
    r"\.\./",              // 路径遍历
    r"~/",                 // 家目录
    r"/etc/passwd",        // 敏感文件
    r"/etc/shadow",        // 敏感文件
    r"id_rsa",             // 私钥文件
]
```

#### 2.5.4 使用示例

```rust
use living_agent_native::{SecurityValidator, SecurityContext, SecurityLevel, Sandbox, SandboxConfig};

// 创建验证器
let mut validator = SecurityValidator::new();

// 添加允许路径
validator.add_allowed_path("/app/data");
validator.add_denied_path("/etc");

// 创建安全上下文
let context = SecurityContext::new("user-001", "session-001")
    .with_level(SecurityLevel::Supervised)
    .allow_command("ls")
    .allow_path("/app/data");

// 验证命令
let result = validator.validate_command("rm -rf /", &context);
if !result.is_valid {
    println!("命令被拒绝: {:?}", result.reason);
    println!("风险级别: {:?}", result.risk_level);
}

// 验证路径
let result = validator.validate_path("/etc/passwd", &context);
if !result.is_valid {
    println!("路径被拒绝: {:?}", result.reason);
}

// 沙箱配置
let sandbox = Sandbox::new(SandboxConfig {
    enabled: true,
    max_memory_mb: 512,
    max_cpu_percent: 80,
    timeout_seconds: 300,
    allowed_networks: vec!["api.example.com".to_string()],
    read_only_paths: vec!["/app/config".to_string()],
    writable_paths: vec!["/app/data".to_string()],
});

// 验证网络访问
if sandbox.validate_network("api.example.com") {
    println!("网络访问允许");
}

// 验证路径访问
if sandbox.validate_path("/app/data/file.txt", true) {
    println!("路径写入允许");
}
```

---

## 三、JNI 接口

### 3.1 接口映射

| Rust 模块 | JNI 文件 | Java 调用入口 |
|-----------|----------|---------------|
| audio | audio_jni.rs | `com.soarcloud.livingagent.native.AudioNative` |
| channel | channel_jni.rs | `com.soarcloud.livingagent.native.ChannelNative` |
| memory | memory_jni.rs | `com.soarcloud.livingagent.native.MemoryNative` |
| knowledge | knowledge_jni.rs | `com.soarcloud.livingagent.native.KnowledgeNative` |
| security | security_jni.rs | `com.soarcloud.livingagent.native.SecurityNative` |

### 3.2 JNI 工具函数

```rust
// 字符串转换
pub fn jstring_to_string(env: &mut Env, jstr: JString) -> Result<String, String>;
pub fn string_to_jstring(env: &mut Env, s: &str) -> Result<jstring, String>;

// 字节数组转换
pub fn jbyte_array_to_bytes(env: &mut Env, arr: JByteArray) -> Result<Vec<u8>, String>;
```

### 3.3 编译输出

```toml
# Cargo.toml
[lib]
name = "living_agent_native"
crate-type = ["cdylib", "rlib"]  # cdylib: 动态库, rlib: Rust库
```

编译后生成:
- Windows: `living_agent_native.dll`
- Linux: `libliving_agent_native.so`
- macOS: `libliving_agent_native.dylib`

---

## 四、编译与调试

### 4.1 环境要求

- **Rust**: 1.70+ (推荐最新稳定版)
- **目标平台**: Windows (MSVC), Linux, macOS
- **依赖**: C 编译器 (用于 opus、sqlite 编译)

### 4.2 编译命令

```bash
# 开发模式 (快速编译，包含调试信息)
cargo build

# 发布模式 (优化编译)
cargo build --release

# 仅编译库
cargo build --lib

# 运行测试
cargo test

# 运行基准测试
cargo bench

# 检查代码
cargo check
cargo clippy
```

### 4.3 编译配置

```toml
# 发布优化配置
[profile.release]
opt-level = 3        # 最高优化级别
lto = true           # 链接时优化
codegen-units = 1    # 单个代码生成单元
strip = true         # 剥离符号

# 开发配置
[profile.dev]
opt-level = 0        # 无优化
debug = true         # 包含调试信息
```

### 4.4 离线编译

项目已配置离线依赖缓存 (`.cargo/registry/`)，支持离线编译：

```bash
# 离线编译
cargo build --offline
```

### 4.5 调试技巧

#### 4.5.1 启用日志

```rust
// 初始化日志
living_agent_native::init();

// 使用日志
use tracing::{info, debug, error, warn};

info!("音频处理器启动");
debug!("处理帧: {} samples", samples.len());
error!("解码失败: {}", err);
```

#### 4.5.2 单元测试

```bash
# 运行所有测试
cargo test

# 运行特定测试
cargo test test_sqlite_backend_basic

# 显示输出
cargo test -- --nocapture

# 运行特定模块测试
cargo test --lib memory::
```

#### 4.5.3 基准测试

```bash
# 运行音频基准测试
cargo bench -- audio_benchmark

# 保存基准结果
cargo bench -- --save-baseline main

# 与基准比较
cargo bench -- --baseline main
```

#### 4.5.4 常见问题排查

| 问题 | 可能原因 | 解决方案 |
|------|----------|----------|
| 编译失败: opus | 缺少 C 编译器 | 安装 Visual Studio Build Tools (Windows) 或 build-essential (Linux) |
| 链接错误: jni | Java 版本不匹配 | 确保 JAVA_HOME 配置正确 |
| SQLite 错误 | 数据库文件锁定 | 确保没有其他进程占用数据库 |
| 音频处理慢 | 帧大小不匹配 | 检查采样率和帧大小配置 |
| 内存泄漏 | 未释放资源 | 检查 RAII 模式是否正确实现 |

---

## 五、性能指标

### 5.1 音频处理

| 指标 | 数值 |
|------|------|
| Opus 编码延迟 | < 5ms (20ms 帧) |
| Opus 解码延迟 | < 2ms |
| VAD 检测延迟 | < 1ms |
| 内存占用 | < 10MB |

### 5.2 通道性能

| 指标 | 数值 |
|------|------|
| 消息吞吐量 | > 1M msg/s |
| 延迟 (P99) | < 100μs |
| 内存开销 | ~100 bytes/msg |

### 5.3 存储性能

| 指标 | 数值 |
|------|------|
| 写入 QPS | > 10K/s |
| 读取 QPS | > 50K/s |
| 查询延迟 | < 10ms |

---

## 六、与主服务集成

### 6.1 架构关系

```
┌─────────────────────────────────────────────────────────────┐
│                    living-agent-app (Java)                   │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              Java Service Layer                      │    │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐            │    │
│  │  │AudioSvc  │ │MemorySvc │ │KnowledgeSvc│           │    │
│  │  └────┬─────┘ └────┬─────┘ └────┬─────┘            │    │
│  └───────┼────────────┼────────────┼───────────────────┘    │
│          │ JNI        │ JNI        │ JNI                    │
│          ▼            ▼            ▼                        │
│  ┌─────────────────────────────────────────────────────┐    │
│  │         living-agent-native (Rust)                   │    │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────┐ │    │
│  │  │  Audio   │ │  Memory  │ │ Knowledge│ │Security│ │    │
│  │  │ Processor│ │ Backend  │ │ Backend  │ │Validator│ │    │
│  │  └──────────┘ └──────────┘ └──────────┘ └────────┘ │    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

### 6.2 配置文件

```yaml
# application.yml
native:
  library-path: ${NATIVE_LIB_PATH:./native}
  
  audio:
    sample-rate: 16000
    channels: 1
    enable-vad: true
    
  memory:
    db-path: ${LIVING_AGENT_DATA_PATH:/app/data}/memory.db
    max-entries: 10000
    
  knowledge:
    db-path: ${LIVING_AGENT_DATA_PATH:/app/data}/knowledge.db
    
  security:
    sandbox-enabled: true
    max-memory-mb: 512
```

---

## 七、相关文档

- [02-architecture.md](./02-architecture.md) - 整体架构设计
- [05-knowledge-system.md](./05-knowledge-system.md) - 知识体系设计
- [memory.md](./memory.md) - 记忆系统集成 MemOS
- [08-database-design.md](./08-database-design.md) - 数据库设计

---

## 八、版本历史

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| 0.1.0 | 2024-01 | 初始版本，包含音频、通道、记忆、知识、安全模块 |
