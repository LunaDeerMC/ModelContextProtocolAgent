# Minecraft MCP Platform - AI Agent Instructions

This document provides essential knowledge for AI agents working on the Minecraft MCP Platform codebase.

## Project Overview

**Minecraft MCP Platform** is a comprehensive system that enables AI models to safely interact with Minecraft servers through the Model Context Protocol (MCP). The project consists of two main components:

1. **MCP Agent** (in `core/` and `sdk/`) - A Minecraft server plugin that provides MCP capabilities
2. **MCP Gateway** (in `docs/docs/MCP-Gateway-Architecture-Design.md`) - A web platform for managing MCP interactions (not yet implemented)

The core architecture follows a layered design with clear separation of concerns:
- **API Layer** - SDK for external plugin integration
- **Core Layer** - Capability registry, execution engine, permission checking, auditing
- **Provider Layer** - Built-in capability providers (World, Player, Entity, System, Chat, Block)
- **Communication Layer** - WebSocket and HTTP SSE servers for Gateway communication
- **Infrastructure Layer** - Configuration, logging, scheduling, I18n

## Key Architectural Concepts

### 1. Capability System (The Core Concept)

Capabilities are the fundamental unit of functionality. They are defined using annotations on methods in provider classes:

- **`@McpProvider`** - Marks a class as a capability provider
- **`@McpContext`** - Marks a method as a read-only query capability (LOW risk)
- **`@McpAction`** - Marks a method as a write operation capability (MEDIUM/HIGH/CRITICAL risk)
- **`@McpEvent`** - Marks a method as an event emitter

**Example from `WorldProvider.java`:**
```java
@McpProvider(id = "mcp-internal-world", name = "MCP World Provider")
public class WorldProvider {
    
    @McpContext(
        id = "world.time.get",
        name = "Get World Time",
        permissions = {"mcp.context.world.time"},
        tags = {"world", "time", "query"}
    )
    public WorldTimeResult getWorldTime(
        @Param(name = "worldName", required = true) String worldName
    ) {
        // Implementation
    }
    
    @McpAction(
        id = "world.time.set",
        name = "Set World Time",
        risk = RiskLevel.HIGH,
        snapshotRequired = true,
        rollbackSupported = true,
        permissions = {"mcp.action.world.time"}
    )
    public SetTimeResult setWorldTime(
        @Param(name = "worldName") String worldName,
        @Param(name = "time") Long time,
        @Param(name = "reason") String reason
    ) {
        // Implementation
    }
}
```

**Critical Patterns:**
- **Annotation-driven registration**: The `CapabilityRegistry` scans classes for these annotations at runtime
- **Schema generation**: Input/output schemas are automatically generated from method signatures and return types
- **Risk classification**: Actions must specify risk level (LOW, MEDIUM, HIGH, CRITICAL) which determines permission requirements
- **Snapshot/rollback**: High-risk actions should support automatic snapshots and rollback capability

### 2. Execution Flow

When a capability is invoked, it goes through an interceptor chain:

```
Request → [PermissionChecker] → [AuditLogger] → [CapabilityInvoker] → Response
```

**From `ExecutionEngine.java`:**
```java
public CompletableFuture<McpResponse> execute(McpRequest request, CallerInfo caller) {
    // 1. Create execution context
    ExecutionContext context = createExecutionContext(request, caller);
    
    // 2. Build and execute interceptor chain
    ExecutionChain chain = new ExecutionChain(interceptors, 
        () -> invokeCapability(context));
    chain.proceed(context).join();
    
    // 3. Return response
    return context.getResponse() != null
        ? context.getResponse()
        : buildSuccessResponse(request.getId(), context.getResult());
}
```

**Key Interceptors:**
- **`PermissionChecker`** (order=100): Validates caller has required permissions and roles based on risk level
- **`AuditLogger`** (order=1000): Records all capability executions for auditing

### 3. Communication Protocols

The Agent supports **two communication channels**:

