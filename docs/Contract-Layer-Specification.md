# MCP 能力规范与协议层设计文档

## Contract Layer Specification v1.0

---

## 1. 概述

### 1.1 文档目的

本文档定义了 Minecraft MCP 平台的能力规范与协议层（Contract Layer），作为 MCP Agent（服务端插件）与 MCP Gateway（Web 平台）之间的标准化契约。所有能力提供者（Provider）与消费者（Consumer）必须遵循本规范进行能力声明、注册、调用与响应。

### 1.2 设计目标

| 目标 | 说明 |
|------|------|
| **解耦** | 实现 Agent 与 Gateway 的松耦合，双方仅通过契约交互 |
| **可扩展** | 支持第三方插件实现 MCP Provider，无需修改核心代码 |
| **可演进** | 支持能力版本管理、弃用与迁移，保障生态持续演进 |
| **类型安全** | 通过 JSON Schema 实现参数与返回值的强类型校验 |
| **可审计** | 所有能力调用可追踪、可审计、可回放 |

### 1.3 术语定义

| 术语 | 定义 |
|------|------|
| **Capability** | MCP 能力，包括 Context（上下文查询）、Action（操作执行）、Event（事件订阅） |
| **Provider** | 能力提供者，通常为 MCP Agent 或第三方插件 |
| **Consumer** | 能力消费者，通常为 MCP Gateway 或模型客户端 |
| **Namespace** | 能力命名空间，用于分类和隔离能力 |
| **Schema** | 能力的参数与返回值结构定义 |

---

## 2. 能力分类体系

### 2.1 能力类型

MCP 平台定义三种核心能力类型：

```
┌─────────────────────────────────────────────────────────────┐
│                     MCP Capability Types                     │
├─────────────────┬─────────────────┬─────────────────────────┤
│    Context      │     Action      │         Event           │
│   (上下文查询)   │   (操作执行)     │       (事件订阅)         │
├─────────────────┼─────────────────┼─────────────────────────┤
│ - 只读操作       │ - 写入/修改操作  │ - 异步事件推送           │
│ - 无副作用       │ - 有副作用       │ - 订阅/取消订阅模式       │
│ - 可缓存        │ - 需权限校验     │ - 实时性要求高           │
│ - 低风险        │ - 需审计记录     │ - 支持过滤条件           │
└─────────────────┴─────────────────┴─────────────────────────┘
```

### 2.2 能力命名空间

采用分层命名空间设计，格式为 `{domain}.{subdomain}.{capability}`：

| 命名空间 | 说明 | 示例 |
|----------|------|------|
| `world` | 世界相关能力 | `world.time.get`, `world.weather.set` |
| `player` | 玩家相关能力 | `player.info.get`, `player.teleport` |
| `entity` | 实体相关能力 | `entity.list`, `entity.remove` |
| `system` | 系统相关能力 | `system.backup`, `system.reload` |
| `permission` | 权限相关能力 | `permission.group.list`, `permission.check` |
| `plugin` | 插件相关能力 | `plugin.list`, `plugin.reload` |
| `chat` | 聊天相关能力 | `chat.broadcast`, `chat.mute` |

### 2.3 保留命名空间

以下命名空间为系统保留，第三方 Provider 不得使用：

- `mcp.*` - MCP 协议内部能力
- `system.*` - 系统级能力
- `internal.*` - 内部实现能力

第三方 Provider 应使用 `ext.{provider_name}.*` 格式，例如：`ext.shopkeeper.transaction.list`

---

## 3. 能力声明规范

### 3.1 Capability Manifest Schema

每个能力必须提供完整的声明清单（Manifest），定义如下：

```json
{
  "$schema": "https://mcp.minecraft.io/schemas/capability-manifest-v1.json",
  "type": "object",
  "required": ["id", "version", "type", "name", "description", "provider"],
  "properties": {
    "id": {
      "type": "string",
      "pattern": "^[a-z][a-z0-9]*(?:\\.[a-z][a-z0-9]*)*$",
      "description": "能力唯一标识符，遵循命名空间规范",
      "examples": ["world.time.get", "player.teleport"]
    },
    "version": {
      "type": "string",
      "pattern": "^\\d+\\.\\d+\\.\\d+$",
      "description": "能力版本号，遵循语义化版本规范"
    },
    "type": {
      "type": "string",
      "enum": ["context", "action", "event"],
      "description": "能力类型"
    },
    "name": {
      "type": "string",
      "description": "能力显示名称"
    },
    "description": {
      "type": "string",
      "description": "能力详细描述"
    },
    "provider": {
      "type": "object",
      "required": ["id", "name"],
      "properties": {
        "id": {
          "type": "string",
          "description": "Provider 唯一标识符"
        },
        "name": {
          "type": "string",
          "description": "Provider 显示名称"
        },
        "version": {
          "type": "string",
          "description": "Provider 版本"
        }
      }
    },
    "parameters": {
      "$ref": "#/definitions/ParameterSchema",
      "description": "输入参数 Schema"
    },
    "returns": {
      "$ref": "#/definitions/ReturnSchema",
      "description": "返回值 Schema"
    },
    "risk": {
      "$ref": "#/definitions/RiskLevel",
      "description": "风险等级"
    },
    "permissions": {
      "type": "array",
      "items": { "type": "string" },
      "description": "所需权限列表"
    },
    "rateLimit": {
      "$ref": "#/definitions/RateLimit",
      "description": "调用频率限制"
    },
    "deprecated": {
      "$ref": "#/definitions/DeprecationInfo",
      "description": "弃用信息（如适用）"
    },
    "tags": {
      "type": "array",
      "items": { "type": "string" },
      "description": "能力标签，用于分类和搜索"
    },
    "examples": {
      "type": "array",
      "items": { "$ref": "#/definitions/Example" },
      "description": "使用示例"
    }
  }
}
```

