# MCP Server Development Guide

This file provides guidance for AI coding agents working with the MinecraftContextProtocolServer codebase.

## Project Overview

**MinecraftContextProtocolServer** is a Minecraft Paper/Spigot plugin implementing the Model Context Protocol (MCP) to enable AI systems to safely interact with Minecraft servers through standardized protocols.

### Architecture Summary

- **Multi-module Gradle project**: `core` (main plugin) and `sdk` (public API)
- **Java 17** required, PaperMC API 1.20.1+, Folia-compatible
- **Provider-based architecture**: Extensible capability system via annotations
- **Dual communication servers**: WebSocket (Gateway) + HTTP SSE (Standard MCP)
- **Event-driven**: Minecraft events → MCP events → Gateway push notifications

## Critical Architecture Patterns

### 1. Provider Pattern (Core Extensibility)

Third-party plugins register capabilities using annotations. The system auto-discovers and registers providers at startup.

**Example Provider Implementation:**
```java
@McpProvider(
    id = "my-plugin",
    name = "My Plugin Provider",
    version = "1.0.0",
    description = "Provides custom capabilities"
)
public class MyProvider {
    @McpContext(
        id = "my-plugin.data.get",
        name = "Get Data",
        description = "Retrieves custom data",
        permissions = {"my-plugin.data.read"},
        tags = {"custom", "query"}
    )
    public MyResult getData(
        @Param(name = "id", required = true, description = "Data ID")
        String id
    ) {
        // Implementation
    }
    
    @McpAction(
        id = "my-plugin.data.set",
        name = "Set Data",
        description = "Sets custom data",
        risk = RiskLevel.HIGH,
        snapshotRequired = true,
        rollbackSupported = true,
        permissions = {"my-plugin.data.write"},
        tags = {"custom", "modify"}
    )
    public SetResult setData(
        @Param(name = "id", required = true, description = "Data ID")
        String id,
        @Param(name = "value", required = true, description = "Data value")
        String value
    ) {
        // Implementation
    }
}
```

**Key Files:**
- `sdk/src/main/java/cn/lunadeer/mc/mcp/sdk/annotations/` - Annotation definitions
- `core/src/main/java/cn/lunadeer/mc/mcp/builtin_provider/` - Built-in providers (World, Player, Entity, System, Chat, Block)
- `core/src/main/java/cn/lunadeer/mcp/core/registry/CapabilityRegistry.java` - Auto-scans and registers providers

### 2. Execution Engine with Interceptor Chain

All capability executions flow through the `ExecutionEngine` with a chain of interceptors for cross-cutting concerns.

**Execution Flow:**
```
McpRequest → ExecutionEngine → [Interceptors] → Handler Method → McpResponse
```

**Interceptors (in order):**
1. `PermissionChecker` - RBAC validation
2. `SchemaValidator` - Parameter/return type validation
3. `AuditLogger` - Operation auditing
4. `ExecutionChain` - Executes the actual handler

**Key Files:**
- `core/src/main/java/cn/lunadeer/mcp/core/execution/ExecutionEngine.java` - Entry point
- `core/src/main/java/cn/lunadeer/mcp/core/execution/ExecutionInterceptor.java` - Interceptor interface
- `core/src/main/java/cn/lunadeer/mcp/core/permission/PermissionChecker.java` - RBAC
- `core/src/main/java/cn/lunadeer/mcp/core/audit/AuditLogger.java` - Audit logging

### 3. Communication Architecture

The plugin supports two communication channels:

**A. WebSocket Server (Gateway Communication)**
- Primary channel for MCP Gateway
- Uses Java-WebSocket 1.5.4
- Handles authentication, heartbeats, message routing
- Supports single gateway connection (configurable)

**B. HTTP SSE MCP Server (Standard MCP)**
- Standard MCP protocol over HTTP Server-Sent Events
- For direct client connections (e.g., Claude Code)
- Bearer token authentication
- Disabled by default