#### WebSocket Protocol (Primary)
- Used for persistent Gateway connections
- Handles authentication, heartbeats, and request/response
- Defined in `WebSocketServer.java` and `WebSocket消息协议规范.md`

**Message Flow:**
```
1. Gateway connects → Agent creates WebSocketConnection
2. Gateway sends auth message → Agent validates token and permissions
3. Gateway sends request → Agent routes through MessageRouter
4. Agent executes capability → Returns response via WebSocket
5. Heartbeat maintains connection
```

#### HTTP SSE Protocol (Standard MCP)
- Used for standard MCP clients (e.g., Claude Code)
- Implements the official MCP protocol
- Defined in `HttpServer.java` and `HttpSseTransport.java`

### 4. Permission & Security Model

**Permission Requirements:**
- Each capability specifies required permissions via `permissions[]` in annotation
- Caller's permissions are checked by `PermissionChecker`
- Permissions are configured in `config.yml` under `gatewayPermissions`

**Risk-Based Role Requirements:**
```java
// From PermissionChecker.java
private String getRequiredRoleForRiskLevel(RiskLevel riskLevel) {
    switch (riskLevel) {
        case LOW:      return null;      // No role required
        case MEDIUM:   return "operator";
        case HIGH:     return "admin";
        case CRITICAL: return "super_admin";
        default:       return null;
    }
}
```

**CallerInfo Structure:**
```java
public class CallerInfo {
    private String id;              // Gateway ID or user identifier
    private Set<String> permissions; // Granted permissions
    private Set<String> roles;      // Assigned roles
    private boolean hasAllPermissions(Set<String> required);
    private boolean hasRole(String role);
}
```

### 5. Audit System

**All capability executions are audited** (both context queries and actions):

**Audit Event Structure:**
```java
public class AuditEvent {
    private String id;
    private Instant timestamp;
    private AuditEventType eventType; // INVOKE, COMPLETED, FAILED
    private String capabilityId;
    private CallerInfo caller;
    private Map<String, Object> request;  // Sanitized (passwords redacted)
    private Object response;               // Truncated for large objects
    private RiskLevel riskLevel;
    private boolean success;
    private String error;
}
```

**Audit Flow:**
1. `AuditLogger.preHandle()` - Records INVOKE event
2. `AuditLogger.postHandle()` - Records COMPLETED event
3. `AuditLogger.onError()` - Records FAILED event
4. Events are written asynchronously to avoid blocking execution

**Sensitive Data Redaction:**
- Fields containing: password, token, secret, key, auth, credential → Redacted as `***REDACTED***`
- Large responses (maps/lists > 10 items) → Summary only

## Project Structure