### 3.2 完整能力声明示例

#### 3.2.1 Context 类型示例

```json
{
  "id": "world.time.get",
  "version": "1.0.0",
  "type": "context",
  "name": "获取世界时间",
  "description": "获取指定世界的当前游戏时间信息，包括游戏刻、天数、时段等",
  "provider": {
    "id": "mcp-agent-core",
    "name": "MCP Core Agent",
    "version": "1.0.0"
  },
  "parameters": {
    "type": "object",
    "required": ["worldName"],
    "properties": {
      "worldName": {
        "type": "string",
        "description": "世界名称",
        "examples": ["world", "world_nether", "world_the_end"]
      }
    }
  },
  "returns": {
    "type": "object",
    "properties": {
      "worldName": {
        "type": "string",
        "description": "世界名称"
      },
      "time": {
        "type": "integer",
        "minimum": 0,
        "maximum": 24000,
        "description": "当前时间刻（0-24000）"
      },
      "fullTime": {
        "type": "integer",
        "description": "完整时间刻（包含天数累计）"
      },
      "day": {
        "type": "integer",
        "description": "当前天数"
      },
      "phase": {
        "type": "string",
        "enum": ["dawn", "day", "dusk", "night"],
        "description": "时段"
      }
    }
  },
  "risk": {
    "level": "low",
    "reason": "只读操作，无副作用"
  },
  "permissions": ["mcp.context.world.time"],
  "rateLimit": {
    "requests": 100,
    "period": "minute"
  },
  "tags": ["world", "time", "context"],
  "examples": [
    {
      "name": "获取主世界时间",
      "parameters": { "worldName": "world" },
      "response": {
        "worldName": "world",
        "time": 6000,
        "fullTime": 1230000,
        "day": 51,
        "phase": "day"
      }
    }
  ]
}
```

#### 3.2.2 Action 类型示例

```json
{
  "id": "player.teleport",
  "version": "1.0.0",
  "type": "action",
  "name": "传送玩家",
  "description": "将指定玩家传送到目标位置",
  "provider": {
    "id": "mcp-agent-core",
    "name": "MCP Core Agent",
    "version": "1.0.0"
  },
  "parameters": {
    "type": "object",
    "required": ["playerName", "location"],
    "properties": {
      "playerName": {
        "type": "string",
        "description": "玩家名称"
      },
      "location": {
        "type": "object",
        "required": ["world", "x", "y", "z"],
        "properties": {
          "world": {
            "type": "string",
            "description": "目标世界"
          },
          "x": { "type": "number", "description": "X 坐标" },
          "y": { "type": "number", "description": "Y 坐标" },
          "z": { "type": "number", "description": "Z 坐标" },
          "yaw": { "type": "number", "description": "偏航角", "default": 0 },
          "pitch": { "type": "number", "description": "俯仰角", "default": 0 }
        }
      },
      "reason": {
        "type": "string",
        "description": "传送原因（用于审计）"
      }
    }
  },
  "returns": {
    "type": "object",
    "properties": {
      "success": { "type": "boolean" },
      "previousLocation": {
        "type": "object",
        "description": "传送前位置（用于回滚）"
      },
      "newLocation": {
        "type": "object",
        "description": "传送后位置"
      }
    }
  },
  "risk": {
    "level": "medium",
    "reason": "修改玩家位置状态",
    "rollbackSupported": true,
    "snapshotRequired": false
  },
  "permissions": ["mcp.action.player.teleport"],
  "rateLimit": {
    "requests": 30,
    "period": "minute"
  },
  "tags": ["player", "teleport", "action"],
  "examples": [
    {
      "name": "传送玩家到出生点",
      "parameters": {
        "playerName": "Steve",
        "location": {
          "world": "world",
          "x": 0,
          "y": 64,
          "z": 0
        },
        "reason": "应玩家请求传送到出生点"
      },
      "response": {
        "success": true,
        "previousLocation": {
          "world": "world",
          "x": 100,
          "y": 70,
          "z": -50
        },
        "newLocation": {
          "world": "world",
          "x": 0,
          "y": 64,
          "z": 0
        }
      }
    }
  ]
}
```

