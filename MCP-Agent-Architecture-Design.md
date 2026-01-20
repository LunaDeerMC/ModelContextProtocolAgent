# Model Context Protocol Agent 技术架构设计文档

## MCP Agent Architecture Design v1.0

---

## 1. 文档概述

### 1.1 文档目的

本文档作为 Model Context Protocol Agent（以下简称 MCP Agent）的技术架构设计指南，为开发团队提供详细的系统设计、模块划分、接口规范及实现指导。

### 1.2 目标读者

- 后端开发工程师
- 插件开发者
- 系统架构师
- 技术负责人

### 1.3 文档范围

本文档涵盖 MCP Agent 的完整架构设计，包括：

- 系统分层架构
- 核心模块设计
- SDK 设计
- 通信机制
- 安全机制
- 扩展机制

---

## 2. 架构概览

### 2.1 系统定位

MCP Agent 是运行在 Minecraft 服务端的插件，作为 MCP 能力的提供者与执行终端，负责：

1. **能力暴露**：将 Minecraft 服务端能力抽象为标准 MCP 能力
2. **能力执行**：接收并执行来自 Gateway 的能力调用请求
3. **事件推送**：将服务端事件实时推送至 Gateway
4. **SDK 支持**：为第三方插件提供 MCP 能力注册机制

### 2.2 整体架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           MCP Agent (Paper Plugin)                           │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                        API Layer (对外接口层)                          │  │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐   │  │
│  │  │  Provider SDK   │  │  Event Emitter  │  │   Admin Commands    │   │  │
│  │  │   (注解驱动)     │  │   (事件发射器)   │  │    (管理命令)        │   │  │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────────┘   │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                      │                                       │
│  ┌───────────────────────────────────▼───────────────────────────────────┐  │
│  │                      Core Layer (核心业务层)                           │  │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐   │  │
│  │  │ Capability      │  │  Execution      │  │   Event             │   │  │
│  │  │ Registry        │  │  Engine         │  │   Dispatcher        │   │  │
│  │  │ (能力注册中心)   │  │  (执行引擎)      │  │   (事件分发器)       │   │  │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────────┘   │  │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐   │  │
│  │  │ Schema          │  │  Permission     │  │   Audit             │   │  │
│  │  │ Validator       │  │  Checker        │  │   Logger            │   │  │
│  │  │ (Schema校验器)  │  │  (权限校验器)    │  │   (审计日志器)       │   │  │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────────┘   │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                      │                                       │
│  ┌───────────────────────────────────▼───────────────────────────────────┐  │
│  │                    Provider Layer (能力提供层)                         │  │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐   │  │
│  │  │ World Provider  │  │ Player Provider │  │  Chat Provider      │   │  │
│  │  │ (世界能力)       │  │ (玩家能力)       │  │  (聊天能力)          │   │  │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────────┘   │  │
│  │  ┌─────────────────┐  ┌─────────────────┐                             │  │
│  │  │ Entity Provider │  │ System Provider │                             │  │
│  │  │ (实体能力)       │  │ (系统能力)       │                             │  │
│  │  └─────────────────┘  └─────────────────┘                             │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐ │  │
│  │  │              Third-Party Providers (第三方能力提供者)             │ │  │
│  │  └─────────────────────────────────────────────────────────────────┘ │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                      │                                       │
│  ┌───────────────────────────────────▼───────────────────────────────────┐  │
│  │                   Safety Layer (安全保障层)                            │  │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐   │  │
│  │  │ Risk Evaluator  │  │ Snapshot        │  │   Rollback          │   │  │
│  │  │ (风险评估器)     │  │ Manager         │  │   Handler           │   │  │
│  │  │                 │  │ (快照管理器)     │  │   (回滚处理器)       │   │  │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────────┘   │  │
│  │  ┌─────────────────┐  ┌─────────────────┐                            │  │
│  │  │ Rate Limiter    │  │ Sandbox         │                            │  │
│  │  │ (限流器)        │  │ Executor        │                            │  │
│  │  │                 │  │ (沙箱执行器)     │                            │  │
│  │  └─────────────────┘  └─────────────────┘                            │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                      │                                       │
│  ┌───────────────────────────────────▼───────────────────────────────────┐  │
│  │                 Communication Layer (通信层)                           │  │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐   │  │
│  │  │ WebSocket       │  │ Message         │  │   Connection        │   │  │
│  │  │ Client          │  │ Codec           │  │   Manager           │   │  │
│  │  │ (WS客户端)       │  │ (消息编解码器)   │  │   (连接管理器)       │   │  │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────────┘   │  │
│  │  ┌─────────────────┐  ┌─────────────────┐                            │  │
│  │  │ Heartbeat       │  │ Reconnect       │                            │  │
│  │  │ Handler         │  │ Strategy        │                            │  │
│  │  │ (心跳处理器)     │  │ (重连策略)       │                            │  │
│  │  └─────────────────┘  └─────────────────┘                            │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                      │                                       │
│  ┌───────────────────────────────────▼───────────────────────────────────┐  │
│  │                Infrastructure Layer (基础设施层)                       │  │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐   │  │
│  │  │ Config          │  │ Storage         │  │   Scheduler         │   │  │
│  │  │ Manager         │  │ (本地存储)       │  │   (调度器)           │   │  │
│  │  │ (配置管理器)     │  │                 │  │                     │   │  │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────────┘   │  │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐   │  │
│  │  │ Logger          │  │ Metrics         │  │   I18n              │   │  │
│  │  │ (日志器)        │  │ Collector       │  │   (国际化)           │   │  │
│  │  │                 │  │ (指标收集器)     │  │                     │   │  │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────────┘   │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
├─────────────────────────────────────────────────────────────────────────────┤
│                        Minecraft Server (Paper/Spigot)                       │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.3 架构设计原则

| 原则 | 说明 |
|------|------|
| **分层解耦** | 各层职责明确，通过接口交互，降低耦合度 |
| **插件化设计** | 核心功能与扩展功能分离，支持热插拔 |
| **契约优先** | 严格遵循 Contract Layer 规范，保证互操作性 |
| **安全优先** | 安全机制贯穿各层，实现纵深防御 |
| **可观测性** | 全链路日志、指标、审计，支持问题追溯 |
| **高可用** | 断线重连、降级容错，保证服务稳定 |

---

## 3. 分层详细设计

### 3.1 API Layer（对外接口层）

#### 3.1.1 职责

- 提供 SDK 注解与接口供第三方插件使用
- 提供事件发射器供内部模块使用
- 提供管理命令供服务器管理员使用

#### 3.1.2 Provider SDK

```java
// SDK 核心接口定义
public interface McpProviderRegistry {
    /**
     * 注册能力提供者
     */
    void register(Object provider);
    
    /**
     * 注销能力提供者
     */
    void unregister(Object provider);
    
    /**
     * 注销指定插件的所有提供者
     */
    void unregisterAll(Plugin plugin);
    
    /**
     * 获取已注册的能力清单
     */
    List<CapabilityManifest> getCapabilities();
}
```

#### 3.1.3 Event Emitter

```java
public interface McpEventEmitter {
    /**
     * 发射事件到所有订阅者
     */
    void emit(String eventId, Object eventData);
    
    /**
     * 发射事件到指定订阅者
     */
    void emit(String eventId, Object eventData, Predicate<Subscription> filter);
}
```

#### 3.1.4 Admin Commands

| 命令 | 说明 | 权限 |
|------|------|------|
| `/mcp status` | 查看 Agent 状态 | `mcp.admin.status` |
| `/mcp reload` | 重载配置 | `mcp.admin.reload` |
| `/mcp providers` | 列出所有 Provider | `mcp.admin.providers` |
| `/mcp capabilities` | 列出所有能力 | `mcp.admin.capabilities` |
| `/mcp test <capability>` | 测试能力执行 | `mcp.admin.test` |
| `/mcp disconnect` | 断开 Gateway 连接 | `mcp.admin.connection` |
| `/mcp connect` | 连接 Gateway | `mcp.admin.connection` |

---

### 3.2 Core Layer（核心业务层）

#### 3.2.1 Capability Registry（能力注册中心）