```
MinecraftContextProtocolServer/
├── build.gradle.kts              # Root build file
├── settings.gradle.kts           # Multi-module config
├── core/                         # Main plugin implementation
│   ├── build.gradle.kts
│   ├── src/main/java/cn/lunadeer/mc/mcp/
│   │   ├── MinecraftContextProtocolServer.java  # Main plugin class
│   │   ├── Configuration.java                   # Plugin configuration
│   │   ├── api/                                 # Public API implementation
│   │   │   ├── McpServerImpl.java              # MCP Server implementation
│   │   │   └── McpEventEmitterImpl.java        # Event emitter
│   │   ├── communication/                       # Network layer
│   │   │   ├── WebSocketServer.java            # WebSocket server
│   │   │   ├── MessageRouter.java              # Message routing
│   │   │   ├── message/                        # Message models
│   │   │   └── session/                        # Session management
│   │   ├── core/                                # Core business logic
│   │   │   ├── execution/                      # Execution engine
│   │   │   ├── permission/                     # Permission checking
│   │   │   ├── audit/                          # Audit logging
│   │   │   └── registry/                       # Capability registry
│   │   ├── http_sse/                            # HTTP SSE server
│   │   │   ├── HttpServer.java                 # Main HTTP server
│   │   │   └── HttpSseTransport.java           # Transport layer
│   │   ├── provider/builtin/                    # Built-in providers
│   │   │   ├── WorldProvider.java
│   │   │   ├── PlayerProvider.java
│   │   │   ├── EntityProvider.java
│   │   │   ├── SystemProvider.java
│   │   │   ├── ChatProvider.java
│   │   │   └── BlockProvider.java
│   │   └── infrastructure/                      # Infrastructure
│   │       ├── XLogger.java                    # Logger wrapper
│   │       ├── I18n.java                       # Internationalization
│   │       └── configuration/                  # Config management
│   └── src/main/resources/
│       └── plugin.yml                          # Bukkit plugin descriptor
│
├── sdk/                          # SDK for external plugins
│   ├── build.gradle.kts
│   ├── src/main/java/cn/lunadeer/mc/mcp/sdk/
│   │   ├── api/                                 # SDK API interfaces
│   │   │   ├── McpServer.java                   # Main SDK entry point
│   │   │   ├── McpProviderRegistry.java
│   │   │   └── McpEventEmitter.java
│   │   ├── annotations/                         # SDK annotations
│   │   │   ├── McpProvider.java
│   │   │   ├── McpContext.java
│   │   │   ├── McpAction.java
│   │   │   ├── McpEvent.java
│   │   │   └── Param.java
│   │   ├── exception/                           # SDK exceptions
│   │   │   ├── McpException.java
│   │   │   └── McpBusinessException.java
│   │   ├── model/                               # Data models
│   │   │   ├── ErrorCode.java
│   │   │   ├── RiskLevel.java
│   │   │   ├── CapabilityManifest.java
│   │   │   └── dto/                             # Data transfer objects
│   │   └── util/                                # Utilities
│   │       └── SchemaGenerator.java             # Schema generation
│
├── docs/                         # Documentation
│   ├── README.md                              # Project overview
│   ├── PRD&TechnicalDesignSummary.md          # Product requirements
│   └── docs/                                  # Technical docs
│       ├── MCP-Agent-Architecture-Design.md   # Agent architecture
│       ├── MCP-Gateway-Architecture-Design.md # Gateway architecture
│       ├── Contract-Layer-Specification.md    # Protocol specification
│       ├── SafetyLayer-SKIPPED.md             # Safety layer (skipped)
│       └── WebSocket消息协议规范.md           # WebSocket protocol
```

## Build & Development Workflow

### Building the Plugin

**From root directory:**
```bash
# Clean and build everything (core + sdk)
./gradlew Clean&Build

# Or build individually
./gradlew :core:shadowJar
./gradlew :sdk:build
```

**Build outputs:**
- `core/build/libs/MinecraftContextProtocolServer-<version>.jar` - The main plugin JAR
- `sdk/build/libs/sdk-<version>.jar` - The SDK JAR (for distribution)

**Build variants:**
- The project supports `buildFull` property to include dependencies or not
- By default, uses `compileOnly` for Paper API (lightweight plugin)
- Full build includes all dependencies in the JAR

### Running Tests

```bash
./gradlew test
```

### Development Setup

1. **Install the plugin** to your Minecraft server's `plugins/` folder
2. **Configure** `plugins/MinecraftContextProtocolServer/config.yml`
3. **Start the server** - The plugin will auto-initialize
4. **Verify** using `/mcp status` command

### Testing Capabilities

**Using the built-in admin commands:**
```bash
/mcp status                    # Check plugin status
/mcp providers                 # List registered providers
/mcp capabilities              # List all available capabilities
/mcp test world.time.get       # Test a specific capability
/mcp sessions                  # View active Gateway connections
```

## Critical Development Patterns

### 1. Adding New Capabilities