#### 3.2.3 Event 类型示例

```json
{
  "id": "player.join",
  "version": "1.0.0",
  "type": "event",
  "name": "玩家加入事件",
  "description": "当玩家加入服务器时触发",
  "provider": {
    "id": "mcp-agent-core",
    "name": "MCP Core Agent",
    "version": "1.0.0"
  },
  "parameters": {
    "type": "object",
    "properties": {
      "filter": {
        "type": "object",
        "properties": {
          "playerNames": {
            "type": "array",
            "items": { "type": "string" },
            "description": "过滤指定玩家（为空则订阅所有）"
          }
        }
      }
    }
  },
  "returns": {
    "type": "object",
    "properties": {
      "playerName": { "type": "string" },
      "playerUuid": { "type": "string" },
      "timestamp": { "type": "string", "format": "date-time" },
      "location": { "type": "object" },
      "isFirstJoin": { "type": "boolean" }
    }
  },
  "risk": {
    "level": "low",
    "reason": "只读事件订阅"
  },
  "permissions": ["mcp.event.player.join"],
  "rateLimit": {
    "subscriptions": 10,
    "period": "connection"
  },
  "tags": ["player", "event", "lifecycle"]
}
```

---

## 4. 参数与返回值 JSON Schema 规范

### 4.1 基础类型定义

```json
{
  "definitions": {
    "Location": {
      "type": "object",
      "required": ["world", "x", "y", "z"],
      "properties": {
        "world": { "type": "string", "description": "世界名称" },
        "x": { "type": "number", "description": "X 坐标" },
        "y": { "type": "number", "description": "Y 坐标" },
        "z": { "type": "number", "description": "Z 坐标" },
        "yaw": { "type": "number", "description": "偏航角", "default": 0 },
        "pitch": { "type": "number", "description": "俯仰角", "default": 0 }
      }
    },
    "PlayerRef": {
      "oneOf": [
        {
          "type": "object",
          "required": ["name"],
          "properties": {
            "name": { "type": "string", "description": "玩家名称" }
          }
        },
        {
          "type": "object",
          "required": ["uuid"],
          "properties": {
            "uuid": { 
              "type": "string", 
              "format": "uuid",
              "description": "玩家 UUID" 
            }
          }
        }
      ]
    },
    "ItemStack": {
      "type": "object",
      "required": ["type"],
      "properties": {
        "type": { "type": "string", "description": "物品类型 ID" },
        "amount": { "type": "integer", "minimum": 1, "default": 1 },
        "durability": { "type": "integer" },
        "enchantments": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "id": { "type": "string" },
              "level": { "type": "integer" }
            }
          }
        },
        "displayName": { "type": "string" },
        "lore": { "type": "array", "items": { "type": "string" } },
        "nbt": { "type": "object", "description": "额外 NBT 数据" }
      }
    },
    "TimeRange": {
      "type": "object",
      "properties": {
        "start": { "type": "string", "format": "date-time" },
        "end": { "type": "string", "format": "date-time" }
      }
    },
    "Pagination": {
      "type": "object",
      "properties": {
        "page": { "type": "integer", "minimum": 1, "default": 1 },
        "pageSize": { "type": "integer", "minimum": 1, "maximum": 100, "default": 20 }
      }
    },
    "PaginatedResult": {
      "type": "object",
      "required": ["items", "total", "page", "pageSize"],
      "properties": {
        "items": { "type": "array" },
        "total": { "type": "integer" },
        "page": { "type": "integer" },
        "pageSize": { "type": "integer" },
        "hasNext": { "type": "boolean" },
        "hasPrevious": { "type": "boolean" }
      }
    }
  }
}
```

### 4.2 参数校验规则

| 规则 | 说明 | 示例 |
|------|------|------|
| `required` | 必填字段 | `"required": ["playerName"]` |
| `minimum/maximum` | 数值范围 | `"minimum": 0, "maximum": 24000` |
| `minLength/maxLength` | 字符串长度 | `"minLength": 3, "maxLength": 16` |
| `pattern` | 正则表达式 | `"pattern": "^[a-zA-Z0-9_]+$"` |
| `enum` | 枚举值 | `"enum": ["day", "night"]` |
| `format` | 格式校验 | `"format": "uuid"` |
| `default` | 默认值 | `"default": 20` |

### 4.3 返回值结构规范

所有能力返回必须遵循统一的响应包装结构：