```
┌─────────────────────────────────────────────────────────────┐
│                    Capability Registry                       │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────┐    │
│  │              Capability Index                        │    │
│  │  ┌───────────────────┬─────────────────────────┐    │    │
│  │  │   Capability ID   │   CapabilityDescriptor  │    │    │
│  │  ├───────────────────┼─────────────────────────┤    │    │
│  │  │ world.time.get    │ { handler, manifest }   │    │    │
│  │  │ player.teleport   │ { handler, manifest }   │    │    │
│  │  │ player.economy.balance   │ { handler, manifest }   │    │    │
│  │  └───────────────────┴─────────────────────────┘    │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              Provider Index                          │    │
│  │  ┌───────────────────┬─────────────────────────┐    │    │
│  │  │   Provider ID     │   ProviderDescriptor    │    │    │
│  │  ├───────────────────┼─────────────────────────┤    │    │
│  │  │ mcp-agent-core    │ { instance, caps[] }    │    │    │
│  │  │ ext.shopkeeper    │ { instance, caps[] }    │    │    │
│  │  └───────────────────┴─────────────────────────┘    │    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

**核心数据结构：**

```java
public class CapabilityDescriptor {
    private final String id;
    private final String version;
    private final CapabilityType type;
    private final CapabilityManifest manifest;
    private final Object providerInstance;
    private final Method handlerMethod;
    private final ParameterSchema parameterSchema;
    private final ReturnSchema returnSchema;
    private final RiskLevel riskLevel;
    private final List<String> permissions;
}

public class ProviderDescriptor {
    private final String id;
    private final String name;
    private final String version;
    private final Object instance;
    private final Plugin ownerPlugin;
    private final List<CapabilityDescriptor> capabilities;
}
```

#### 3.2.2 Execution Engine（执行引擎）

执行引擎负责处理能力调用请求，采用责任链模式实现：

```
Request → [RateLimiter] → [PermissionChecker] → [SchemaValidator] 
        → [RiskEvaluator] → [SnapshotCreator] → [CapabilityInvoker] 
        → [AuditLogger] → Response
```

**执行流程：**

```java
public class ExecutionEngine {
    private final List<ExecutionInterceptor> interceptors;
    
    public CompletableFuture<McpResponse> execute(McpRequest request) {
        ExecutionContext context = new ExecutionContext(request);
        
        // 构建执行链
        ExecutionChain chain = new ExecutionChain(interceptors, 
            () -> invokeCapability(context));
        
        return chain.proceed(context)
            .thenApply(result -> buildResponse(context, result))
            .exceptionally(ex -> buildErrorResponse(context, ex));
    }
}
```

**执行拦截器接口：**

```java
public interface ExecutionInterceptor {
    /**
     * 执行前拦截
     * @return true 继续执行，false 中断执行
     */
    boolean preHandle(ExecutionContext context) throws McpException;
    
    /**
     * 执行后处理
     */
    void postHandle(ExecutionContext context, Object result);
    
    /**
     * 异常处理
     */
    void onError(ExecutionContext context, Throwable ex);
    
    /**
     * 执行顺序
     */
    int getOrder();
}
```

#### 3.2.3 Event Dispatcher（事件分发器）

```java
public class EventDispatcher {
    // 事件订阅索引：eventId -> List<Subscription>
    private final Map<String, List<Subscription>> subscriptions;
    
    // Minecraft 事件监听器注册
    private final Map<Class<? extends Event>, EventListener<?>> eventListeners;
    
    /**
     * 添加事件订阅
     */
    public String subscribe(String eventId, SubscriptionFilter filter, 
                           String subscriberId) {
        // 生成订阅 ID
        // 注册订阅关系
        // 若首次订阅该事件类型，注册 Bukkit 监听器
    }
    
    /**
     * 取消订阅
     */
    public void unsubscribe(String subscriptionId) {
        // 移除订阅关系
        // 若无订阅者，注销 Bukkit 监听器
    }
    
    /**
     * 分发事件（由 Bukkit 监听器调用）
     */
    void dispatch(String eventId, Object eventData) {
        List<Subscription> subs = subscriptions.get(eventId);
        for (Subscription sub : subs) {
            if (sub.getFilter().matches(eventData)) {
                communicationLayer.pushEvent(sub.getSubscriberId(), 
                    eventId, eventData);
            }
        }
    }
}
```

#### 3.2.4 Schema Validator（Schema 校验器）

```java
public class SchemaValidator {
    private final JsonSchemaFactory schemaFactory;
    private final Map<String, JsonSchema> schemaCache;
    
    /**
     * 校验请求参数
     */
    public ValidationResult validateParameters(String capabilityId, 
                                               JsonNode parameters) {
        CapabilityDescriptor descriptor = registry.get(capabilityId);
        JsonSchema schema = getOrCreateSchema(descriptor.getParameterSchema());
        
        Set<ValidationMessage> errors = schema.validate(parameters);
        return new ValidationResult(errors.isEmpty(), errors);
    }
    
    /**
     * 校验返回值
     */
    public ValidationResult validateReturn(String capabilityId, 
                                           JsonNode returnValue) {
        // 类似实现
    }
}
```

#### 3.2.5 Permission Checker（权限校验器）

```java
public class PermissionChecker {
    /**
     * 校验调用权限
     * 采用双重校验：Gateway 侧 RBAC + Agent 侧二次校验
     */
    public boolean checkPermission(ExecutionContext context) {
        CapabilityDescriptor cap = context.getCapability();
        CallerInfo caller = context.getCaller();
        
        // 1. 校验能力所需权限
        for (String permission : cap.getPermissions()) {
            if (!caller.hasPermission(permission)) {
                return false;
            }
        }
        
        // 2. 校验风险等级对应的角色要求
        RiskLevel risk = cap.getRiskLevel();
        if (!caller.hasRole(risk.getRequiredRole())) {
            return false;
        }
        
        return true;
    }
}
```

#### 3.2.6 Audit Logger（审计日志器）

```java
public class AuditLogger {
    private final BlockingQueue<AuditEvent> eventQueue;
    private final AuditEventWriter writer;
    
    /**
     * 记录审计事件（异步）
     */
    public void log(AuditEvent event) {
        eventQueue.offer(event);
    }
    
    /**
     * 构建审计事件
     */
    public AuditEvent buildEvent(ExecutionContext context, Object result) {
        return AuditEvent.builder()
            .id(UUID.randomUUID().toString())
            .timestamp(Instant.now())
            .eventType(AuditEventType.INVOKE)
            .capabilityId(context.getCapabilityId())
            .caller(context.getCaller())
            .request(sanitize(context.getRequest()))  // 脱敏
            .response(sanitize(result))
            .riskLevel(context.getRiskLevel())
            .metadata(buildMetadata(context))
            .build();
    }
}
```

---

### 3.3 Provider Layer（能力提供层）

#### 3.3.1 内置 Provider 设计

##### World Provider

```java
@McpProvider(
    id = "mcp-agent-core",
    name = "MCP Core Agent",
    version = "1.0.0"
)
public class WorldProvider {