**DO:**
```java
@McpProvider(id = "my-plugin", name = "My Plugin Provider")
public class MyProvider {
    
    // Read-only query - LOW risk
    @McpContext(
        id = "myplugin.data.get",
        name = "Get My Data",
        permissions = {"myplugin.read"},
        tags = {"myplugin", "query"}
    )
    public MyDataResult getData(
        @Param(name = "id", required = true) String id
    ) {
        // Implementation
    }
    
    // Write operation - MEDIUM risk or higher
    @McpAction(
        id = "myplugin.data.set",
        name = "Set My Data",
        risk = RiskLevel.MEDIUM,  // Choose appropriate risk level
        permissions = {"myplugin.write"},
        snapshotRequired = false,  // Set true if world modification
        rollbackSupported = false  // Set true if reversible
    )
    public boolean setData(
        @Param(name = "id") String id,
        @Param(name = "value") String value
    ) {
        // Implementation
    }
}
```

**DON'T:**
- Don't use `@McpAction` for read-only operations
- Don't forget to specify permissions in annotations
- Don't mark high-risk operations as LOW risk
- Don't forget to document parameters with `@Param` annotations

### 2. Working with the Execution Engine

**The execution flow is asynchronous** - always return `CompletableFuture` or use synchronous methods (the engine wraps them):

```java
// Good - synchronous execution
@McpContext(id = "world.time.get")
public WorldTimeResult getWorldTime(String worldName) {
    World world = Bukkit.getWorld(worldName);
    if (world == null) {
        throw new McpBusinessException(
            ErrorCode.OPERATION_FAILED.getErrorCode(),
            "World not found: " + worldName
        );
    }
    return new WorldTimeResult(...);
}

// Also acceptable - async execution
@McpContext(id = "world.async.get")
public CompletableFuture<WorldData> getWorldDataAsync(String worldName) {
    return CompletableFuture.supplyAsync(() -> {
        // Heavy computation
        return computeWorldData(worldName);
    });
}
```

### 3. Error Handling

**Use appropriate exception types:**
```java
// Business logic errors
throw new McpBusinessException(
    ErrorCode.OPERATION_FAILED.getErrorCode(),
    "User-friendly error message"
);

// Security violations (automatically caught by PermissionChecker)
// Don't throw McpSecurityException manually - let PermissionChecker handle it

// Invalid parameters
throw new IllegalArgumentException("Invalid parameter");
```

### 4. Configuration Management

**Add new config options in `Configuration.java`:**
```java
public class Configuration extends ConfigurationFile {
    
    @Comment("My new configuration section")
    public static class MySection extends ConfigurationPart {
        @Comment("My config value")
        public String myValue = "default";
        
        @Comment("My number value")
        public int myNumber = 42;
    }
    
    public static MySection mySection = new MySection();
}
```

**Access config in code:**
```java
String value = Configuration.mySection.myValue;
int number = Configuration.mySection.myNumber;
```

**The config file is auto-generated** at `plugins/MinecraftContextProtocolServer/config.yml`

### 5. Working with Permissions

**In capability annotations:**
```java
@McpContext(
    permissions = {"mcp.context.world.time", "myplugin.read"}
)
```

**In config.yml (gateway permissions):**
```yaml
gatewayPermissions:
  default:
    - "mcp.capability.execution"
    - "mcp.capability.event-emitter"
    - "mcp.capability.command-manager"
  specific-gateway-id:
    - "mcp.context.world.time"
    - "myplugin.write"
```

**PermissionChecker automatically validates** that the caller has ALL required permissions.

### 6. Audit Logging

**AuditLogger is automatic** - all capability executions are logged. For custom audit events:

```java
// If you need custom audit logging (rare)
AuditLogger auditLogger = new AuditLogger();
AuditEvent event = new AuditEvent.Builder()
    .id(UUID.randomUUID().toString())
    .timestamp(Instant.now())
    .eventType(AuditEventType.CUSTOM)
    .capabilityId("custom.event")
    .caller(caller)
    .request(sanitizedData)
    .success(true)
    .build();
auditLogger.log(event);
```

## Common Tasks & Examples

### Task: Add a new World Weather capability