```json
{
  "type": "object",
  "required": ["success", "requestId", "timestamp"],
  "properties": {
    "success": {
      "type": "boolean",
      "description": "操作是否成功"
    },
    "requestId": {
      "type": "string",
      "format": "uuid",
      "description": "请求唯一标识符"
    },
    "timestamp": {
      "type": "string",
      "format": "date-time",
      "description": "响应时间戳"
    },
    "data": {
      "type": "object",
      "description": "业务数据（成功时返回）"
    },
    "error": {
      "$ref": "#/definitions/Error",
      "description": "错误信息（失败时返回）"
    },
    "metadata": {
      "type": "object",
      "properties": {
        "executionTime": { "type": "integer", "description": "执行耗时（毫秒）" },
        "serverId": { "type": "string", "description": "执行服务器标识" },
        "snapshotId": { "type": "string", "description": "快照标识（如有）" }
      }
    }
  }
}
```

---

## 5. 错误码与异常结构

### 5.1 错误响应结构

```json
{
  "definitions": {
    "Error": {
      "type": "object",
      "required": ["code", "message"],
      "properties": {
        "code": {
          "type": "string",
          "description": "错误码"
        },
        "message": {
          "type": "string",
          "description": "错误描述（用户可读）"
        },
        "details": {
          "type": "object",
          "description": "错误详情"
        },
        "cause": {
          "$ref": "#/definitions/Error",
          "description": "原因错误（链式错误）"
        },
        "suggestion": {
          "type": "string",
          "description": "修复建议"
        },
        "documentation": {
          "type": "string",
          "format": "uri",
          "description": "相关文档链接"
        }
      }
    }
  }
}
```

### 5.2 错误码规范

采用分层错误码设计：`{Category}.{Subcategory}.{Specific}`

#### 5.2.1 系统级错误（1xxxx）

| 错误码 | 说明 | HTTP 状态码 |
|--------|------|------------|
| `SYSTEM.INTERNAL_ERROR` | 系统内部错误 | 500 |
| `SYSTEM.SERVICE_UNAVAILABLE` | 服务不可用 | 503 |
| `SYSTEM.TIMEOUT` | 请求超时 | 504 |
| `SYSTEM.RATE_LIMITED` | 请求频率超限 | 429 |
| `SYSTEM.MAINTENANCE` | 系统维护中 | 503 |

#### 5.2.2 协议级错误（2xxxx）

| 错误码 | 说明 | HTTP 状态码 |
|--------|------|------------|
| `PROTOCOL.INVALID_REQUEST` | 请求格式无效 | 400 |
| `PROTOCOL.CAPABILITY_NOT_FOUND` | 能力不存在 | 404 |
| `PROTOCOL.VERSION_MISMATCH` | 版本不匹配 | 400 |
| `PROTOCOL.SCHEMA_VALIDATION_FAILED` | Schema 校验失败 | 400 |
| `PROTOCOL.UNSUPPORTED_TYPE` | 不支持的能力类型 | 400 |

#### 5.2.3 认证与权限错误（3xxxx）

| 错误码 | 说明 | HTTP 状态码 |
|--------|------|------------|
| `AUTH.UNAUTHORIZED` | 未认证 | 401 |
| `AUTH.TOKEN_EXPIRED` | Token 已过期 | 401 |
| `AUTH.TOKEN_INVALID` | Token 无效 | 401 |
| `PERMISSION.DENIED` | 权限不足 | 403 |
| `PERMISSION.ROLE_REQUIRED` | 需要特定角色 | 403 |
| `PERMISSION.APPROVAL_REQUIRED` | 需要审批 | 403 |

#### 5.2.4 业务级错误（4xxxx）

| 错误码 | 说明 | HTTP 状态码 |
|--------|------|------------|
| `BUSINESS.PLAYER_NOT_FOUND` | 玩家不存在 | 404 |
| `BUSINESS.PLAYER_OFFLINE` | 玩家不在线 | 400 |
| `BUSINESS.WORLD_NOT_FOUND` | 世界不存在 | 404 |
| `BUSINESS.INSUFFICIENT_BALANCE` | 余额不足 | 400 |
| `BUSINESS.INVALID_LOCATION` | 无效的位置 | 400 |
| `BUSINESS.OPERATION_FAILED` | 操作执行失败 | 400 |

#### 5.2.5 风控与治理错误（5xxxx）

| 错误码 | 说明 | HTTP 状态码 |
|--------|------|------------|
| `RISK.OPERATION_BLOCKED` | 操作被风控拦截 | 403 |
| `RISK.PENDING_APPROVAL` | 等待审批 | 202 |
| `RISK.APPROVAL_REJECTED` | 审批被拒绝 | 403 |
| `RISK.ROLLBACK_FAILED` | 回滚失败 | 500 |

### 5.3 错误响应示例