    @McpContext(
        id = "world.time.get",
        name = "获取世界时间",
        permissions = {"mcp.context.world.time"}
    )
    public WorldTimeResult getWorldTime(
        @Param(name = "worldName", required = true) String worldName
    ) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new McpBusinessException(
                ErrorCode.WORLD_NOT_FOUND, 
                "世界不存在: " + worldName
            );
        }
        
        long time = world.getTime();
        return WorldTimeResult.builder()
            .worldName(worldName)
            .time(time)
            .fullTime(world.getFullTime())
            .day((int) (world.getFullTime() / 24000))
            .phase(TimePhase.fromTick(time))
            .build();
    }
    
    @McpAction(
        id = "world.time.set",
        name = "设置世界时间",
        risk = RiskLevel.HIGH,
        snapshotRequired = true,
        permissions = {"mcp.action.world.time"}
    )
    public SetTimeResult setWorldTime(
        @Param(name = "worldName", required = true) String worldName,
        @Param(name = "time", required = true) long time,
        @Param(name = "reason") String reason
    ) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new McpBusinessException(ErrorCode.WORLD_NOT_FOUND);
        }
        
        long previousTime = world.getTime();
        world.setTime(time);
        
        return SetTimeResult.builder()
            .success(true)
            .previousTime(previousTime)
            .newTime(time)
            .build();
    }
    
    @McpContext(
        id = "world.weather.get",
        name = "获取天气状态",
        permissions = {"mcp.context.world.weather"}
    )
    public WeatherResult getWeather(
        @Param(name = "worldName", required = true) String worldName
    ) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new McpBusinessException(ErrorCode.WORLD_NOT_FOUND);
        }
        
        WeatherType type = world.hasStorm() 
            ? (world.isThundering() ? WeatherType.THUNDER : WeatherType.RAIN)
            : WeatherType.CLEAR;
            
        return WeatherResult.builder()
            .worldName(worldName)
            .type(type)
            .duration(world.getWeatherDuration())
            .build();
    }
    
    @McpAction(
        id = "world.weather.set",
        name = "设置天气",
        risk = RiskLevel.MEDIUM,
        permissions = {"mcp.action.world.weather"}
    )
    public SetWeatherResult setWeather(
        @Param(name = "worldName", required = true) String worldName,
        @Param(name = "type", required = true) WeatherType type,
        @Param(name = "duration") Integer duration
    ) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new McpBusinessException(ErrorCode.WORLD_NOT_FOUND);
        }
        
        WeatherType previousType = /* 获取当前天气 */;
        
        // 设置天气
        switch (type) {
            case CLEAR:
                world.setStorm(false);
                world.setThundering(false);
                break;
            case RAIN:
                world.setStorm(true);
                world.setThundering(false);
                break;
            case THUNDER:
                world.setStorm(true);
                world.setThundering(true);
                break;
        }
        
        if (duration != null) {
            world.setWeatherDuration(duration);
        }
        
        return SetWeatherResult.builder()
            .success(true)
            .previousType(previousType)
            .newType(type)
            .build();
    }
    
    @McpContext(
        id = "world.rule.get",
        name = "获取游戏规则",
        permissions = {"mcp.context.world.rule"}
    )
    public GameRuleResult getGameRule(
        @Param(name = "worldName", required = true) String worldName,
        @Param(name = "rule") String rule
    ) {
        // 实现
    }
    
    @McpAction(
        id = "world.rule.set",
        name = "设置游戏规则",
        risk = RiskLevel.HIGH,
        rollbackSupported = true,
        permissions = {"mcp.action.world.rule"}
    )
    public SetGameRuleResult setGameRule(
        @Param(name = "worldName", required = true) String worldName,
        @Param(name = "rule", required = true) String rule,
        @Param(name = "value", required = true) String value
    ) {
        // 实现
    }
    
    @McpContext(
        id = "world.tps.get",
        name = "获取 TPS",
        permissions = {"mcp.context.world.tps"}
    )
    public TpsResult getTps() {
        double[] tps = Bukkit.getTPS();
        return TpsResult.builder()
            .tps1m(tps[0])
            .tps5m(tps[1])
            .tps15m(tps[2])
            .mspt(Bukkit.getAverageTickTime())
            .build();
    }
}
```

##### Player Provider

```java
@McpProvider(
    id = "mcp-agent-core",
    name = "MCP Core Agent",
    version = "1.0.0"
)
public class PlayerProvider {

    @McpContext(
        id = "player.list",
        name = "获取在线玩家列表",
        permissions = {"mcp.context.player.list"}
    )
    public PlayerListResult getPlayerList(
        @Param(name = "pagination") PaginationParam pagination
    ) {
        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        
        List<PlayerInfo> playerInfos = players.stream()
            .map(this::toPlayerInfo)
            .collect(Collectors.toList());
        
        return paginate(playerInfos, pagination);
    }
    
    @McpContext(
        id = "player.info.get",
        name = "获取玩家信息",
        permissions = {"mcp.context.player.info"}
    )
    public PlayerDetailResult getPlayerInfo(
        @Param(name = "playerName") String playerName,
        @Param(name = "uuid") String uuid
    ) {
        Player player = resolvePlayer(playerName, uuid);
        if (player == null) {
            throw new McpBusinessException(ErrorCode.PLAYER_NOT_FOUND);
        }
        
        return PlayerDetailResult.builder()
            .name(player.getName())
            .uuid(player.getUniqueId().toString())
            .displayName(player.getDisplayName())
            .location(toLocationDto(player.getLocation()))
            .health(player.getHealth())
            .maxHealth(player.getMaxHealth())
            .foodLevel(player.getFoodLevel())
            .level(player.getLevel())
            .exp(player.getExp())
            .gameMode(player.getGameMode().name())
            .isOp(player.isOp())
            .isFlying(player.isFlying())
            .ping(player.getPing())
            .firstPlayed(Instant.ofEpochMilli(player.getFirstPlayed()))
            .lastPlayed(Instant.ofEpochMilli(player.getLastPlayed()))
            .build();
    }
    
    @McpAction(
        id = "player.teleport",
        name = "传送玩家",
        risk = RiskLevel.MEDIUM,
        rollbackSupported = true,
        permissions = {"mcp.action.player.teleport"}
    )
    public TeleportResult teleportPlayer(
        @Param(name = "playerName", required = true) String playerName,
        @Param(name = "location", required = true) LocationParam location,
        @Param(name = "reason") String reason
    ) {
        Player player = Bukkit.getPlayer(playerName);
        if (player == null) {
            throw new McpBusinessException(ErrorCode.PLAYER_OFFLINE);
        }
        
        Location previousLocation = player.getLocation().clone();
        Location targetLocation = location.toBukkitLocation();
        
        // 主线程执行传送
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            boolean success = player.teleport(targetLocation);
            future.complete(success);
        });
        
        boolean success = future.get(5, TimeUnit.SECONDS);
        
        return TeleportResult.builder()
            .success(success)
            .previousLocation(toLocationDto(previousLocation))
            .newLocation(toLocationDto(targetLocation))
            .build();
    }
    
    @McpAction(
        id = "player.kick",
        name = "踢出玩家",
        risk = RiskLevel.MEDIUM,
        permissions = {"mcp.action.player.kick"}
    )
    public KickResult kickPlayer(
        @Param(name = "playerName", required = true) String playerName,
        @Param(name = "reason") String reason
    ) {
        Player player = Bukkit.getPlayer(playerName);
        if (player == null) {
            throw new McpBusinessException(ErrorCode.PLAYER_OFFLINE);
        }
        
        String kickReason = reason != null ? reason : "Kicked by administrator";
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.kick(Component.text(kickReason));
        });
        
        return KickResult.builder()
            .success(true)
            .playerName(playerName)
            .reason(kickReason)
            .build();
    }
    
    @McpAction(
        id = "player.mute",
        name = "禁言玩家",
        risk = RiskLevel.MEDIUM,
        rollbackSupported = true,
        permissions = {"mcp.action.player.mute"}
    )
    public MuteResult mutePlayer(
        @Param(name = "playerName", required = true) String playerName,
        @Param(name = "duration") Integer durationSeconds,
        @Param(name = "reason") String reason
    ) {
        // 实现
    }
    
    @McpAction(
        id = "player.ban",
        name = "封禁玩家",
        risk = RiskLevel.CRITICAL,
        snapshotRequired = true,
        permissions = {"mcp.action.player.ban"}
    )
    public BanResult banPlayer(
        @Param(name = "playerName", required = true) String playerName,
        @Param(name = "duration") Integer durationSeconds,
        @Param(name = "reason") String reason
    ) {
        // 实现
    }
}
```

##### System Provider

```java
@McpProvider(
    id = "mcp-agent-core",
    name = "MCP Core Agent",
    version = "1.0.0"
)
public class SystemProvider {

    @McpAction(
        id = "system.backup",
        name = "创建备份",
        risk = RiskLevel.HIGH,
        permissions = {"mcp.action.system.backup"}
    )
    public BackupResult createBackup(
        @Param(name = "worlds") List<String> worlds,
        @Param(name = "description") String description
    ) {
        // 实现世界备份逻辑
    }
    
    @McpAction(
        id = "system.restore",
        name = "恢复备份",
        risk = RiskLevel.CRITICAL,
        snapshotRequired = true,
        permissions = {"mcp.action.system.restore"}
    )
    public RestoreResult restoreBackup(
        @Param(name = "backupId", required = true) String backupId,
        @Param(name = "confirm", required = true) boolean confirm
    ) {
        // 实现备份恢复逻辑
    }
    
    @McpAction(
        id = "system.reload",
        name = "重载插件",
        risk = RiskLevel.HIGH,
        permissions = {"mcp.action.system.reload"}
    )
    public ReloadResult reloadPlugin(
        @Param(name = "pluginName") String pluginName
    ) {
        // 实现插件重载逻辑
    }
}
```

##### Entity Provider

```java
@McpProvider(
    id = "mcp-agent-core",
    name = "MCP Core Agent",
    version = "1.0.0"
)
public class EntityProvider {

    @McpContext(
        id = "entity.list",
        name = "列出实体",
        permissions = {"mcp.context.entity.list"}
    )
    public EntityListResult listEntities(
        @Param(name = "worldName", required = true) String worldName,
        @Param(name = "type") String entityType,
        @Param(name = "location") LocationParam center,
        @Param(name = "radius") Double radius,
        @Param(name = "pagination") PaginationParam pagination
    ) {
        // 实现
    }
    
