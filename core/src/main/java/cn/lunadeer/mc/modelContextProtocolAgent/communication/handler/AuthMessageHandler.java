package cn.lunadeer.mc.modelContextProtocolAgent.communication.handler;

import cn.lunadeer.mc.modelContextProtocolAgent.Configuration;
import cn.lunadeer.mc.modelContextProtocolAgent.ModelContextProtocolAgent;
import cn.lunadeer.mc.modelContextProtocolAgent.communication.auth.AuthHandler;
import cn.lunadeer.mc.modelContextProtocolAgent.communication.auth.AuthResult;
import cn.lunadeer.mc.modelContextProtocolAgent.communication.codec.MessageCodec;
import cn.lunadeer.mc.modelContextProtocolAgent.communication.message.AuthRequest;
import cn.lunadeer.mc.modelContextProtocolAgent.communication.message.AuthResponse;
import cn.lunadeer.mc.modelContextProtocolAgent.communication.message.McpMessage;
import cn.lunadeer.mc.modelContextProtocolAgent.communication.session.GatewaySession;
import cn.lunadeer.mc.modelContextProtocolAgent.communication.session.SessionManager;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.I18n;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.XLogger;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.model.CapabilityManifest;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.UUID;

/**
 * Handles authentication messages from gateways.
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
public class AuthMessageHandler implements MessageHandler {

    private final AuthHandler authHandler;
    private final SessionManager sessionManager;
    private final MessageCodec messageCodec;

    public AuthMessageHandler(AuthHandler authHandler, SessionManager sessionManager, MessageCodec messageCodec) {
        this.authHandler = authHandler;
        this.sessionManager = sessionManager;
        this.messageCodec = messageCodec;
    }

    @Override
    public void handle(GatewaySession session, McpMessage message) {
        if (!(message instanceof AuthRequest)) {
            XLogger.warn("AuthMessageHandler received non-AuthRequest message: " + message.getType());
            return;
        }

        AuthRequest request = (AuthRequest) message;

        if (session.isAuthenticated()) {
            XLogger.warn(I18n.authHandlerText.gatewayReauthAttempt, session.getGatewayId());
            return;
        }

        AuthResult result = authHandler.authenticate(request.getGatewayId(), request.getToken());
        if (result.isSuccess()) {
            session.setGatewayId(request.getGatewayId());
            session.setPermissions(result.getPermissions());
            sessionManager.markAuthenticated(session);

            // Send registration acknowledgment with agent info and capabilities
            AuthResponse response = AuthResponse.builder()
                    .id(UUID.randomUUID().toString())
                    .success(true)
                    .gatewayId(request.getGatewayId())
                    .sessionId(session.getId())
                    .agentInfo(new AuthResponse.AgentInfo(
                            Configuration.agentInfo.agentId,
                            Configuration.agentInfo.agentName,
                            Configuration.agentInfo.agentVersion,
                            Configuration.agentInfo.environment,
                            new AuthResponse.ServerInfo(
                                    Bukkit.getServer().getName(),
                                    Bukkit.getServer().getWorldType(),
                                    Bukkit.getServer().getVersion(),
                                    Bukkit.getServer().getMaxPlayers()
                            )
                    ))
                    .permissions(result.getPermissions())
                    .capabilities(getCapabilityManifest())
                    .config(new AuthResponse.Config(
                            Configuration.websocketServer.heartbeatInterval,
                            Configuration.websocketServer.reconnectDelay,
                            Configuration.websocketServer.maxRetries
                    ))
                    .build();

            String jsonResponse = messageCodec.encode(response);
            session.send(jsonResponse);
        } else {
            // Send authentication failure response
            AuthResponse response = AuthResponse.builder()
                    .id(UUID.randomUUID().toString())
                    .success(false)
                    .gatewayId(request.getGatewayId())
                    .reason(result.getReason())
                    .build();

            String jsonResponse = messageCodec.encode(response);
            session.send(jsonResponse);

            // Close the connection
            session.close(4003, "Authentication failed");
        }
    }

    @Override
    public String getMessageType() {
        return "auth";
    }

    /**
     * Gets the capability manifest for the agent.
     * Retrieves all registered capabilities from the capability registry.
     */
    private List<CapabilityManifest> getCapabilityManifest() {
        try {
            ModelContextProtocolAgent plugin = ModelContextProtocolAgent.getInstance();
            if (plugin != null && plugin.getCapabilityRegistry() != null) {
                return plugin.getCapabilityRegistry().getCapabilities();
            }
        } catch (Exception e) {
            XLogger.error("Failed to retrieve capability manifest: {0}", e.getMessage());
        }
        return List.of();
    }
}