```json
{
  "success": false,
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-01-19T10:30:00Z",
  "error": {
    "code": "PERMISSION.APPROVAL_REQUIRED",
    "message": "该操作需要管理员审批",
    "details": {
      "capabilityId": "player.economy.balance.adjust",
      "riskLevel": "high",
      "approvalId": "approval-123456"
    },
    "suggestion": "请等待管理员审批，或联系管理员加急处理",
    "documentation": "https://docs.mcp.minecraft.io/approval-process"
  }
}
```

---

## 6. 风险等级与安全标注

### 6.1 风险等级定义

```json
{
  "definitions": {
    "RiskLevel": {
      "type": "object",
      "required": ["level"],
      "properties": {
        "level": {
          "type": "string",
          "enum": ["low", "medium", "high", "critical"],
          "description": "风险等级"
        },
        "reason": {
          "type": "string",
          "description": "风险评估理由"
        },
        "rollbackSupported": {
          "type": "boolean",
          "default": false,
          "description": "是否支持回滚"
        },
        "snapshotRequired": {
          "type": "boolean",
          "default": false,
          "description": "是否需要执行前快照"
        },
        "approvalRequired": {
          "type": "boolean",
          "default": false,
          "description": "是否需要审批"
        },
        "auditLevel": {
          "type": "string",
          "enum": ["none", "basic", "detailed", "full"],
          "default": "basic",
          "description": "审计级别"
        }
      }
    }
  }
}
```

### 6.2 风险等级策略矩阵

| 风险等级 | 默认策略 | 审批要求 | 快照要求 | 审计级别 | 示例能力 |
|----------|----------|----------|----------|----------|----------|
| `low` | 自动允许 | 无 | 无 | basic | `world.time.get`, `player.list` |
| `medium` | 自动允许 | 无 | 可选 | detailed | `player.teleport`, `chat.mute` |
| `high` | 需审批 | 单人审批 | 必须 | full | `player.economy.balance.adjust`, `world.rule.set` |
| `critical` | 多重审批 | 多人审批 | 必须 | full | `system.backup.restore`, `player.ban` |

### 6.3 能力权限标注

```json
{
  "permissions": {
    "type": "array",
    "items": {
      "type": "string",
      "pattern": "^mcp\\.(context|action|event)\\.[a-z]+(?:\\.[a-z]+)*$"
    },
    "description": "所需权限列表"
  },
  "requiredRoles": {
    "type": "array",
    "items": {
      "type": "string",
      "enum": ["viewer", "operator", "admin", "super_admin"]
    },
    "description": "所需最低角色"
  }
}
```

---

## 7. 版本管理与弃用规则

### 7.1 版本号规范

采用语义化版本（Semantic Versioning）：`MAJOR.MINOR.PATCH`

| 版本变更类型 | 说明 | 示例 |
|--------------|------|------|
| MAJOR | 不兼容的 API 变更 | 1.0.0 → 2.0.0 |
| MINOR | 向后兼容的功能新增 | 1.0.0 → 1.1.0 |
| PATCH | 向后兼容的问题修复 | 1.0.0 → 1.0.1 |

### 7.2 兼容性规则

#### 7.2.1 向后兼容变更（允许 MINOR 升级）

- 新增可选参数
- 新增返回值字段
- 扩展枚举值
- 放宽参数校验规则

#### 7.2.2 不兼容变更（必须 MAJOR 升级）

- 移除或重命名参数
- 移除返回值字段
- 修改参数类型
- 收紧参数校验规则
- 修改能力 ID

### 7.3 弃用流程

```json
{
  "definitions": {
    "DeprecationInfo": {
      "type": "object",
      "required": ["since", "removalVersion"],
      "properties": {
        "since": {
          "type": "string",
          "description": "开始弃用的版本"
        },
        "removalVersion": {
          "type": "string",
          "description": "计划移除的版本"
        },
        "reason": {
          "type": "string",
          "description": "弃用原因"
        },
        "replacement": {
          "type": "string",
          "description": "替代能力 ID"
        },
        "migrationGuide": {
          "type": "string",
          "format": "uri",
          "description": "迁移指南链接"
        }
      }
    }
  }
}
```

### 7.4 弃用示例

```json
{
  "id": "player.kick",
  "version": "1.0.0",
  "deprecated": {
    "since": "1.2.0",
    "removalVersion": "2.0.0",
    "reason": "统一玩家管理接口",
    "replacement": "player.management.kick",
    "migrationGuide": "https://docs.mcp.minecraft.io/migration/player-kick"
  }
}
```

### 7.5 版本兼容性矩阵

| Gateway 版本 | Agent 版本 | 兼容性 |
|--------------|------------|--------|
| 1.x | 1.x | ✅ 完全兼容 |
| 2.x | 1.x | ⚠️ 降级兼容 |
| 1.x | 2.x | ❌ 不兼容 |
| 2.x | 2.x | ✅ 完全兼容 |

---

## 8. Provider 注册协议

### 8.1 注册流程