    @McpAction(
        id = "entity.remove",
        name = "移除实体",
        risk = RiskLevel.HIGH,
        snapshotRequired = true,
        permissions = {"mcp.action.entity.remove"}
    )
    public RemoveEntityResult removeEntities(
        @Param(name = "worldName", required = true) String worldName,
        @Param(name = "type") String entityType,
        @Param(name = "location") LocationParam center,
        @Param(name = "radius") Double radius,
        @Param(name = "excludePlayers") boolean excludePlayers
    ) {
        // 实现
    }
}
```

#### 3.3.2 Provider 注册流程

```
┌─────────────────────────────────────────────────────────────────────┐
│                     Provider Registration Flow                       │
└─────────────────────────────────────────────────────────────────────┘

  Plugin.onEnable()
        │
        ▼
  ┌─────────────────┐
  │ Get Registry    │
  │ from ServiceMgr │
  └────────┬────────┘
           │
           ▼
  ┌─────────────────┐
  │ Create Provider │
  │   Instance      │
  └────────┬────────┘
           │
           ▼
  ┌─────────────────┐     ┌──────────────────────────────────────┐
  │ registry.       │────▶│ 1. Scan @McpProvider annotation      │
  │ register(obj)   │     │ 2. Scan @McpContext/@McpAction/Event │
  └────────┬────────┘     │ 3. Extract parameter schemas         │
           │              │ 4. Build CapabilityManifest          │
           │              │ 5. Create CapabilityDescriptor       │
           │              │ 6. Register to capability index      │
           │              │ 7. Register to provider index        │
           │              └──────────────────────────────────────┘
           ▼
  ┌─────────────────┐
  │ Notify Gateway  │
  │ (capability_    │
  │  update)        │
  └─────────────────┘
```

---

### 3.4 Safety Layer（安全保障层）

#### 3.4.1 Risk Evaluator（风险评估器）

```java
public class RiskEvaluator {
    
    /**
     * 评估操作风险
     */
    public RiskAssessment evaluate(ExecutionContext context) {
        CapabilityDescriptor cap = context.getCapability();
        RiskLevel baseLevel = cap.getRiskLevel();
        
        RiskAssessment.Builder builder = RiskAssessment.builder()
            .baseLevel(baseLevel);
        
        // 1. 基于参数的动态风险评估
        if (isHighImpactParameters(context)) {
            builder.escalate("参数影响范围较大");
        }
        
        // 2. 基于调用频率的风险评估
        if (isAnomalousCallPattern(context)) {
            builder.escalate("调用频率异常");
        }
        
        // 3. 基于调用者历史的风险评估
        if (hasRecentFailures(context.getCaller())) {
            builder.escalate("调用者近期存在失败记录");
        }
        
        return builder.build();
    }
    
    /**
     * 判断是否需要审批
     */
    public boolean requiresApproval(RiskAssessment assessment) {
        return assessment.getFinalLevel().ordinal() >= RiskLevel.HIGH.ordinal();
    }
    
    /**
     * 判断是否需要快照
     */
    public boolean requiresSnapshot(RiskAssessment assessment) {
        return assessment.getFinalLevel().ordinal() >= RiskLevel.HIGH.ordinal()
            || assessment.getCapability().isSnapshotRequired();
    }
}
```

#### 3.4.2 Snapshot Manager（快照管理器）

```java
public class SnapshotManager {
    private final Path snapshotDir;
    private final SnapshotStorage storage;
    
    /**
     * 创建执行前快照
     */
    public Snapshot createSnapshot(ExecutionContext context) {
        SnapshotScope scope = determineScope(context);
        
        Snapshot snapshot = Snapshot.builder()
            .id(UUID.randomUUID().toString())
            .timestamp(Instant.now())
            .scope(scope)
            .requestId(context.getRequestId())
            .capabilityId(context.getCapabilityId())
            .build();
        
        switch (scope) {
            case PLAYER:
                snapshotPlayer(snapshot, context);
                break;
            case WORLD:
                snapshotWorld(snapshot, context);
                break;
            case ECONOMY:
                snapshotEconomy(snapshot, context);
                break;
            case FULL:
                snapshotFull(snapshot, context);
                break;
        }
        
        storage.save(snapshot);
        return snapshot;
    }
    
    /**
     * 加载快照
     */
    public Snapshot loadSnapshot(String snapshotId) {
        return storage.load(snapshotId);
    }
    
    /**
     * 清理过期快照
     */
    public void cleanupExpiredSnapshots(Duration retention) {
        storage.deleteOlderThan(Instant.now().minus(retention));
    }
    
    private void snapshotPlayer(Snapshot snapshot, ExecutionContext context) {
        String playerName = context.getParameterAs("playerName", String.class);
        Player player = Bukkit.getPlayer(playerName);
        if (player != null) {
            PlayerSnapshot playerSnapshot = PlayerSnapshot.builder()
                .name(player.getName())
                .uuid(player.getUniqueId())
                .location(player.getLocation().clone())
                .inventory(player.getInventory().getContents().clone())
                .health(player.getHealth())
                .foodLevel(player.getFoodLevel())
                .exp(player.getExp())
                .level(player.getLevel())
                .gameMode(player.getGameMode())
                .build();
            snapshot.setPlayerSnapshot(playerSnapshot);
        }
    }
}
```

#### 3.4.3 Rollback Handler（回滚处理器）

```java
public class RollbackHandler {
    private final SnapshotManager snapshotManager;
    private final AuditLogger auditLogger;
    
    /**
     * 执行回滚
     */
    public RollbackResult rollback(String snapshotId, String reason) {
        Snapshot snapshot = snapshotManager.loadSnapshot(snapshotId);
        if (snapshot == null) {
            throw new McpException(ErrorCode.ROLLBACK_FAILED, "快照不存在");
        }
        
        RollbackResult.Builder result = RollbackResult.builder()
            .snapshotId(snapshotId)
            .timestamp(Instant.now());
        
        try {
            switch (snapshot.getScope()) {
                case PLAYER:
                    rollbackPlayer(snapshot);
                    break;
                case WORLD:
                    rollbackWorld(snapshot);
                    break;
                case ECONOMY:
                    rollbackEconomy(snapshot);
                    break;
                case FULL:
                    rollbackFull(snapshot);
                    break;
            }
            
            result.success(true);
            
            // 记录回滚审计
            auditLogger.logRollback(snapshot, reason);
            
        } catch (Exception e) {
            result.success(false).error(e.getMessage());
        }
        
        return result.build();
    }
    
    private void rollbackPlayer(Snapshot snapshot) {
        PlayerSnapshot ps = snapshot.getPlayerSnapshot();
        Player player = Bukkit.getPlayer(ps.getUuid());
        
        if (player != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.teleport(ps.getLocation());
                player.getInventory().setContents(ps.getInventory());
                player.setHealth(ps.getHealth());
                player.setFoodLevel(ps.getFoodLevel());
                player.setExp(ps.getExp());
                player.setLevel(ps.getLevel());
                player.setGameMode(ps.getGameMode());
            });
        }
    }
}
```

#### 3.4.4 Rate Limiter（限流器）

```java
public class RateLimiter {
    // 使用令牌桶算法
    private final Map<String, TokenBucket> buckets;
    private final RateLimitConfig config;
    
    /**
     * 尝试获取执行许可
     */
    public boolean tryAcquire(String capabilityId, String callerId) {
        String key = buildKey(capabilityId, callerId);
        TokenBucket bucket = getOrCreateBucket(key, capabilityId);
        return bucket.tryConsume(1);
    }
    
    /**
     * 获取剩余配额
     */
    public RateLimitStatus getStatus(String capabilityId, String callerId) {
        String key = buildKey(capabilityId, callerId);
        TokenBucket bucket = buckets.get(key);
        
        if (bucket == null) {
            RateLimitRule rule = config.getRule(capabilityId);
            return new RateLimitStatus(rule.getRequests(), rule.getRequests());
        }
        
        return new RateLimitStatus(bucket.getCapacity(), bucket.getAvailable());
    }
    
    private TokenBucket getOrCreateBucket(String key, String capabilityId) {
        return buckets.computeIfAbsent(key, k -> {
            RateLimitRule rule = config.getRule(capabilityId);
            return new TokenBucket(
                rule.getRequests(),
                rule.getPeriod()
            );
        });
    }
}
```

---

### 3.5 Communication Layer（通信层）

#### 3.5.1 整体通信架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                      Communication Layer                            │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   ┌───────────────┐    ┌───────────────┐    ┌───────────────┐       │
│   │   WebSocket   │    │   Message     │    │  Connection   │       │
│   │    Client     │◀──▶│    Codec      │◀──▶│   Manager   │       │
│   └───────┬───────┘    └───────────────┘    └───────┬───────┘       │
│           │                                         │               │
│           ▼                                         ▼               │
│   ┌───────────────┐    ┌───────────────┐    ┌───────────────┐       │
│   │   Heartbeat   │    │   Message     │    │   Reconnect   │       │
│   │   Handler     │    │   Router      │    │   Strategy    │       │
│   └───────────────┘    └───────┬───────┘    └───────────────┘       │
│                                │                                    │
│                                ▼                                    │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │                    Message Handlers                         │   │
│   │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐     │   │
│   │  │ Request  │  │ Response │  │  Event   │  │ Control  │     │   │
│   │  │ Handler  │  │ Handler  │  │ Handler  │  │ Handler  │     │   │
│   │  └──────────┘  └──────────┘  └──────────┘  └──────────┘     │   │
│   └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

#### 3.5.2 WebSocket Client

```java
public class GatewayWebSocketClient {
    private final URI gatewayUri;
    private final ConnectionManager connectionManager;
    private final MessageCodec codec;
    private final MessageRouter router;
    private WebSocket webSocket;
    
