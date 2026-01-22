package cn.lunadeer.mc.modelContextProtocolAgent.provider.builtin;

import cn.lunadeer.mc.modelContextProtocolAgentSDK.annotations.McpAction;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.annotations.McpContext;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.annotations.McpProvider;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.annotations.Param;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.exception.McpBusinessException;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.model.ErrorCode;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.model.RiskLevel;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.model.dto.*;
import org.bukkit.Bukkit;
import org.bukkit.World;

/**
 * Built-in MCP provider for world-related capabilities.
 * <p>
 * Provides capabilities for querying and modifying world state including time,
 * weather, game rules, and server performance metrics.
 * </p>
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
@McpProvider(
    id = "mcp-internal-world",
    name = "MCP World Provider",
    version = "1.0.0",
    description = "Built-in capabilities for Minecraft world management"
)
public class WorldProvider {

    /**
     * Gets the current time of a world.
     *
     * @param worldName the name of the world
     * @return the world time result
     */
    @McpContext(
        id = "world.time.get",
        name = "Get World Time",
        description = "Retrieves the current time of a world",
        permissions = {"mcp.context.world.time"},
        tags = {"world", "time", "query"}
    )
    public WorldTimeResult getWorldTime(
        @Param(name = "worldName", required = true, description = "The name of the world")
        String worldName
    ) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new McpBusinessException(
                ErrorCode.OPERATION_FAILED.getErrorCode(),
                "World not found: " + worldName
            );
        }

        long time = world.getTime();
        WorldTimeResult result = new WorldTimeResult();
        result.setWorldName(worldName);
        result.setTime(time);
        result.setFullTime(world.getFullTime());
        result.setDay((int) (world.getFullTime() / 24000));
        result.setPhase(time < 12000 ? WorldTimeResult.TimePhase.DAY : WorldTimeResult.TimePhase.NIGHT);
        return result;
    }

    /**
     * Sets the time of a world.
     *
     * @param worldName the name of the world
     * @param time the time to set (0-24000)
     * @param reason optional reason for the change
     * @return the set time result
     */
    @McpAction(
        id = "world.time.set",
        name = "Set World Time",
        description = "Sets the current time of a world",
        risk = RiskLevel.HIGH,
        snapshotRequired = true,
        rollbackSupported = true,
        permissions = {"mcp.action.world.time"},
        tags = {"world", "time", "modify"}
    )
    public SetTimeResult setWorldTime(
        @Param(name = "worldName", required = true, description = "The name of the world")
        String worldName,
        @Param(name = "time", required = true, description = "The time to set (0-24000)", min = 0, max = 24000)
        Long time,
        @Param(name = "reason", description = "Reason for the change")
        String reason
    ) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new McpBusinessException(
                ErrorCode.OPERATION_FAILED.getErrorCode(),
                "World not found: " + worldName
            );
        }

        long previousTime = world.getTime();
        world.setTime(time);

        SetTimeResult result = new SetTimeResult();
        result.setSuccess(true);
        result.setPreviousTime(previousTime);
        result.setNewTime(time);
        return result;
    }

    /**
     * Gets the current weather of a world.
     *
     * @param worldName the name of the world
     * @return the weather result
     */
    @McpContext(
        id = "world.weather.get",
        name = "Get Weather",
        description = "Retrieves the current weather of a world",
        permissions = {"mcp.context.world.weather"},
        tags = {"world", "weather", "query"}
    )
    public WeatherResult getWeather(
        @Param(name = "worldName", required = true, description = "The name of the world")
        String worldName
    ) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new McpBusinessException(
                ErrorCode.OPERATION_FAILED.getErrorCode(),
                "World not found: " + worldName
            );
        }

        cn.lunadeer.mc.modelContextProtocolAgentSDK.model.dto.WeatherType type;
        if (world.hasStorm()) {
            if (world.isThundering()) {
                type = cn.lunadeer.mc.modelContextProtocolAgentSDK.model.dto.WeatherType.THUNDER;
            } else {
                type = cn.lunadeer.mc.modelContextProtocolAgentSDK.model.dto.WeatherType.RAIN;
            }
        } else {
            type = cn.lunadeer.mc.modelContextProtocolAgentSDK.model.dto.WeatherType.CLEAR;
        }

        WeatherResult result = new WeatherResult();
        result.setWorldName(worldName);
        result.setType(type);
        result.setDuration(world.getWeatherDuration());
        return result;
    }

    /**
     * Sets the weather of a world.
     *
     * @param worldName the name of the world
     * @param type the weather type to set
     * @param duration optional duration in ticks
     * @return the set weather result
     */
    @McpAction(
        id = "world.weather.set",
        name = "Set Weather",
        description = "Sets the weather of a world",
        risk = RiskLevel.MEDIUM,
        permissions = {"mcp.action.world.weather"},
        tags = {"world", "weather", "modify"}
    )
    public SetWeatherResult setWeather(
        @Param(name = "worldName", required = true, description = "The name of the world")
        String worldName,
        @Param(name = "type", required = true, description = "The weather type to set")
        WeatherType type,
        @Param(name = "duration", description = "Duration in ticks")
        Integer duration
    ) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new McpBusinessException(
                ErrorCode.OPERATION_FAILED.getErrorCode(),
                "World not found: " + worldName
            );
        }

        cn.lunadeer.mc.modelContextProtocolAgentSDK.model.dto.WeatherType previousType;
        if (world.hasStorm()) {
            if (world.isThundering()) {
                previousType = cn.lunadeer.mc.modelContextProtocolAgentSDK.model.dto.WeatherType.THUNDER;
            } else {
                previousType = cn.lunadeer.mc.modelContextProtocolAgentSDK.model.dto.WeatherType.RAIN;
            }
        } else {
            previousType = cn.lunadeer.mc.modelContextProtocolAgentSDK.model.dto.WeatherType.CLEAR;
        }

        // Set weather
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

        SetWeatherResult result = new SetWeatherResult();
        result.setSuccess(true);
        result.setPreviousType(previousType);
        result.setNewType(type);
        return result;
    }

    /**
     * Gets the TPS (Ticks Per Second) of the server.
     *
     * @return the TPS result
     */
    @McpContext(
        id = "world.tps.get",
        name = "Get TPS",
        description = "Retrieves the server's TPS (Ticks Per Second) metrics",
        permissions = {"mcp.context.world.tps"},
        tags = {"world", "performance", "query"}
    )
    public TpsResult getTps() {
        double[] tps = Bukkit.getTPS();
        TpsResult result = new TpsResult();
        result.setTps1m(tps.length > 0 ? tps[0] : null);
        result.setTps5m(tps.length > 1 ? tps[1] : null);
        result.setTps15m(tps.length > 2 ? tps[2] : null);
        result.setMspt(Bukkit.getAverageTickTime());
        return result;
    }

    /**
     * Gets a game rule value from a world.
     *
     * @param worldName the name of the world
     * @param rule the game rule name
     * @return the game rule value
     */
    @McpContext(
        id = "world.rule.get",
        name = "Get Game Rule",
        description = "Retrieves a game rule value from a world",
        permissions = {"mcp.context.world.rule"},
        tags = {"world", "rule", "query"}
    )
    public String getGameRule(
        @Param(name = "worldName", required = true, description = "The name of the world")
        String worldName,
        @Param(name = "rule", required = true, description = "The game rule name")
        String rule
    ) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new McpBusinessException(
                ErrorCode.OPERATION_FAILED.getErrorCode(),
                "World not found: " + worldName
            );
        }

        String value = world.getGameRuleValue(rule);
        if (value == null) {
            throw new McpBusinessException(
                ErrorCode.OPERATION_FAILED.getErrorCode(),
                "Game rule not found: " + rule
            );
        }

        return value;
    }

    /**
     * Sets a game rule value in a world.
     *
     * @param worldName the name of the world
     * @param rule the game rule name
     * @param value the game rule value
     * @return true if successful
     */
    @McpAction(
        id = "world.rule.set",
        name = "Set Game Rule",
        description = "Sets a game rule value in a world",
        risk = RiskLevel.HIGH,
        snapshotRequired = true,
        rollbackSupported = true,
        permissions = {"mcp.action.world.rule"},
        tags = {"world", "rule", "modify"}
    )
    public Boolean setGameRule(
        @Param(name = "worldName", required = true, description = "The name of the world")
        String worldName,
        @Param(name = "rule", required = true, description = "The game rule name")
        String rule,
        @Param(name = "value", required = true, description = "The game rule value")
        String value
    ) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new McpBusinessException(
                ErrorCode.OPERATION_FAILED.getErrorCode(),
                "World not found: " + worldName
            );
        }

        return world.setGameRuleValue(rule, value);
    }
}