**Key Files:**
- `core/src/main/java/cn/lunadeer/mcp/server/websocket_gateway/WebSocketServer.java` - WS server
- `core/src/main/java/cn/lunadeer/mcp/server/websocket_gateway/session/SessionManager.java` - Session lifecycle
- `core/src/main/java/cn/lunadeer/mcp/server/http_sse/HttpServer.java` - HTTP MCP server

### 4. Internationalization (I18n) Pattern

All user-facing text uses a centralized I18n system with YAML configuration files.

**Pattern:**
1. Define text in `I18n.java` as static fields
2. Load language files from `languages/` folder
3. Use placeholders: `{0}`, `{1}`, etc.
4. Support color codes: `§` or `&`

**Example:**
```java
// In I18n.java
public static class ExecutionEngineText extends ConfigurationPart {
    public String capabilityNotFound = "Capability not found: {0}";
    public String unexpectedErrorDuringCapabilityExecution = "Unexpected error during capability execution";
}

// Usage
XLogger.info(I18n.executionEngineText.capabilityNotFound, capabilityId);
```

**Key Files:**
- `core/src/main/java/cn/lunadeer/mcp/infrastructure/I18n.java` - Central I18n class
- `core/src/main/java/cn/lunadeer/mcp/infrastructure/configuration/` - Configuration system

## Development Workflows

### Build Commands

```bash
# Clean and build the plugin (recommended)
./gradlew Clean&Build

# Build specific module
./gradlew :core:build
./gradlew :sdk:build

# Create fat JAR (includes all dependencies)
./gradlew :core:shadowJar

# Clean build directory
./gradlew clean

# Build with full dependencies (for distribution)
./gradlew -PBuildFull=true Clean&Build
```

### Testing

```bash
# Run all tests
./gradlew test

# Run tests for specific module
./gradlew :core:test
./gradlew :sdk:test
```

### Publishing SDK

```bash
# Publish SDK to local Maven repository
./gradlew :sdk:uploadToLocal

# Publish SDK to Maven Central
./gradlew :sdk:uploadToCentral
```

### Development Environment

- **IDE**: IntelliJ IDEA (project files included)
- **Java**: 17 required
- **Minecraft**: Paper 1.20.1+ or compatible server
- **Build System**: Gradle with Kotlin DSL

### Version Management

The project uses automatic version incrementing based on git branch:
- **dev branch**: `1.0.0-alpha.X` (auto-increments)
- **main branch**: `1.0.0-beta`
- Version is stored in `version.properties` and auto-updated during build

## Code Organization

### Core Module Structure

```
core/src/main/java/cn/lunadeer/mc/mcp/
├── api/                          # Public API layer
│   ├── McpServerImpl.java
│   ├── McpEventEmitterImpl.java
│   └── command/                  # Admin commands
├── builtin_provider/             # Built-in capability providers
│   ├── WorldProvider.java        # Time, weather, game rules
│   ├── PlayerProvider.java       # Player info, teleport, kick
│   ├── EntityProvider.java       # Entity list, remove
│   ├── SystemProvider.java       # Backup, restore, plugin management
│   ├── ChatProvider.java         # Chat operations
│   └── BlockProvider.java        # Block operations
├── core/                         # Core business logic
│   ├── execution/                # Execution engine & interceptors
│   ├── permission/               # RBAC permission checker
│   ├── audit/                    # Audit logging
│   ├── registry/                 # Capability registry
│   └── schema/                   # Schema validation
├── server/                       # Communication servers
│   ├── websocket_gateway/        # WebSocket server for Gateway
│   └── http_sse/                 # HTTP SSE MCP Server
├── infrastructure/               # Infrastructure components
│   ├── configuration/            # YAML config system
│   ├── scheduler/                # Async task scheduling
│   ├── XLogger.java              # Custom logger
│   ├── Notification.java         # Player notifications
│   └── I18n.java                 # Internationalization
└── MinecraftContextProtocolServer.java  # Main plugin class
```

