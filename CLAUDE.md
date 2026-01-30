# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**MinecraftContextProtocolServer** is a Minecraft server plugin that implements the Model Context Protocol (MCP), enabling AI models to safely interact with Minecraft servers. The project consists of two main components:

1. **Core Plugin** (`core/`) - Main Minecraft plugin with MCP capability execution
2. **SDK** (`sdk/`) - Java SDK for other plugins to register custom MCP capabilities

## Build Commands

### Building the Plugin
```bash
# Clean and build everything (core + sdk)
./gradlew Clean&Build

# Build only the core plugin
./gradlew :core:shadowJar

# Build only the SDK
./gradlew :sdk:build

# Run tests
./gradlew test
```

### Build Output
- `core/build/libs/MinecraftContextProtocolServer-<version>.jar` - Main plugin JAR
- `sdk/build/libs/sdk-<version>.jar` - SDK JAR for distribution

## Project Structure

```
MinecraftContextProtocolServer/
├── build.gradle.kts              # Root build configuration
├── settings.gradle.kts           # Multi-module configuration
├── core/                         # Main plugin implementation
│   ├── build.gradle.kts
│   ├── src/main/java/cn/lunadeer/mc/mcp/
│   │   ├── MinecraftContextProtocolServer.java  # Plugin entry point
│   │   ├── Configuration.java                   # Plugin configuration
│   │   ├── api/                                 # Public API implementation
│   │   ├── communication/                       # WebSocket & HTTP SSE servers
│   │   ├── core/                                # Core business logic
│   │   │   ├── execution/                      # Execution engine
│   │   │   ├── permission/                     # Permission checking
│   │   │   ├── audit/                          # Audit logging
│   │   │   └── registry/                       # Capability registry
│   │   ├── provider/builtin/                    # Built-in providers
│   │   │   ├── WorldProvider.java
│   │   │   ├── PlayerProvider.java
│   │   │   ├── EntityProvider.java
│   │   │   ├── SystemProvider.java
│   │   │   ├── ChatProvider.java
│   │   │   └── BlockProvider.java
│   │   └── infrastructure/                      # Infrastructure
│   └── src/main/resources/plugin.yml
│
├── sdk/                          # SDK for external plugins
│   ├── build.gradle.kts
│   ├── src/main/java/cn/lunadeer/mc/mcp/sdk/
│   │   ├── api/                                 # SDK API interfaces
│   │   ├── annotations/                         # SDK annotations
│   │   │   ├── McpProvider.java
│   │   │   ├── McpContext.java
│   │   │   ├── McpAction.java
│   │   │   ├── McpEvent.java
│   │   │   └── Param.java
│   │   ├── exception/                           # SDK exceptions
│   │   ├── model/                               # Data models
│   │   └── util/                                # Utilities
│
└── docs/                         # Documentation
    ├── README.md
    ├── PRD&TechnicalDesignSummary.md
    └── docs/
        ├── MCP-Agent-Architecture-Design.md
        ├── Contract-Layer-Specification.md
        └── WebSocket消息协议规范.md
```

## Architecture Overview

The plugin follows a layered architecture:

```
API Layer (SDK)
    ↓
Core Layer (Registry, Execution, Permission, Audit)
    ↓
Provider Layer (World, Player, Entity, System, Chat, Block)
    ↓
Communication Layer (WebSocket Server, HTTP SSE Server)
    ↓
Infrastructure Layer (Config, Logging, Scheduling)
```

## Key Concepts

### 1. Capability System

Capabilities are the fundamental unit of functionality, defined using annotations:

- **`@McpProvider`** - Marks a class as a capability provider
- **`@McpContext`** - Read-only query capability (LOW risk)
- **`@McpAction`** - Write operation capability (MEDIUM/HIGH/CRITICAL risk)
- **`@McpEvent`** - Event emitter capability