**1. Create or modify provider:**
```java
// In WorldProvider.java or new provider
@McpAction(
    id = "world.weather.control",
    name = "Control Weather",
    description = "Set weather to clear/rain/thunder",
    risk = RiskLevel.MEDIUM,
    permissions = {"mcp.action.world.weather"},
    tags = {"world", "weather", "modify"}
)
public SetWeatherResult controlWeather(
    @Param(name = "worldName", required = true) String worldName,
    @Param(name = "weather", required = true) String weatherType, // "clear", "rain", "thunder"
    @Param(name = "duration") Integer durationTicks
) {
    World world = Bukkit.getWorld(worldName);
    if (world == null) {
        throw new McpBusinessException(
            ErrorCode.OPERATION_FAILED.getErrorCode(),
            "World not found: " + worldName
        );
    }
    
    WeatherType previous = getWeatherType(world);
    
    switch (weatherType.toLowerCase()) {
        case "clear":
            world.setStorm(false);
            world.setThundering(false);
            break;
        case "rain":
            world.setStorm(true);
            world.setThundering(false);
            break;
        case "thunder":
            world.setStorm(true);
            world.setThundering(true);
            break;
        default:
            throw new IllegalArgumentException(
                "Invalid weather type: " + weatherType
            );
    }
    
    if (durationTicks != null) {
        world.setWeatherDuration(durationTicks);
    }
    
    return new SetWeatherResult(true, previous, getWeatherType(world));
}
```

**2. Test the capability:**
```bash
# In Minecraft console or as admin
/mcp test world.weather.control worldName=world weather=clear duration=6000
```

### Task: Debug a capability execution

**1. Enable debug mode in config.yml:**
```yaml
debug: true
```

**2. Reload configuration:**
```bash
/mcp reload
```

**3. Check logs for detailed execution flow:**
- Permission checking
- Audit events
- Execution results

**4. Use test command:**
```bash
/mcp test <capability-id> [params]
```

### Task: Register a custom provider from another plugin

**In your plugin's onEnable():**
```java
@Override
public void onEnable() {
    // Get MCP Agent service
    McpServer mcpAgent = Bukkit.getServicesManager()
        .load(McpServer.class);
    
    if (mcpAgent != null) {
        // Register your provider
        mcpAgent.getProviderRegistry().register(new MyProvider(), this);
    }
}

@Override
public void onDisable() {
    // Unregister all providers from this plugin
    McpServer mcpAgent = Bukkit.getServicesManager()
        .load(McpServer.class);
    
    if (mcpAgent != null) {
        mcpAgent.getProviderRegistry().unregisterAll(this);
    }
}
```

## Integration Points

### External Dependencies

**Paper/Spigot API:**
- `io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT`
- Used for Bukkit integration (worlds, players, entities)

**WebSocket Library:**
- `org.java-websocket:Java-WebSocket:1.5.4`
- Used for Gateway communication

**JSON Serialization:**
- Implicitly used via Gson (from Paper API)

### Third-Party Plugin Integration

**MCP Agent is designed to be extended** by other plugins:

1. **Declare dependency** in your plugin.yml:
```yaml
depend: [MinecraftContextProtocolServer]
```

2. **Use the SDK** to register capabilities:
```java
// In your plugin
McpServer mcpAgent = Bukkit.getServicesManager()
    .load(McpServer.class);
if (mcpAgent != null) {
    mcpAgent.getProviderRegistry().register(new MyProvider(), this);
}
```

3. **Your capabilities** will be automatically discovered and exposed via MCP protocol

### Gateway Integration

**The Agent exposes two endpoints:**

1. **WebSocket Server** (default: `localhost:8080`)
   - For persistent Gateway connections
   - Configured in `config.yml` → `websocketServer`

2. **HTTP SSE Server** (default: `localhost:8081`)
   - For standard MCP clients (Claude Code, etc.)
   - Configured in `config.yml` → `httpSseMcpServer`

**Example Gateway connection flow:**
```javascript
// WebSocket connection
const ws = new WebSocket('ws://localhost:8080');

ws.onopen = () => {
    ws.send(JSON.stringify({
        id: 'auth-1',
        type: 'auth',
        timestamp: new Date().toISOString(),
        payload: {
            gatewayId: 'my-gateway',
            token: 'secret-token-from-config'
        }
    }));
};

ws.onmessage = (event) => {
    const message = JSON.parse(event.data);
    if (message.type === 'auth_response') {
        if (message.success) {
            console.log('Authenticated! Available capabilities:', 
                message.payload.capabilities);
        }
    }
};
```