### SDK Module Structure

```
sdk/src/main/java/cn/lunadeer/mc/mcp/sdk/
├── api/                          # Public API interfaces
│   ├── McpServer.java
│   ├── McpProviderRegistry.java
│   └── McpEventEmitter.java
├── annotations/                  # Annotation definitions
│   ├── McpProvider.java
│   ├── McpAction.java
│   ├── McpEvent.java
│   ├── McpContext.java
│   ├── Param.java
│   └── Result.java
├── model/                        # Data models
│   ├── CapabilityType.java
│   ├── RiskLevel.java
│   ├── ErrorCode.java
│   └── dto/                      # DTOs for operations
├── exception/                    # Exception hierarchy
└── util/                         # Utilities
```

## Configuration

### Main Config File: `core/src/main/resources/config.yml`

Generated from `Configuration.java` class structure. Key sections:

- **WebSocket Server**: Host, port, enable/disable, auth token
- **HTTP SSE MCP Server**: Host, port, bearer token, enable/disable
- **Server Info**: Server ID, name, version, environment
- **Language**: i18n language selection
- **Debug**: Debug mode toggle
- **Gateway Permissions**: RBAC configuration

### Plugin Metadata: `core/src/main/resources/plugin.yml`

- **Name**: MinecraftContextProtocolServer
- **Main Class**: `cn.lunadeer.mc.mcp.MinecraftContextProtocolServer`
- **API Version**: 1.20
- **Folia Support**: Enabled
- **Commands**: `/mcp` with subcommands

## Admin Commands

- `/mcp status` - Server status
- `/mcp reload` - Reload configuration
- `/mcp providers` - List registered providers
- `/mcp capabilities` - List all capabilities
- `/mcp sessions` - List connected gateways
- `/mcp server start/stop` - Control WebSocket server
- `/mcp kick <player>` - Kick a player
- `/mcp kickall` - Kick all players

## Security Model

- **Gateway Authentication**: Token-based WebSocket auth (`authToken` in config)
- **RBAC**: Role-based access control (Viewer/Operator/Admin/Super Admin)
- **Server-side Validation**: Double-check permissions at plugin level
- **Audit Logging**: All operations logged with context
- **Risk Classification**: Low/Medium/High/Critical risk levels
- **Snapshot/Rollback**: High-risk operations support snapshots and rollback

## Key Design Decisions

### Why Two Communication Channels?

1. **WebSocket Server (Gateway)**: Primary channel for production use. Provides bidirectional communication, real-time events, and centralized management through MCP Gateway.

2. **HTTP SSE MCP Server**: Standard MCP protocol for compatibility with other MCP clients (e.g., Claude Code). Disabled by default to avoid exposing the server directly.

### Why Provider Pattern?

- **Extensibility**: Third-party plugins can add capabilities without modifying core code
- **Modularity**: Capabilities are self-contained and can be enabled/disabled independently
- **Type Safety**: Annotations provide compile-time guarantees and runtime validation
- **Documentation**: Annotations serve as inline documentation for capabilities

### Why Interceptor Chain?

- **Separation of Concerns**: Each interceptor handles one specific cross-cutting concern
- **Reusability**: Interceptors can be applied to multiple capabilities
- **Testability**: Each interceptor can be tested independently
- **Extensibility**: New interceptors can be added without modifying existing code

## Common Patterns & Conventions

### 1. Exception Handling

Use `McpBusinessException` for business logic errors with error codes:
```java
throw new McpBusinessException(
    ErrorCode.OPERATION_FAILED.getErrorCode(),
    "World not found: " + worldName
);
```

### 2. Result Objects

Return result objects instead of raw values for consistency:
```java
public WorldTimeResult getWorldTime(String worldName) {
    // ... implementation
    return new WorldTimeResult(worldName, time, fullTime, day, phase);
}
```

### 3. Configuration Parts