```
┌─────────────┐       ┌─────────────┐       ┌─────────────┐
│  MCP Agent  │       │ MCP Gateway │       │   Database  │
└──────┬──────┘       └──────┬──────┘       └──────┬──────┘
       │                     │                     │
       │                     │  1. Connect (WS)    │
       │<────────────────────│                     │
       │                     │                     │
       │                     │  2. Handshake       │
       │<───────────────────>│                     │
       │                     │                     │
       │                     │  3. Register        │
       │<────────────────────│                     │
       │                     │  {gatewayInfo}      │
       │                     │                     │
       │  4. Ack + Capabilities │                  │
       │────────────────────>│                     │
       │                     │                     │
       │                     │  5. Store           │
       │                     │────────────────────>│
       │                     │                     │
       │  6. Heartbeat       │                     │
       │<───────────────────>│                     │
       │    (periodic)       │                     │
```

**架构说明：**
- **Gateway 主动连接**：MCP Gateway 主动连接到 MCP Agent 的 WebSocket 服务器
- **多 Agent 支持**：一个 Gateway 可以同时连接多个 Agent，实现集中式管理
- **Agent 作为服务器**：Agent 端运行 WebSocket 服务器，监听 Gateway 连接

### 8.2 注册消息结构

#### 8.2.1 Gateway 注册请求

```json
{
  "type": "register",
  "version": "1.0.0",
  "gateway": {
    "id": "gateway-001",
    "name": "Main Gateway",
    "version": "1.0.0",
    "environment": "production"
  },
  "authentication": {
    "type": "token",
    "token": "eyJhbGciOiJIUzI1NiIs..."
  }
}
```

#### 8.2.2 Agent 注册响应

```json
{
  "type": "register_ack",
  "success": true,
  "gatewayId": "gateway-001",
  "sessionId": "session-abc123",
  "agentInfo": {
    "id": "agent-001",
    "name": "Main Server Agent",
    "version": "1.0.0",
    "serverInfo": {
      "name": "MainSurvival",
      "type": "paper",
      "version": "1.20.4",
      "maxPlayers": 100,
      "onlinePlayers": 45
    },
    "environment": "production"
  },
  "config": {
    "heartbeatInterval": 30000,
    "reconnectDelay": 5000,
    "maxRetries": 3
  },
  "capabilities": [
    {
      "id": "world.time.get",
      "version": "1.0.0",
      "type": "context"
    },
    {
      "id": "player.teleport",
      "version": "1.0.0",
      "type": "action"
    }
  ]
}
```

### 8.3 心跳协议

```json
{
  "type": "heartbeat",
  "gatewayId": "gateway-001",
  "sessionId": "session-abc123",
  "timestamp": "2026-01-19T10:30:00Z",
  "status": {
    "healthy": true,
    "tps": 19.8,
    "onlinePlayers": 45,
    "memoryUsage": 0.65
  }
}
```

### 8.4 能力动态更新

```json
{
  "type": "capability_update",
  "gatewayId": "gateway-001",
  "action": "add",
  "capabilities": [
    {
      "id": "ext.shopkeeper.list",
      "version": "1.0.0",
      "type": "context"
    }
  ]
}
```

---

## 9. 通信协议规范

### 9.1 传输层

| 协议 | 用途 | 端口 |
|------|------|------|
| WebSocket | Gateway → Agent 实时通信 | 8765 |
| HTTPS | 模型客户端 → Gateway API | 443 |

**连接模式说明：**
- **Gateway 作为客户端**：MCP Gateway 主动连接到 MCP Agent 的 WebSocket 服务器
- **Agent 作为服务器**：每个 MCP Agent 运行 WebSocket 服务器，监听 Gateway 连接
- **多 Agent 支持**：一个 Gateway 可以同时连接多个 Agent，实现集中式管理

### 9.2 消息帧格式

```json
{
  "frame": {
    "type": "object",
    "required": ["id", "type", "timestamp"],
    "properties": {
      "id": {
        "type": "string",
        "format": "uuid",
        "description": "消息唯一标识"
      },
      "type": {
        "type": "string",
        "enum": [
          "request",
          "response",
          "event",
          "error",
          "heartbeat",
          "heartbeat_ack",
          "register",
          "register_ack"
        ]
      },
      "timestamp": {
        "type": "string",
        "format": "date-time"
      },
      "correlationId": {
        "type": "string",
        "description": "关联请求 ID（响应时使用）"
      },
      "payload": {
        "type": "object",
        "description": "消息体"
      }
    }
  }
}
```

### 9.3 能力调用请求

```json
{
  "id": "req-001",
  "type": "request",
  "timestamp": "2026-01-19T10:30:00Z",
  "payload": {
    "capabilityId": "player.teleport",
    "version": "1.0.0",
    "parameters": {
      "playerName": "Steve",
      "location": {
        "world": "world",
        "x": 0,
        "y": 64,
        "z": 0
      }
    },
    "context": {
      "caller": "model-gpt4",
      "sessionId": "session-123",
      "traceId": "trace-456"
    }
  }
}
```