    /**
     * 连接到 Gateway
     */
    public CompletableFuture<Void> connect() {
        return HttpClient.newHttpClient()
            .newWebSocketBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .buildAsync(gatewayUri, new WebSocketListener())
            .thenAccept(ws -> {
                this.webSocket = ws;
                connectionManager.onConnected();
                performHandshake();
            });
    }
    
    /**
     * 发送消息
     */
    public CompletableFuture<Void> send(McpMessage message) {
        String json = codec.encode(message);
        return webSocket.sendText(json, true)
            .thenAccept(ws -> {});
    }
    
    /**
     * 发送请求并等待响应
     */
    public <T> CompletableFuture<T> request(McpRequest request, Class<T> responseType) {
        CompletableFuture<T> future = new CompletableFuture<>();
        pendingRequests.put(request.getId(), new PendingRequest<>(future, responseType));
        
        send(request).exceptionally(ex -> {
            pendingRequests.remove(request.getId());
            future.completeExceptionally(ex);
            return null;
        });
        
        return future.orTimeout(30, TimeUnit.SECONDS);
    }
    
    private class WebSocketListener implements WebSocket.Listener {
        private StringBuilder buffer = new StringBuilder();
        
        @Override
        public CompletionStage<?> onText(WebSocket webSocket, 
                                         CharSequence data, 
                                         boolean last) {
            buffer.append(data);
            if (last) {
                String message = buffer.toString();
                buffer = new StringBuilder();
                handleMessage(message);
            }
            webSocket.request(1);
            return null;
        }
        
        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, 
                                          int statusCode, 
                                          String reason) {
            connectionManager.onDisconnected(statusCode, reason);
            return null;
        }
        
        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            connectionManager.onError(error);
        }
    }
    
    private void handleMessage(String json) {
        McpMessage message = codec.decode(json);
        router.route(message);
    }
}
```

#### 3.5.3 Message Codec（消息编解码器）

```java
public class MessageCodec {
    private final ObjectMapper objectMapper;
    
    public MessageCodec() {
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
    
    /**
     * 编码消息
     */
    public String encode(McpMessage message) {
        try {
            MessageFrame frame = MessageFrame.builder()
                .id(message.getId())
                .type(message.getType())
                .timestamp(Instant.now())
                .correlationId(message.getCorrelationId())
                .payload(message.getPayload())
                .build();
            return objectMapper.writeValueAsString(frame);
        } catch (JsonProcessingException e) {
            throw new CodecException("消息编码失败", e);
        }
    }
    
    /**
     * 解码消息
     */
    public McpMessage decode(String json) {
        try {
            MessageFrame frame = objectMapper.readValue(json, MessageFrame.class);
            return switch (frame.getType()) {
                case "request" -> decodeRequest(frame);
                case "response" -> decodeResponse(frame);
                case "event" -> decodeEvent(frame);
                case "heartbeat" -> decodeHeartbeat(frame);
                default -> throw new CodecException("未知消息类型: " + frame.getType());
            };
        } catch (JsonProcessingException e) {
            throw new CodecException("消息解码失败", e);
        }
    }
}
```

#### 3.5.4 Connection Manager（连接管理器）

```java
public class ConnectionManager {
    private final ReconnectStrategy reconnectStrategy;
    private final AtomicReference<ConnectionState> state;
    private final ScheduledExecutorService scheduler;
    
    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        RECONNECTING
    }
    
    /**
     * 处理连接成功
     */
    public void onConnected() {
        state.set(ConnectionState.CONNECTED);
        reconnectStrategy.reset();
        
        // 发送注册消息
        sendRegistration();
        
        // 启动心跳
        startHeartbeat();
    }
    
    /**
     * 处理断开连接
     */
    public void onDisconnected(int statusCode, String reason) {
        state.set(ConnectionState.DISCONNECTED);
        stopHeartbeat();
        
        if (shouldReconnect(statusCode)) {
            scheduleReconnect();
        }
    }
    
    /**
     * 处理连接错误
     */
    public void onError(Throwable error) {
        logger.error("WebSocket 连接错误", error);
        
        if (state.get() == ConnectionState.CONNECTED) {
            onDisconnected(-1, error.getMessage());
        }
    }
    
    /**
     * 调度重连
     */
    private void scheduleReconnect() {
        state.set(ConnectionState.RECONNECTING);
        
        Duration delay = reconnectStrategy.nextDelay();
        if (delay == null) {
            logger.error("达到最大重连次数，停止重连");
            return;
        }
        
        logger.info("将在 {} 后尝试重连", delay);
        scheduler.schedule(() -> {
            wsClient.connect()
                .exceptionally(ex -> {
                    logger.error("重连失败", ex);
                    scheduleReconnect();
                    return null;
                });
        }, delay.toMillis(), TimeUnit.MILLISECONDS);
    }
}
```

#### 3.5.5 Reconnect Strategy（重连策略）

```java
public class ExponentialBackoffReconnectStrategy implements ReconnectStrategy {
    private final Duration initialDelay;
    private final Duration maxDelay;
    private final double multiplier;
    private final int maxRetries;
    
    private int retryCount = 0;
    private Duration currentDelay;
    
    public ExponentialBackoffReconnectStrategy() {
        this.initialDelay = Duration.ofSeconds(1);
        this.maxDelay = Duration.ofMinutes(5);
        this.multiplier = 2.0;
        this.maxRetries = 10;
        this.currentDelay = initialDelay;
    }
    
    @Override
    public Duration nextDelay() {
        if (retryCount >= maxRetries) {
            return null; // 停止重连
        }
        
        Duration delay = currentDelay;
        
        // 计算下次延迟（指数退避 + 随机抖动）
        long nextDelayMs = (long) (currentDelay.toMillis() * multiplier);
        nextDelayMs += ThreadLocalRandom.current().nextLong(1000); // 随机抖动
        currentDelay = Duration.ofMillis(Math.min(nextDelayMs, maxDelay.toMillis()));
        
        retryCount++;
        return delay;
    }
    
    @Override
    public void reset() {
        retryCount = 0;
        currentDelay = initialDelay;
    }
}
```

#### 3.5.6 Heartbeat Handler（心跳处理器）

```java
public class HeartbeatHandler {
    private final ScheduledExecutorService scheduler;
    private final GatewayWebSocketClient wsClient;
    private final Duration interval;
    private final Duration timeout;
    
    private ScheduledFuture<?> heartbeatTask;
    private Instant lastPongTime;
    
    /**
     * 启动心跳
     */
    public void start() {
        heartbeatTask = scheduler.scheduleAtFixedRate(
            this::sendHeartbeat,
            interval.toMillis(),
            interval.toMillis(),
            TimeUnit.MILLISECONDS
        );
    }
    
    /**
     * 停止心跳
     */
    public void stop() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }
    }
    
    /**
     * 发送心跳
     */
    private void sendHeartbeat() {
        // 检查上次心跳响应
        if (lastPongTime != null && 
            Duration.between(lastPongTime, Instant.now()).compareTo(timeout) > 0) {
            logger.warn("心跳超时，触发重连");
            wsClient.reconnect();
            return;
        }
        
        HeartbeatMessage heartbeat = HeartbeatMessage.builder()
            .agentId(config.getAgentId())
            .sessionId(sessionId)
            .timestamp(Instant.now())
            .status(buildStatus())
            .build();
        
        wsClient.send(heartbeat);
    }
    
    /**
     * 处理心跳响应
     */
    public void onPong(HeartbeatAck ack) {
        lastPongTime = Instant.now();
    }
    
    private AgentStatus buildStatus() {
        return AgentStatus.builder()
            .healthy(true)
            .tps(Bukkit.getTPS()[0])
            .onlinePlayers(Bukkit.getOnlinePlayers().size())
            .memoryUsage(getMemoryUsage())
            .build();
    }
}
```

---

### 3.6 Infrastructure Layer（基础设施层）

#### 3.6.1 Config Manager（配置管理器）

```java
public class ConfigManager {
    private final Path configFile;
    private McpAgentConfig config;
    