Use `ConfigurationPart` for nested configuration structures:
```java
public static class ServerInfo extends ConfigurationPart {
    public String serverId = "mcp-server-default";
    public String serverName = "MCP Server";
}
```

### 4. Async Operations

Use `CompletableFuture` for async operations:
```java
public CompletableFuture<McpResponse> execute(McpRequest request, CallerInfo caller) {
    return CompletableFuture.supplyAsync(() -> {
        // ... execution logic
    });
}
```

## Important Files to Read

### Core Files
- `core/src/main/java/cn/lunadeer/mcp/MinecraftContextProtocolServer.java` - Main entry point
- `core/src/main/java/cn/lunadeer/mcp/api/McpServerImpl.java` - Server implementation
- `core/src/main/java/cn/lunadeer/mcp/core/execution/ExecutionEngine.java` - Execution engine
- `core/src/main/java/cn/lunadeer/mcp/server/websocket_gateway/WebSocketServer.java` - WebSocket server
- `core/src/main/java/cn/lunadeer/mcp/server/http_sse/HttpServer.java` - HTTP MCP server

### SDK Files
- `sdk/src/main/java/cn/lunadeer/mcp/sdk/api/McpServer.java` - Main SDK API
- `sdk/src/main/java/cn/lunadeer/mcp/sdk/annotations/McpProvider.java` - Provider annotation
- `sdk/src/main/java/cn/lunadeer/mcp/sdk/annotations/McpAction.java` - Action annotation

### Documentation
- `docs/README.md` - Project overview and vision
- `docs/PRD&TechnicalDesignSummary.md` - Requirements and design (Chinese)
- `docs/docs/MCP-Agent-Architecture-Design.md` - Technical architecture
- `docs/docs/Contract-Layer-Specification.md` - MCP protocol spec
- `docs/docs/WebSocket消息协议规范.md` - WebSocket protocol (Chinese)

## Common Issues & Solutions

### Build Issues
- Ensure Java 17 is installed and configured
- Run `./gradlew clean` before rebuilding
- Check `version.properties` if version conflicts occur
- If dependency resolution fails, try `./gradlew --refresh-dependencies`

### Runtime Issues
- Check `config.yml` for correct server settings
- Verify WebSocket/HTTP server ports are available
- Check logs in server console for errors
- Enable debug mode in config for detailed logging

### Plugin Loading Issues
- Ensure PaperMC API version 1.20.1+ is used
- Verify Java 17 compatibility
- Check that all dependencies are included (for lite build)

## Testing Strategy

- **Unit Tests**: JUnit 5
- **Test Location**: `core/src/test/java/` and `sdk/src/test/java/`
- **Run Tests**: `./gradlew test`
- **Integration Tests**: Manual testing with Minecraft server

## Dependencies

### Core Dependencies
- `io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT`
- `org.java-websocket:Java-WebSocket:1.5.4`
- `net.kyori:adventure-platform-bukkit:4.3.3`

### Test Dependencies
- `org.junit.jupiter:junit-jupiter:5.10.0`

### Repositories
- Maven Central
- PaperMC Repository
- Modrinth Maven
- JitPack

## Notes for AI Agents

1. **Always use the I18n system** for user-facing messages
2. **Follow the Provider Pattern** when adding new capabilities
3. **Use the interceptor chain** for cross-cutting concerns
4. **Respect the annotation-based registration** system
5. **Maintain backward compatibility** with existing providers
6. **Update both core and SDK** when changing public APIs
7. **Use appropriate risk levels** for actions (Low/Medium/High/Critical)
8. **Consider snapshot/rollback** for high-risk operations
9. **Follow the existing package structure** for new files
10. **Use Java 17 features** but avoid bleeding-edge syntax

## Version Information

- Current version: `1.0.0-alpha.X` (dev branch) or `1.0.0-beta` (main branch)
- Last updated: 2026-01-30
- API Version: 1.20
- Java Version: 17