**Example:**
```java
@McpProvider(id = "mcp-internal-world", name = "MCP World Provider")
public class WorldProvider {

    @McpContext(
        id = "world.time.get",
        name = "Get World Time",
        permissions = {"mcp.context.world.time"}
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

### 2. Execution Flow

When a capability is invoked, it goes through an interceptor chain:

```
Request → [PermissionChecker] → [AuditLogger] → [CapabilityInvoker] → Response
```

**Key Interceptors:**
- **`PermissionChecker`** - Validates caller has required permissions and roles
- **`AuditLogger`** - Records all capability executions for auditing

### 3. Communication Protocols

The Agent supports **two communication channels**:

#### WebSocket Protocol (Primary)
- Used for persistent Gateway connections
- Handles authentication, heartbeats, and request/response
- Configured in `config.yml` → `websocketServer`

#### HTTP SSE Protocol (Standard MCP)
- Used for standard MCP clients (e.g., Claude Code)
- Implements the official MCP protocol
- Configured in `config.yml` → `httpSseMcpServer`

### 4. Permission & Security Model

**Risk-Based Role Requirements:**
- `RiskLevel.LOW` - No role required
- `RiskLevel.MEDIUM` - Requires "operator" role
- `RiskLevel.HIGH` - Requires "admin" role
- `RiskLevel.CRITICAL` - Requires "super_admin" role

**CallerInfo Structure:**
```java
public class CallerInfo {
    private String id;              // Gateway ID or user identifier
    private Set<String> permissions; // Granted permissions
    private Set<String> roles;      // Assigned roles
}
```

### 5. Audit System

**All capability executions are audited** (both context queries and actions):
- Events are written asynchronously to avoid blocking execution
- Sensitive data (passwords, tokens) is redacted
- Large responses are truncated for audit logs

## Development Patterns

### Adding New Capabilities

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
        risk = RiskLevel.MEDIUM,
        permissions = {"myplugin.write"},
        snapshotRequired = false,
        rollbackSupported = false
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

### Working with the Execution Engine

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
```

### Error Handling

**Use appropriate exception types:**
```java
// Business logic errors
throw new McpBusinessException(
    ErrorCode.OPERATION_FAILED.getErrorCode(),
    "User-friendly error message"
);

// Security violations - let PermissionChecker handle it
// Invalid parameters
throw new IllegalArgumentException("Invalid parameter");
```

### Configuration Management

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

### Working with Permissions

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
  specific-gateway-id:
    - "mcp.context.world.time"
    - "myplugin.write"
```

**PermissionChecker automatically validates** that the caller has ALL required permissions.

### Audit Logging

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

## Testing

### Minecraft Admin Commands

```bash
/mcp status                    # Check plugin status
/mcp reload                    # Reload configuration
/mcp providers                 # List registered providers
/mcp capabilities              # List all available capabilities
/mcp test <capability-id>      # Test a specific capability
/mcp sessions                  # View active Gateway connections
/mcp kick <sessionId>          # Disconnect a Gateway
/mcp server start              # Start WebSocket server
/mcp server stop               # Stop WebSocket server
```

### Testing Checklist

When making changes, verify:
- [ ] Capability registration: `/mcp providers` shows your provider
- [ ] Capability discovery: `/mcp capabilities` shows your new capability
- [ ] Execution test: `/mcp test <your-capability-id>` works
- [ ] Permission check: Verify permission requirements are enforced
- [ ] Audit logging: Check logs for audit events
- [ ] Error handling: Test with invalid parameters
- [ ] Configuration: New config options are properly loaded
- [ ] WebSocket connection: Can connect and authenticate from Gateway
- [ ] HTTP SSE: Standard MCP clients can connect

## Important Notes

### 1. Plugin Lifecycle

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

### 2. Configuration File Location

**Config is stored in plugin's data folder:**
- Path: `plugins/MinecraftContextProtocolServer/config.yml`
- Auto-generated on first run
- Use `/mcp reload` to reload without restart

### 3. Audit Events Are Asynchronous

**Audit events are queued and written by a background thread** - this means:
- Audit events may not be immediately visible in logs
- The audit logger has a queue (`eventQueue`)
- Events are processed by a dedicated writer thread

### 4. Session Management

**WebSocket sessions have a 5-minute timeout** (configurable):
```java
sessionManager = new SessionManager(300); // 300 seconds = 5 minutes
```

**Inactive sessions are automatically cleaned up** by the heartbeat handler.

### 5. Schema Generation

**Input/output schemas are auto-generated** from method signatures:
- Primitive types → JSON schema primitives
- Enums → JSON schema enum
- Complex objects (Records) → JSON schema object with properties

### 6. Risk Level Mismatch

**Don't underestimate risk levels** - the PermissionChecker enforces role requirements:
- LOW → No role required
- MEDIUM → Requires "operator" role
- HIGH → Requires "admin" role
- CRITICAL → Requires "super_admin" role

**If you set LOW risk but the operation modifies world state**, it will bypass approval workflows in Gateway (which is dangerous).

### 7. Snapshot/Rollback Not Implemented in Agent

**Note:** The Safety Layer (snapshot, rollback, risk evaluation) is **SKIPPED** in Phase 1 (see `SafetyLayer-SKIPPED.md`).

**The Agent only provides hooks** (`snapshotRequired`, `rollbackSupported` in annotations):
- `snapshotRequired = true` → Gateway should create snapshot before execution
- `rollbackSupported = true` → Gateway should provide rollback capability

**Actual implementation is deferred to Gateway** (Phase 2).

### 8. HTTP SSE Server is for Standard MCP Clients

**The WebSocket server is primary** for Gateway communication.

**The HTTP SSE server** is for compatibility with standard MCP clients like:
- Claude Code
- Other MCP-compatible tools

**It implements the official MCP protocol** over HTTP/SSE.

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
