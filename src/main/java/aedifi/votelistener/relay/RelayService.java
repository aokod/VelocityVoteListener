package aedifi.votelistener.relay;

import aedifi.votelistener.config.ConfigLoader.PluginConfig;
import aedifi.votelistener.config.RouteDefinition;
import aedifi.votelistener.vote.VotifierBridge.VoteData;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RelayService {
    private final ProxyServer proxyServer;
    private final Logger logger;
    private final LegacyComponentSerializer ampersandSerializer;
    private final RouteResolver routeResolver;

    private PluginConfig config;
    private final Map<String, List<PendingPrivateMessage>> queuedPrivateMessagesByUsername = new ConcurrentHashMap<>();

    public RelayService(ProxyServer proxyServer, Logger logger, PluginConfig config) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.config = config;
        this.ampersandSerializer = LegacyComponentSerializer.legacyAmpersand();
        this.routeResolver = new RouteResolver(logger);
    }

    public void setConfig(PluginConfig config) {
        this.config = config;
    }

    public void handleVote(VoteData vote) {
        Optional<RouteDefinition> route = routeResolver.resolve(config, vote);
        if (route.isEmpty()) {
            return;
        }

        for (String targetServerName : route.get().targetServers()) {
            Optional<RegisteredServer> targetServer = proxyServer.getServer(targetServerName);
            if (targetServer.isEmpty()) {
                logger.warn("Route target server '{}' does not exist in Velocity config.", targetServerName);
                continue;
            }

            List<Component> privateLines = renderLines(route.get().privateMessages(), vote, targetServerName);
            List<Component> publicLines = renderLines(route.get().publicMessages(), vote, targetServerName);

            sendOrQueuePrivate(vote.username(), targetServerName, privateLines);
            broadcastToServer(targetServer.get(), publicLines);
        }
    }

    public void onPlayerConnectedToServer(Player player, String serverName) {
        String key = player.getUsername().toLowerCase(Locale.ROOT);
        List<PendingPrivateMessage> queued = queuedPrivateMessagesByUsername.get(key);
        if (queued == null || queued.isEmpty()) {
            return;
        }

        queued.removeIf(pending -> {
            if (pending.targetServer().equalsIgnoreCase(serverName)) {
                pending.lines().forEach(player::sendMessage);
                return true;
            }
            return false;
        });

        if (queued.isEmpty()) {
            queuedPrivateMessagesByUsername.remove(key);
        }
    }

    private List<Component> renderLines(List<String> source, VoteData vote, String serverName) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<Component> out = new ArrayList<>(source.size());
        for (String line : source) {
            String replaced = line
                    .replace("%player%", vote.username())
                    .replace("%service%", vote.serviceName())
                    .replace("%timestamp%", vote.timestamp())
                    .replace("%listener%", vote.listenerLabel())
                    .replace("%server%", serverName);
            out.add(ampersandSerializer.deserialize(replaced));
        }
        return out;
    }

    private void sendOrQueuePrivate(String username, String targetServer, List<Component> lines) {
        if (lines.isEmpty()) {
            return;
        }
        Optional<Player> onlinePlayer = proxyServer.getPlayer(username);
        if (onlinePlayer.isPresent()) {
            Player player = onlinePlayer.get();
            String currentServer = player.getCurrentServer()
                    .map(connection -> connection.getServerInfo().getName())
                    .orElse("");
            if (currentServer.equalsIgnoreCase(targetServer)) {
                lines.forEach(player::sendMessage);
                return;
            }
        }

        if (!config.queuePrivateUntilTargetJoin()) {
            return;
        }

        String key = username.toLowerCase(Locale.ROOT);
        queuedPrivateMessagesByUsername.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>())
                .add(new PendingPrivateMessage(targetServer, lines));
    }

    private void broadcastToServer(RegisteredServer targetServer, List<Component> lines) {
        if (lines.isEmpty()) {
            return;
        }
        Collection<Player> players = targetServer.getPlayersConnected();
        if (players.isEmpty()) {
            return;
        }
        for (Player player : players) {
            lines.forEach(player::sendMessage);
        }
    }

    private record PendingPrivateMessage(String targetServer, List<Component> lines) {
    }
}
