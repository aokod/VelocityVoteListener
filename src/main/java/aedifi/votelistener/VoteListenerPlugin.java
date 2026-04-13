package aedifi.votelistener;

import aedifi.votelistener.config.ConfigLoader;
import aedifi.votelistener.config.ConfigLoader.PluginConfig;
import aedifi.votelistener.relay.RelayService;
import aedifi.votelistener.vote.VotifierBridge;
import com.google.inject.Inject;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;

@Plugin(
        id = "votelistener",
        name = "VoteListener",
        version = BuildConstants.VERSION,
        authors = {"aedifi"},
        dependencies = {
                @Dependency(id = "nuvotifier", optional = true)
        }
)
public final class VoteListenerPlugin {
    private final ProxyServer proxyServer;
    private final Logger logger;
    private final Path dataDirectory;

    private RelayService relayService;
    private VotifierBridge votifierBridge;

    @Inject
    public VoteListenerPlugin(ProxyServer proxyServer, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        PluginConfig config = loadConfig();
        this.relayService = new RelayService(proxyServer, logger, config);
        this.votifierBridge = new VotifierBridge(proxyServer.getEventManager(), this, logger);
        votifierBridge.register(relayService::handleVote);

        proxyServer.getCommandManager().register(
                proxyServer.getCommandManager().metaBuilder("votereload").build(),
                (SimpleCommand) invocation -> {
                    PluginConfig reloaded = loadConfig();
                    relayService.setConfig(reloaded);
                    invocation.source().sendMessage(Component.text("Configuration reloaded!"));
                });
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        if (relayService == null || event.getPlayer().getCurrentServer().isEmpty()) {
            return;
        }
        String joinedServer = event.getPlayer().getCurrentServer().get().getServerInfo().getName();
        relayService.onPlayerConnectedToServer(event.getPlayer(), joinedServer);
    }

    private PluginConfig loadConfig() {
        try {
            return ConfigLoader.loadOrCreate(dataDirectory, logger);
        } catch (IOException e) {
            throw new RuntimeException("Unable to load configuration file; check for syntax errors.", e);
        }
    }
}