    /**
     * 加载配置
     */
    public void load() {
        if (!Files.exists(configFile)) {
            saveDefault();
        }
        
        try {
            config = yaml.loadAs(Files.newInputStream(configFile), 
                                 McpAgentConfig.class);
            validate(config);
        } catch (Exception e) {
            throw new ConfigException("配置加载失败", e);
        }
    }
    
    /**
     * 重载配置
     */
    public void reload() {
        McpAgentConfig oldConfig = config;
        try {
            load();
            notifyConfigChanged(oldConfig, config);
        } catch (Exception e) {
            config = oldConfig;
            throw e;
        }
    }
}

@Data
public class McpAgentConfig {
    private GatewayConfig gateway;
    private SecurityConfig security;
    private StorageConfig storage;
    private LoggingConfig logging;
    
    @Data
    public static class GatewayConfig {
        private String url = "ws://localhost:8765";
        private String token;
        private int heartbeatInterval = 30000;
        private int reconnectMaxRetries = 10;
    }
    
    @Data
    public static class SecurityConfig {
        private boolean enableDoubleCheck = true;
        private int snapshotRetentionDays = 7;
        private Map<String, RateLimitRule> rateLimits;
    }
    
    @Data
    public static class StorageConfig {
        private String snapshotDir = "plugins/McpAgent/snapshots";
        private String auditLogDir = "plugins/McpAgent/audit";
    }
}
```

**配置文件示例（config.yml）：**

```yaml
# MCP Agent Configuration

# Gateway 连接配置
gateway:
  url: "ws://localhost:8765"
  token: "your-auth-token-here"
  heartbeat-interval: 30000  # 心跳间隔（毫秒）
  reconnect-max-retries: 10  # 最大重连次数

# 安全配置
security:
  enable-double-check: true  # 启用 Agent 侧二次权限校验
  snapshot-retention-days: 7  # 快照保留天数
  
  # 限流配置
  rate-limits:
    default:
      requests: 60
      period: minute
    world.time.set:
      requests: 10
      period: minute
    economy.balance.adjust:
      requests: 30
      period: minute

# 存储配置
storage:
  snapshot-dir: "plugins/McpAgent/snapshots"
  audit-log-dir: "plugins/McpAgent/audit"

# 日志配置
logging:
  level: INFO
  audit-enabled: true
  audit-detailed: false  # 详细审计（包含完整参数）
```

#### 3.6.2 Scheduler（调度器）

```java
public class McpScheduler {
    private final Plugin plugin;
    private final ScheduledExecutorService asyncExecutor;
    
    /**
     * 在主线程执行（用于 Bukkit API 调用）
     */
    public CompletableFuture<Void> runSync(Runnable task) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                task.run();
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }
    
    /**
     * 在主线程执行并返回结果
     */
    public <T> CompletableFuture<T> callSync(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                future.complete(task.call());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }
    
    /**
     * 异步执行
     */
    public CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(task, asyncExecutor);
    }
    
    /**
     * 延迟执行
     */
    public ScheduledFuture<?> schedule(Runnable task, Duration delay) {
        return asyncExecutor.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
    }
    
    /**
     * 周期执行
     */
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, 
                                                   Duration initialDelay, 
                                                   Duration period) {
        return asyncExecutor.scheduleAtFixedRate(
            task, 
            initialDelay.toMillis(), 
            period.toMillis(), 
            TimeUnit.MILLISECONDS
        );
    }
}
```

---

## 4. SDK 设计

### 4.1 SDK 模块结构

```
mcp-agent-sdk/
├── annotations/           # 注解定义
│   ├── McpProvider.java
│   ├── McpContext.java
│   ├── McpAction.java
│   ├── McpEvent.java
│   └── Param.java
├── api/                   # 公共 API
│   ├── McpProviderRegistry.java
│   ├── McpEventEmitter.java
│   └── McpAgent.java
├── model/                 # 数据模型
│   ├── CapabilityManifest.java
│   ├── RiskLevel.java
│   ├── ErrorCode.java
│   └── dto/
│       ├── LocationParam.java
│       ├── PaginationParam.java
│       └── ...
├── exception/             # 异常定义
│   ├── McpException.java
│   ├── McpBusinessException.java
│   └── McpValidationException.java
└── util/                  # 工具类
    ├── JsonUtil.java
    └── SchemaGenerator.java
```

### 4.2 核心注解定义

```java
/**
 * 标记一个类为 MCP 能力提供者
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpProvider {
    /**
     * Provider 唯一标识符
     * 格式：小写字母、数字、连字符
     */
    String id();
    
    /**
     * Provider 显示名称
     */
    String name();
    
    /**
     * Provider 版本号
     * 遵循语义化版本规范
     */
    String version() default "1.0.0";
    
    /**
     * Provider 描述
     */
    String description() default "";
}

/**
 * 标记一个方法为 Context 类型能力（只读查询）
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpContext {
    /**
     * 能力 ID
     * 格式：{namespace}.{capability}
     */
    String id();
    
    /**
     * 能力显示名称
     */
    String name();
    
    /**
     * 能力描述
     */
    String description() default "";
    
    /**
     * 版本号
     */
    String version() default "1.0.0";
    
    /**
     * 所需权限列表
     */
    String[] permissions() default {};
    
    /**
     * 能力标签
     */
    String[] tags() default {};
    
    /**
     * 是否可缓存
     */
    boolean cacheable() default true;
    
    /**
     * 缓存 TTL（秒）
     */
    int cacheTtl() default 60;
}

/**
 * 标记一个方法为 Action 类型能力（写入操作）
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpAction {
    String id();
    String name();
    String description() default "";
    String version() default "1.0.0";
    
    /**
     * 风险等级
     */
    RiskLevel risk() default RiskLevel.MEDIUM;
    
    /**
     * 是否支持回滚
     */
    boolean rollbackSupported() default false;
    
    /**
     * 是否需要执行前快照
     */
    boolean snapshotRequired() default false;
    
    String[] permissions() default {};
    String[] tags() default {};
    
    /**
     * 是否需要确认
     */
    boolean confirmRequired() default false;
}

/**
 * 标记一个方法为 Event 类型能力（事件订阅）
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpEvent {
    String id();
    String name();
    String description() default "";
    String version() default "1.0.0";
    String[] permissions() default {};
    String[] tags() default {};
    
    /**
     * 绑定的 Bukkit 事件类型
     */
    Class<? extends Event>[] bukkitEvents() default {};
}

/**
 * 标记方法参数
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Param {
    /**
     * 参数名称
     */
    String name();
    
    /**
     * 是否必填
     */
    boolean required() default false;
    
    /**
     * 参数描述
     */
    String description() default "";
    
    /**
     * 默认值（JSON 格式）
     */
    String defaultValue() default "";
    
    /**
     * 最小值（数值类型）
     */
    double min() default Double.MIN_VALUE;
    
    /**
     * 最大值（数值类型）
     */
    double max() default Double.MAX_VALUE;
    
    /**
     * 正则表达式（字符串类型）
     */
    String pattern() default "";
}
```

### 4.3 SDK 使用示例

#### 4.3.1 基础用法

```java
// 1. 定义 Provider
@McpProvider(
    id = "ext.shopkeeper",
    name = "Shopkeeper Integration",
    version = "1.0.0",
    description = "Shopkeeper 插件 MCP 能力集成"
)
public class ShopkeeperProvider {

    @McpContext(
        id = "ext.shopkeeper.list",
        name = "获取商店列表",
        description = "获取所有已注册的商店信息",
        permissions = {"mcp.ext.shopkeeper.list"},
        tags = {"shopkeeper", "economy"}
    )
    public ShopListResult listShops(
        @Param(name = "worldName") String worldName,
        @Param(name = "pagination") PaginationParam pagination
    ) {
        // 实现查询逻辑
        List<Shop> shops = shopkeeperAPI.getShops();
        
        if (worldName != null) {
            shops = shops.stream()
                .filter(s -> s.getWorld().equals(worldName))
                .collect(Collectors.toList());
        }
        
        return paginate(shops, pagination);
    }
    
    @McpAction(
        id = "ext.shopkeeper.create",
        name = "创建商店",
        risk = RiskLevel.HIGH,
        rollbackSupported = true,
        permissions = {"mcp.ext.shopkeeper.create"},
        tags = {"shopkeeper", "economy"}
    )
    public CreateShopResult createShop(
        @Param(name = "location", required = true) LocationParam location,
        @Param(name = "type", required = true) String shopType,
        @Param(name = "owner", required = true) String ownerName
    ) {
        // 实现创建逻辑
    }
}