## Important Notes & Gotchas

### 1. **Plugin Lifecycle**

**The Agent registers as a Bukkit service** on `onEnable()`:
```java
getServer().getServicesManager().register(
    McpServer.class, 
    mcpAgent, 
    this, 
    ServicePriority.Normal
);
```

**Other plugins should check for null** when loading the service:
```java
McpServer mcpAgent = Bukkit.getServicesManager()
    .load(McpServer.class);
if (mcpAgent == null) {
    getLogger().warning("MCP Agent not found - capabilities disabled");
    return;
}
```

### 2. **Configuration File Location**

**Config is stored in plugin's data folder:**
- Path: `plugins/MinecraftContextProtocolServer/config.yml`
- Auto-generated on first run
- Use `/mcp reload` to reload without restart

### 3. **Audit Events Are Asynchronous**

**Audit events are queued and written by a background thread** - this means:
- Audit events may not be immediately visible in logs
- The audit logger has a queue (`eventQueue`)
- Events are processed by a dedicated writer thread

### 4. **Session Management**

**WebSocket sessions have a 5-minute timeout** (configurable):
```java
sessionManager = new SessionManager(300); // 300 seconds = 5 minutes
```

**Inactive sessions are automatically cleaned up** by the heartbeat handler.

### 5. **Schema Generation**

**Input/output schemas are auto-generated** from method signatures:
- Primitive types → JSON schema primitives
- Enums → JSON schema enum
- Complex objects (Records) → JSON schema object with properties

**Example:**
```java
@McpContext(id = "world.time.get")
public WorldTimeResult getWorldTime(String worldName) {
    // ...
}

// Auto-generated schema:
{
  "type": "object",
  "properties": {
    "worldName": { "type": "string" }
  },
  "required": ["worldName"]
}
```

### 6. **Risk Level Mismatch**

**Don't underestimate risk levels** - the PermissionChecker enforces role requirements:
- LOW → No role required
- MEDIUM → Requires "operator" role
- HIGH → Requires "admin" role
- CRITICAL → Requires "super_admin" role

**If you set LOW risk but the operation modifies world state**, it will bypass approval workflows in Gateway (which is dangerous).

### 7. **Snapshot/Rollback Not Implemented in Agent**

**Note:** The Safety Layer (snapshot, rollback, risk evaluation) is **SKIPPED** in Phase 1 (see `SafetyLayer-SKIPPED.md`).

**The Agent only provides hooks** (`snapshotRequired`, `rollbackSupported` in annotations):
- `snapshotRequired = true` → Gateway should create snapshot before execution
- `rollbackSupported = true` → Gateway should provide rollback capability

**Actual implementation is deferred to Gateway** (Phase 2).

### 8. **HTTP SSE Server is for Standard MCP Clients**

**The WebSocket server is primary** for Gateway communication.

**The HTTP SSE server** is for compatibility with standard MCP clients like:
- Claude Code
- Other MCP-compatible tools

**It implements the official MCP protocol** over HTTP/SSE.

## Testing Checklist

When making changes, verify:

- [ ] **Capability registration**: `/mcp providers` shows your provider
- [ ] **Capability discovery**: `/mcp capabilities` shows your new capability
- [ ] **Execution test**: `/mcp test <your-capability-id>` works
- [ ] **Permission check**: Verify permission requirements are enforced
- [ ] **Audit logging**: Check logs for audit events
- [ ] **Error handling**: Test with invalid parameters
- [ ] **Configuration**: New config options are properly loaded
- [ ] **WebSocket connection**: Can connect and authenticate from Gateway
- [ ] **HTTP SSE**: Standard MCP clients can connect

## Documentation References

