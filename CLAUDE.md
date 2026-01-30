# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**MinecraftContextProtocolServer** - A Minecraft Paper/Spigot plugin that implements the Model Context Protocol (MCP) to enable AI systems to safely interact with Minecraft servers through standardized protocols.

### Architecture
- **Multi-module Gradle project** with 2 modules: `core` (main plugin) and `sdk` (public API for third-party plugins)
- **Java 17** required
- **PaperMC API 1.20.1** for Minecraft server integration
- **Java-WebSocket 1.5.4** for WebSocket server
- **Folia-compatible** (async scheduling support)

### Key Components

#### Core Module (`core/`)
- **Main Entry Point**: `MinecraftContextProtocolServer.java` - Bukkit plugin lifecycle
- **Provider Layer**: Extensible capability system (World, Player, Entity, System, Chat, Block providers)
- **Execution Engine**: With interceptors for permissions and audit logging
- **Communication Servers**:
  - WebSocket Server (Gateway communication)
  - HTTP SSE MCP Server (Standard MCP protocol)
- **Infrastructure**: Configuration, logging, scheduling, i18n

#### SDK Module (`sdk/`)
- **Public API**: `McpServer`, `McpProviderRegistry`, `McpEventEmitter`
- **Annotations**: `@McpProvider`, `@McpAction`, `@McpEvent`, `@McpContext`
- **Data Models**: DTOs for all operations
- **Exception Hierarchy**: `McpException`, `McpBusinessException`, `McpSecurityException`, `McpValidationException`

## Development Commands

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
```

### Publishing (SDK Module)
```bash
# Publish SDK to local Maven repository
./gradlew :sdk:uploadToLocal

# Publish SDK to Maven Central
./gradlew :sdk:uploadToCentral
```

### Testing
```bash
# Run all tests
./gradlew test

# Run tests for specific module
./gradlew :core:test
./gradlew :sdk:test
```

### Development Environment
- **IDE**: IntelliJ IDEA (project files included)
- **Java**: 17 required
- **Minecraft**: Paper 1.20.1+ or compatible server
- **Build System**: Gradle with Kotlin DSL

## Code Structure

### Package Structure
```
core/src/main/java/cn/lunadeer/mc/mcp/
├── api/                          # Public API layer
│   ├── McpServerImpl.java
│   ├── McpEventEmitterImpl.java
│   └── command/                  # Admin commands
├── builtin_provider/             # Built-in capability providers
│   ├── WorldProvider.java
│   ├── PlayerProvider.java
│   ├── EntityProvider.java
│   ├── SystemProvider.java
│   ├── ChatProvider.java
│   └── BlockProvider.java
├── core/                         # Core business logic
│   ├── execution/                # Execution engine & interceptors
│   ├── permission/               # RBAC permission checker
│   ├── audit/                    # Audit logging
│   ├── registry/                 # Capability registry
│   └── schema/                   # Schema validation
├── server/                       # Communication servers
│   ├── websocket_gateway/        # WebSocket server for Gateway
│   │   ├── WebSocketServer.java
│   │   ├── session/              # Session management
│   │   ├── handler/              # Message handlers
│   │   ├── auth/                 # Authentication
│   │   └── message/              # Message models
│   └── http_sse/                 # HTTP SSE MCP Server
│       ├── HttpServer.java
│       ├── handler/              # MCP protocol handlers
│       ├── lifecycle/            # Session lifecycle
│       └── tool/                 # Tool execution
├── infrastructure/               # Infrastructure components
│   ├── configuration/            # YAML config system
│   ├── scheduler/                # Async task scheduling
│   ├── XLogger.java              # Custom logger
│   ├── Notification.java         # Player notifications
│   └── I18n.java                 # Internationalization
└── MinecraftContextProtocolServer.java  # Main plugin class
```

### SDK Package Structure
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
- **WebSocket Server**: Host, port, enable/disable
- **HTTP SSE MCP Server**: Host, port, bearer token, enable/disable
- **Server Info**: Server ID, name, version
- **Language**: i18n language selection
- **Debug**: Debug mode toggle

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

## Key Design Patterns

### Provider Pattern
Third-party plugins can register capabilities via the SDK:
```java
@McpProvider(name = "my_provider")
public class MyProvider {
    @McpAction(name = "my_action", risk = RiskLevel.LOW)
    public MyResult myAction(MyParam param) {
        // Implementation
    }
}
```

### Interceptor Pattern
Cross-cutting concerns are handled by interceptors:
- `PermissionChecker` - RBAC validation
- `AuditLogger` - Operation auditing

### Event-Driven Architecture
Minecraft events → MCP events → Gateway push notifications

## Important Files to Read

### Documentation
- `docs/README.md` - Project overview and vision
- `docs/PRD&TechnicalDesignSummary.md` - Requirements and design (Chinese)
- `docs/docs/MCP-Agent-Architecture-Design.md` - Technical architecture
- `docs/docs/Contract-Layer-Specification.md` - MCP protocol spec
- `docs/docs/WebSocket消息协议规范.md` - WebSocket protocol (Chinese)

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

## Version Management

The project uses automatic version incrementing based on git branch:
- **dev branch**: `1.0.0-alpha.X` (auto-increments)
- **main branch**: `1.0.0-beta`

Version is stored in `version.properties` and auto-updated during build.

## Testing Strategy

- **Unit Tests**: JUnit 5
- **Test Location**: `core/src/test/java/` and `sdk/src/test/java/`
- **Run Tests**: `./gradlew test`

## Common Issues & Solutions

### Build Issues
- Ensure Java 17 is installed and configured
- Run `./gradlew clean` before rebuilding
- Check `version.properties` if version conflicts occur

### Runtime Issues
- Check `config.yml` for correct server settings
- Verify WebSocket/HTTP server ports are available
- Check logs in server console for errors

## Security Model

- **Gateway Authentication**: Token-based WebSocket auth
- **RBAC**: Role-based access control (Viewer/Operator/Admin/Super Admin)
- **Server-side Validation**: Double-check permissions at plugin level
- **Audit Logging**: All operations logged with context
- **Risk Classification**: Low/Medium/High/Critical risk levels

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