// 2. 注册 Provider
public class ShopkeeperMcpPlugin extends JavaPlugin {
    
    @Override
    public void onEnable() {
        // 获取 MCP Agent 服务
        McpProviderRegistry registry = getServer()
            .getServicesManager()
            .load(McpProviderRegistry.class);
        
        if (registry != null) {
            // 注册 Provider
            registry.register(new ShopkeeperProvider());
            getLogger().info("Shopkeeper MCP 能力已注册");
        }
    }
    
    @Override
    public void onDisable() {
        McpProviderRegistry registry = getServer()
            .getServicesManager()
            .load(McpProviderRegistry.class);
        
        if (registry != null) {
            registry.unregisterAll(this);
        }
    }
}
```

#### 4.3.2 事件订阅

```java
@McpProvider(id = "ext.events", name = "Custom Events")
public class CustomEventProvider {

    @McpEvent(
        id = "ext.shopkeeper.transaction",
        name = "商店交易事件",
        bukkitEvents = {ShopkeeperTradeEvent.class}
    )
    public void onTransaction(ShopkeeperTradeEvent event) {
        // 框架会自动将事件转换并推送到订阅者
    }
}
```

### 4.4 SDK Maven 依赖

```xml
<dependency>
    <groupId>io.mcp.minecraft</groupId>
    <artifactId>mcp-agent-sdk</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

---

## 5. 项目结构

### 5.1 模块划分

```
mcp-agent/
├── mcp-agent-sdk/           # SDK 模块（供第三方插件使用）
│   ├── src/main/java/
│   └── build.gradle.kts
│
├── mcp-agent-core/          # 核心实现模块
│   ├── src/main/java/
│   │   └── io/mcp/minecraft/agent/
│   │       ├── McpAgentPlugin.java          # 插件入口
│   │       ├── api/                          # API 层实现
│   │       │   ├── ProviderRegistryImpl.java
│   │       │   ├── EventEmitterImpl.java
│   │       │   └── command/
│   │       ├── core/                         # 核心业务层
│   │       │   ├── registry/
│   │       │   ├── execution/
│   │       │   ├── event/
│   │       │   ├── schema/
│   │       │   ├── permission/
│   │       │   └── audit/
│   │       ├── provider/                     # 内置 Provider
│   │       │   ├── WorldProvider.java
│   │       │   ├── PlayerProvider.java
│   │       │   ├── EntityProvider.java
│   │       │   ├── EconomyProvider.java
│   │       │   ├── SystemProvider.java
│   │       │   └── ChatProvider.java
│   │       ├── safety/                       # 安全层
│   │       │   ├── RiskEvaluator.java
│   │       │   ├── SnapshotManager.java
│   │       │   ├── RollbackHandler.java
│   │       │   ├── RateLimiter.java
│   │       │   └── SandboxExecutor.java
│   │       ├── communication/                # 通信层
│   │       │   ├── GatewayWebSocketClient.java
│   │       │   ├── MessageCodec.java
│   │       │   ├── ConnectionManager.java
│   │       │   ├── HeartbeatHandler.java
│   │       │   └── handler/
│   │       └── infrastructure/               # 基础设施层
│   │           ├── config/
│   │           ├── storage/
│   │           ├── scheduler/
│   │           ├── logging/
│   │           └── metrics/
│   ├── src/main/resources/
│   │   ├── plugin.yml
│   │   └── config.yml
│   └── build.gradle.kts
│
├── mcp-agent-test/          # 测试模块
│   ├── src/test/java/
│   └── build.gradle.kts
│
└── build.gradle.kts                  # 父 build 文件
```

### 5.2 包结构说明

```
io.mcp.minecraft.agent
├── api                      # 对外 API
│   ├── registry             # 注册相关
│   ├── event                # 事件相关
│   └── command              # 命令相关
├── core                     # 核心实现
│   ├── registry             # 能力注册中心
│   ├── execution            # 执行引擎
│   ├── event                # 事件分发
│   ├── schema               # Schema 校验
│   ├── permission           # 权限校验
│   └── audit                # 审计日志
├── provider                 # 内置能力提供者
│   └── builtin              # 内置 Provider
├── safety                   # 安全保障
│   ├── risk                 # 风险评估
│   ├── snapshot             # 快照管理
│   ├── rollback             # 回滚处理
│   └── ratelimit            # 限流控制
├── communication            # 通信层
│   ├── websocket            # WebSocket 客户端
│   ├── codec                # 消息编解码
│   ├── connection           # 连接管理
│   └── handler              # 消息处理器
├── infrastructure           # 基础设施
│   ├── config               # 配置管理
│   ├── storage              # 存储
│   ├── scheduler            # 调度器
│   ├── logging              # 日志
│   └── metrics              # 指标
└── util                     # 工具类
```

---

## 6. 核心流程设计

### 6.1 插件启动流程

```
┌─────────────────────────────────────────────────────────────────────┐
│                      Plugin Startup Flow                             │
└─────────────────────────────────────────────────────────────────────┘

  onLoad()
     │
     ▼
  ┌─────────────────┐
  │ 1. 加载配置文件  │
  └────────┬────────┘
           │
           ▼
  ┌─────────────────┐
  │ 2. 初始化日志系统│
  └────────┬────────┘
           │
           ▼
  onEnable()
     │
     ▼
  ┌─────────────────┐
  │ 3. 初始化基础设施│
  │   - Scheduler   │
  │   - Storage     │
  │   - Metrics     │
  └────────┬────────┘
           │
           ▼
  ┌─────────────────┐
  │ 4. 初始化核心模块│
  │   - Registry    │
  │   - Execution   │
  │   - Audit       │
  └────────┬────────┘
           │
           ▼
  ┌─────────────────┐
  │ 5. 初始化安全模块│
  │   - RiskEval    │
  │   - Snapshot    │
  │   - RateLimit   │
  └────────┬────────┘
           │
           ▼
  ┌─────────────────┐
  │ 6. 注册内置     │
  │    Provider     │
  └────────┬────────┘
           │
           ▼
  ┌─────────────────┐
  │ 7. 注册服务到   │
  │  ServiceManager │
  └────────┬────────┘
           │
           ▼
  ┌─────────────────┐
  │ 8. 注册命令     │
  └────────┬────────┘
           │
           ▼
  ┌─────────────────┐
  │ 9. 连接 Gateway │
  └────────┬────────┘
           │
           ▼
  ┌─────────────────┐
  │ 10. 插件启动完成│
  └─────────────────┘
```

### 6.2 能力调用流程

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Capability Invocation Flow                        │
└─────────────────────────────────────────────────────────────────────┘

  Gateway Request
       │
       ▼
  ┌─────────────────┐
  │ 1. 消息解码     │
  │   (MessageCodec)│
  └────────┬────────┘
           │
           ▼
  ┌─────────────────┐
  │ 2. 消息路由     │
  │  (MessageRouter)│
  └────────┬────────┘
           │
           ▼
  ┌─────────────────┐
  │ 3. 查找能力描述 │
  │   (Registry)    │
  └────────┬────────┘
           │
           ▼
  ┌─────────────────────────────────────────────────────────────────┐
  │                    Execution Engine Pipeline                     │
  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────┐    │
  │  │ 4.限流   │─▶│ 5.权限   │─▶│ 6.Schema │─▶│ 7.风险评估   │    │
  │  │ 校验     │  │ 校验     │  │ 校验     │  │              │    │
  │  └──────────┘  └──────────┘  └──────────┘  └──────┬───────┘    │
  │                                                    │            │
  │                                     ┌──────────────┴──────────┐ │
  │                                     ▼                         ▼ │
  │                              ┌─────────────┐          ┌─────────┐│
  │                              │ 8.需要审批  │          │ 9.创建  ││
  │                              │ 返回等待   │          │ 快照    ││
  │                              └─────────────┘          └────┬────┘│
  │                                                            │     │
  │                                                            ▼     │
  │                                                   ┌─────────────┐│
  │                                                   │ 10.执行能力 ││
  │                                                   │ (主线程)    ││
  │                                                   └──────┬──────┘│
  │                                                          │       │
  │                                                          ▼       │
  │                                                   ┌─────────────┐│
  │                                                   │ 11.记录审计 ││
  │                                                   └─────────────┘│
  └─────────────────────────────────────────────────────────────────┘
           │
           ▼
  ┌─────────────────┐
  │ 12. 构建响应    │
  └────────┬────────┘
           │
           ▼
  ┌─────────────────┐
  │ 13. 发送响应    │
  └─────────────────┘