### 9.4 能力调用响应

```json
{
  "id": "resp-001",
  "type": "response",
  "timestamp": "2026-01-19T10:30:01Z",
  "correlationId": "req-001",
  "payload": {
    "success": true,
    "data": {
      "previousLocation": {
        "world": "world",
        "x": 100,
        "y": 70,
        "z": -50
      },
      "newLocation": {
        "world": "world",
        "x": 0,
        "y": 64,
        "z": 0
      }
    },
    "metadata": {
      "executionTime": 15,
      "serverId": "agent-001",
      "snapshotId": null
    }
  }
}
```

### 9.5 事件推送

```json
{
  "id": "evt-001",
  "type": "event",
  "timestamp": "2026-01-19T10:31:00Z",
  "payload": {
    "eventId": "player.join",
    "data": {
      "playerName": "Alex",
      "playerUuid": "550e8400-e29b-41d4-a716-446655440001",
      "timestamp": "2026-01-19T10:31:00Z",
      "location": {
        "world": "world",
        "x": 0,
        "y": 64,
        "z": 0
      },
      "isFirstJoin": false
    }
  }
}
```

---

## 10. 审计日志规范

### 10.1 审计事件结构

```json
{
  "AuditEvent": {
    "type": "object",
    "required": ["id", "timestamp", "eventType", "capabilityId", "caller"],
    "properties": {
      "id": {
        "type": "string",
        "format": "uuid"
      },
      "timestamp": {
        "type": "string",
        "format": "date-time"
      },
      "eventType": {
        "type": "string",
        "enum": ["invoke", "approve", "reject", "rollback", "error"]
      },
      "capabilityId": {
        "type": "string"
      },
      "capabilityVersion": {
        "type": "string"
      },
      "caller": {
        "type": "object",
        "properties": {
          "type": { "type": "string", "enum": ["model", "user", "system"] },
          "id": { "type": "string" },
          "name": { "type": "string" }
        }
      },
      "request": {
        "type": "object",
        "description": "请求参数（脱敏后）"
      },
      "response": {
        "type": "object",
        "description": "响应结果（脱敏后）"
      },
      "riskLevel": {
        "type": "string"
      },
      "approvalInfo": {
        "type": "object",
        "properties": {
          "required": { "type": "boolean" },
          "approvedBy": { "type": "string" },
          "approvedAt": { "type": "string", "format": "date-time" }
        }
      },
      "rollbackInfo": {
        "type": "object",
        "properties": {
          "snapshotId": { "type": "string" },
          "rolledBack": { "type": "boolean" },
          "rollbackAt": { "type": "string", "format": "date-time" }
        }
      },
      "metadata": {
        "type": "object",
        "properties": {
          "agentId": { "type": "string" },
          "serverId": { "type": "string" },
          "sessionId": { "type": "string" },
          "traceId": { "type": "string" },
          "executionTime": { "type": "integer" },
          "clientIp": { "type": "string" }
        }
      }
    }
  }
}
```

---

## 11. Java SDK 注解规范

### 11.1 Provider 注解

```java
/**
 * 标记一个类为 MCP 能力提供者
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpProvider {
    /** Provider 唯一标识 */
    String id();
    
    /** Provider 显示名称 */
    String name();
    
    /** Provider 版本 */
    String version() default "1.0.0";
    
    /** 描述信息 */
    String description() default "";
}
```

### 11.2 Context 注解

```java
/**
 * 标记一个方法为 Context 类型能力
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpContext {
    /** 能力 ID（不含命名空间） */
    String id();
    
    /** 能力显示名称 */
    String name();
    
    /** 描述信息 */
    String description() default "";
    
    /** 版本 */
    String version() default "1.0.0";
    
    /** 所需权限 */
    String[] permissions() default {};
    
    /** 标签 */
    String[] tags() default {};
}
```

### 11.3 Action 注解

```java
/**
 * 标记一个方法为 Action 类型能力
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpAction {
    /** 能力 ID */
    String id();
    
    /** 能力显示名称 */
    String name();
    
    /** 描述信息 */
    String description() default "";
    
    /** 版本 */
    String version() default "1.0.0";
    
    /** 风险等级 */
    RiskLevel risk() default RiskLevel.MEDIUM;
    
    /** 是否支持回滚 */
    boolean rollbackSupported() default false;
    
    /** 是否需要执行前快照 */
    boolean snapshotRequired() default false;
    
    /** 所需权限 */
    String[] permissions() default {};
    
    /** 标签 */
    String[] tags() default {};
}
```

### 11.4 Event 注解