**Key documentation files:**
- `docs/README.md` - Project overview and high-level architecture
- `docs/PRD&TechnicalDesignSummary.md` - Product requirements and design
- `docs/docs/MCP-Agent-Architecture-Design.md` - Detailed agent architecture
- `docs/docs/Contract-Layer-Specification.md` - Protocol and capability specifications
- `docs/docs/WebSocket消息协议规范.md` - WebSocket message protocol

**When adding new features:**
1. Check if existing documentation needs updates
2. Follow patterns established in `WorldProvider.java`
3. Add your capability to the appropriate provider or create a new one
4. Update `plugin.yml` if adding new commands or permissions
5. Document your changes in the relevant docs

## Quick Reference

### Common Commands
```bash
# Build
./gradlew Clean&Build

# Test
./gradlew test

# Minecraft admin commands
/mcp status
/mcp reload
/mcp providers
/mcp capabilities
/mcp test <capability-id>
/mcp sessions
```

### Key Annotations
- `@McpProvider` - Class-level, marks a capability provider
- `@McpContext` - Method-level, read-only query (LOW risk)
- `@McpAction` - Method-level, write operation (MEDIUM+ risk)
- `@McpEvent` - Method-level, event emitter
- `@Param` - Parameter-level, defines parameter metadata

### Key Classes
- `CapabilityRegistry` - Manages capability registration and lookup
- `ExecutionEngine` - Executes capabilities with interceptor chain
- `PermissionChecker` - Validates permissions and roles
- `AuditLogger` - Records all capability executions
- `WebSocketServer` - Handles Gateway WebSocket connections
- `HttpServer` - Handles standard MCP HTTP/SSE connections

### Key Interfaces
- `McpServer` - Main SDK entry point (from Bukkit service manager)
- `McpProviderRegistry` - Register/unregister capabilities
- `McpEventEmitter` - Emit events to Gateway
- `ExecutionInterceptor` - Intercept capability execution

### Error Codes
- `ErrorCode.OPERATION_FAILED` - General business logic failure
- `ErrorCode.PERMISSION_DENIED` - Insufficient permissions
- `ErrorCode.CAPABILITY_NOT_FOUND` - Capability doesn't exist
- `ErrorCode.INTERNAL_ERROR` - Unexpected server error

### Risk Levels
- `RiskLevel.LOW` - Read-only queries, no role required
- `RiskLevel.MEDIUM` - Minor modifications, requires "operator" role
- `RiskLevel.HIGH` - Significant changes, requires "admin" role
- `RiskLevel.CRITICAL` - Destructive operations, requires "super_admin" role

## Next Steps

**For new contributors:**
1. Read `docs/PRD&TechnicalDesignSummary.md` to understand the vision
2. Study `WorldProvider.java` as a reference implementation
3. Start with a simple read-only capability (`@McpContext`)
4. Test using `/mcp test` command
5. Gradually add more complex capabilities

**For experienced developers:**
1. Review `ExecutionEngine.java` for performance optimization opportunities
2. Consider implementing missing Safety Layer features (snapshots, rollback)
3. Add new built-in providers (economy, inventory, etc.)
4. Improve error handling and logging
5. Write integration tests for WebSocket communication

## Support Channels

**Documentation:**
- Project README: `docs/README.md`
- Architecture docs: `docs/docs/MCP-Agent-Architecture-Design.md`
- Protocol docs: `docs/docs/Contract-Layer-Specification.md`

**Code Examples:**
- Built-in providers: `core/src/main/java/cn/lunadeer/mc/mcp/provider/builtin/`
- SDK usage: `sdk/src/main/java/cn/lunadeer/mc/mcp/sdk/`
- Main plugin: `core/src/main/java/cn/lunadeer/mc/mcp/MinecraftContextProtocolServer.java`

**Testing:**
- Unit tests: `core/src/test/`
- Manual testing: Use `/mcp test` commands in Minecraft

---

**Last Updated:** 2026-01-30
**Project Phase:** Phase 1 (Foundation)
**Status:** Active Development