```

### 6.3 事件推送流程

```
┌─────────────────────────────────────────────────────────────────────┐
│                      Event Push Flow                                 │
└─────────────────────────────────────────────────────────────────────┘

  Bukkit Event
       │
       ▼
  ┌─────────────────┐
  │ 1. Bukkit 监听器│
  │   捕获事件      │
  └────────┬────────┘
           │
           ▼
  ┌─────────────────┐
  │ 2. 转换为 MCP   │
  │   事件格式      │
  └────────┬────────┘
           │
           ▼
  ┌─────────────────┐
  │ 3. 查找订阅者   │
  │   (Dispatcher)  │
  └────────┬────────┘
           │
           ▼
  ┌─────────────────┐
  │ 4. 应用过滤条件 │
  └────────┬────────┘
           │
           ▼
  ┌─────────────────┐
  │ 5. 构建事件消息 │
  └────────┬────────┘
           │
           ▼
  ┌─────────────────┐
  │ 6. 推送到      │
  │   Gateway       │
  └─────────────────┘
```

---

## 7. 错误处理设计

### 7.1 异常层次结构

```
McpException (Base Exception)
├── McpValidationException        # 参数校验异常
│   ├── SchemaValidationException
│   └── ParameterRequiredException
├── McpBusinessException          # 业务异常
│   ├── PlayerNotFoundException
│   ├── WorldNotFoundException
│   ├── InsufficientBalanceException
│   └── OperationFailedException
├── McpSecurityException          # 安全异常
│   ├── PermissionDeniedException
│   ├── RateLimitExceededException
│   └── ApprovalRequiredException
├── McpCommunicationException     # 通信异常
│   ├── ConnectionLostException
│   └── MessageCodecException
└── McpSystemException            # 系统异常
    ├── ConfigurationException
    └── StorageException
```

### 7.2 统一异常处理

```java
public class ExceptionHandler {
    
    public McpResponse handleException(ExecutionContext context, Throwable ex) {
        ErrorCode errorCode;
        String message;
        Map<String, Object> details = new HashMap<>();
        
        if (ex instanceof McpBusinessException bex) {
            errorCode = bex.getErrorCode();
            message = bex.getMessage();
            details = bex.getDetails();
        } else if (ex instanceof McpValidationException vex) {
            errorCode = ErrorCode.SCHEMA_VALIDATION_FAILED;
            message = vex.getMessage();
            details.put("validationErrors", vex.getErrors());
        } else if (ex instanceof McpSecurityException sex) {
            errorCode = sex.getErrorCode();
            message = sex.getMessage();
            if (sex instanceof ApprovalRequiredException arex) {
                details.put("approvalId", arex.getApprovalId());
            }
        } else {
            // 未知异常
            errorCode = ErrorCode.INTERNAL_ERROR;
            message = "系统内部错误";
            logger.error("未处理的异常", ex);
        }
        
        return McpResponse.error(
            context.getRequestId(),
            errorCode,
            message,
            details
        );
    }
}
```

---

## 8. 测试策略

### 8.1 测试分层

| 测试类型 | 范围 | 工具 |
|----------|------|------|
| 单元测试 | 单个类/方法 | JUnit 5, Mockito |
| 集成测试 | 模块间交互 | MockBukkit |
| E2E 测试 | 完整链路 | Docker + 真实服务器 |

### 8.2 Mock 框架

使用 MockBukkit 模拟 Minecraft 服务器环境：

```java
@ExtendWith(MockBukkitExtension.class)
class WorldProviderTest {
    
    @MockBukkitInject
    private ServerMock server;
    
    private WorldProvider worldProvider;
    
    @BeforeEach
    void setUp() {
        worldProvider = new WorldProvider();
        server.addSimpleWorld("world");
    }
    
    @Test
    void testGetWorldTime() {
        World world = server.getWorld("world");
        world.setTime(6000);
        
        WorldTimeResult result = worldProvider.getWorldTime("world");
        
        assertEquals("world", result.getWorldName());
        assertEquals(6000, result.getTime());
        assertEquals(TimePhase.DAY, result.getPhase());
    }
    
    @Test
    void testGetWorldTime_NotFound() {
        assertThrows(McpBusinessException.class, () -> {
            worldProvider.getWorldTime("nonexistent");
        });
    }
}
```

---

## 9. 部署与运维

### 9.1 部署要求

| 组件 | 最低要求 | 推荐配置 |
|------|----------|----------|
| Java | 17+ | 21 |
| Minecraft Server | Paper 1.20+ | Paper 1.20.4 |
| 内存 | 512MB（插件） | 1GB |
| 网络 | 能访问 Gateway | 低延迟连接 |

### 9.2 安装步骤

1. 将 `mcp-agent-core.jar` 放入 `plugins/` 目录
2. 启动服务器生成默认配置
3. 编辑 `plugins/McpAgent/config.yml` 配置 Gateway 地址和认证信息
4. 重启服务器或执行 `/mcp reload`

### 9.3 监控指标

| 指标 | 说明 | 告警阈值 |
|------|------|----------|
| `mcp.connection.status` | 连接状态 | disconnected |
| `mcp.requests.total` | 请求总数 | - |
| `mcp.requests.errors` | 错误请求数 | > 10/min |
| `mcp.requests.latency` | 请求延迟 | > 1000ms |
| `mcp.capability.count` | 注册能力数 | - |
| `mcp.snapshot.count` | 快照数量 | > 1000 |

### 9.4 日志配置

```yaml
# logging in config.yml
logging:
  level: INFO
  
  # 分类日志级别
  categories:
    communication: DEBUG
    execution: INFO
    audit: INFO
    safety: WARN
  
  # 审计日志
  audit:
    enabled: true
    detailed: false
    file: "audit.log"
    max-size: "100MB"
    max-files: 10
```

---

## 10. 安全考虑

### 10.1 认证与授权

- Gateway 连接使用 Token 认证
- 支持 Agent 侧二次权限校验
- 能力调用携带调用者身份信息

### 10.2 数据安全

- 敏感参数脱敏存储
- 快照数据加密存储
- 审计日志防篡改

### 10.3 运行安全

- 能力执行超时控制
- 限流防止资源耗尽
- 沙箱隔离高风险操作

---

## 11. 后续演进

### Phase 1（MVP）

- [x] SDK 核心注解与注册机制
- [x] 世界、玩家、系统基础能力
- [x] Gateway 通信与心跳
- [x] 基础审计日志
- [x] 快照与回滚支持

### Phase 2

- [ ] 完整经济能力集成
- [ ] 更多内置事件支持
- [ ] 性能优化与缓存
- [ ] 能力版本管理

### Phase 3

- [ ] 分布式快照存储
- [ ] 能力热更新
- [ ] 高级沙箱执行
- [ ] 插件生态文档

---

## 附录 A：依赖清单

```xml
<dependencies>
    <!-- Minecraft Server API -->
    <dependency>
        <groupId>io.papermc.paper</groupId>
        <artifactId>paper-api</artifactId>
        <version>1.20.4-R0.1-SNAPSHOT</version>
        <scope>provided</scope>
    </dependency>
    
    <!-- JSON Processing -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>2.16.0</version>
    </dependency>
    
    <!-- JSON Schema Validation -->
    <dependency>
        <groupId>com.networknt</groupId>
        <artifactId>json-schema-validator</artifactId>
        <version>1.0.87</version>
    </dependency>
    
    <!-- Vault Economy API -->
    <dependency>
        <groupId>com.github.MilkBowl</groupId>
        <artifactId>VaultAPI</artifactId>
        <version>1.7.1</version>
        <scope>provided</scope>
    </dependency>
    
    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <version>1.18.30</version>
        <scope>provided</scope>
    </dependency>
    
    <!-- Testing -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>5.10.1</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>com.github.seeseemelk</groupId>
        <artifactId>MockBukkit-v1.20</artifactId>
        <version>3.9.0</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

---

## 附录 B：plugin.yml 示例

```yaml
name: McpAgent
version: '1.0.0'
main: io.mcp.minecraft.agent.McpAgentPlugin
api-version: '1.20'
description: Minecraft MCP Protocol Agent
author: MCP Team
website: https://mcp.minecraft.io

softdepend:
  - Vault

commands:
  mcp:
    description: MCP Agent 管理命令
    usage: /<command> <subcommand>
    permission: mcp.admin

permissions:
  mcp.admin:
    description: MCP Agent 管理权限
    default: op
    children:
      mcp.admin.status: true
      mcp.admin.reload: true
      mcp.admin.providers: true
      mcp.admin.capabilities: true
      mcp.admin.test: true
      mcp.admin.connection: true
```

---

*文档版本：1.0.0*
*最后更新：2026-01-19*