```java
/**
 * 标记一个方法为 Event 类型能力
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpEvent {
    /** 事件 ID */
    String id();
    
    /** 事件显示名称 */
    String name();
    
    /** 描述信息 */
    String description() default "";
    
    /** 版本 */
    String version() default "1.0.0";
    
    /** 所需权限 */
    String[] permissions() default {};
    
    /** 标签 */
    String[] tags() default {};
}
```

### 11.5 使用示例

```java
@McpProvider(
    id = "mcp-agent-core",
    name = "MCP Core Agent",
    version = "1.0.0",
    description = "MCP 核心能力提供者"
)
public class CoreCapabilityProvider {

    @McpContext(
        id = "world.time.get",
        name = "获取世界时间",
        description = "获取指定世界的当前游戏时间信息",
        permissions = {"mcp.context.world.time"},
        tags = {"world", "time"}
    )
    public WorldTimeResult getWorldTime(
        @Param(name = "worldName", required = true) String worldName
    ) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new McpException(ErrorCode.WORLD_NOT_FOUND, "世界不存在: " + worldName);
        }
        return new WorldTimeResult(world);
    }

    @McpAction(
        id = "player.teleport",
        name = "传送玩家",
        description = "将指定玩家传送到目标位置",
        risk = RiskLevel.MEDIUM,
        rollbackSupported = true,
        permissions = {"mcp.action.player.teleport"},
        tags = {"player", "teleport"}
    )
    public TeleportResult teleportPlayer(
        @Param(name = "playerName", required = true) String playerName,
        @Param(name = "location", required = true) LocationParam location,
        @Param(name = "reason") String reason
    ) {
        Player player = Bukkit.getPlayer(playerName);
        if (player == null) {
            throw new McpException(ErrorCode.PLAYER_OFFLINE, "玩家不在线: " + playerName);
        }
        
        Location previousLocation = player.getLocation();
        Location targetLocation = location.toBukkitLocation();
        
        player.teleport(targetLocation);
        
        return new TeleportResult(true, previousLocation, targetLocation);
    }
}
```

---

## 12. 扩展与生态

### 12.1 第三方 Provider 接入规范

1. **命名空间注册**：使用 `ext.{provider_name}.*` 格式
2. **能力声明**：提供完整的 Capability Manifest
3. **版本兼容**：遵循语义化版本规范
4. **安全标注**：正确标注风险等级与权限要求
5. **文档提供**：提供 API 文档与使用示例

### 12.2 能力发现 API

```
GET /api/v1/capabilities
GET /api/v1/capabilities/{capabilityId}
GET /api/v1/capabilities?namespace=world&type=context
GET /api/v1/providers
GET /api/v1/providers/{providerId}/capabilities
```

### 12.3 能力测试工具

Gateway 提供内置的能力测试工具：

- **Schema 校验器**：验证能力声明是否符合规范
- **沙箱执行器**：在隔离环境测试能力执行
- **Mock 服务**：模拟 Agent 响应进行开发测试

---

## 附录 A：完整 JSON Schema 定义

完整的 JSON Schema 定义文件请参见：
- `/schemas/capability-manifest-v1.json`
- `/schemas/common-types-v1.json`
- `/schemas/error-v1.json`
- `/schemas/message-frame-v1.json`
- `/schemas/audit-event-v1.json`

---

## 附录 B：核心能力清单

| 能力 ID | 类型 | 风险等级 | 描述 |
|---------|------|----------|------|
| `world.time.get` | context | low | 获取世界时间 |
| `world.time.set` | action | high | 设置世界时间 |
| `world.weather.get` | context | low | 获取天气状态 |
| `world.weather.set` | action | medium | 设置天气状态 |
| `world.rule.get` | context | low | 获取游戏规则 |
| `world.rule.set` | action | high | 设置游戏规则 |
| `world.tps.get` | context | low | 获取 TPS |
| `world.block.get` | context | low | 获取方块信息 |
| `world.block.set` | action | high | 设置方块 |
| `player.list` | context | low | 获取在线玩家列表 |
| `player.info.get` | context | low | 获取玩家信息 |
| `player.teleport` | action | medium | 传送玩家 |
| `player.kick` | action | medium | 踢出玩家 |
| `player.ban` | action | critical | 封禁玩家 |
| `player.mute` | action | medium | 禁言玩家 |
| `player.economy.balance.get` | context | low | 获取玩家余额 |
| `player.economy.balance.adjust` | action | high | 调整玩家余额 |
| `entity.list` | context | low | 列出实体 |
| `entity.remove` | action | high | 移除实体 |
| `system.backup` | action | high | 创建备份 |
| `system.restore` | action | critical | 恢复备份 |
| `system.reload` | action | high | 重载插件 |
| `chat.broadcast` | action | medium | 广播消息 |

---

## 附录 C：变更记录

| 版本 | 日期 | 变更内容 |
|------|------|----------|
| 1.0.0 | 2026-01-19 | 初始版本发布 |

---

*文档结束*
