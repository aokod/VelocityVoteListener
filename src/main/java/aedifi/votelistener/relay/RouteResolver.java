package aedifi.votelistener.relay;

import aedifi.votelistener.config.ConfigLoader.PluginConfig;
import aedifi.votelistener.config.RouteDefinition;
import aedifi.votelistener.vote.VotifierBridge.VoteData;
import org.slf4j.Logger;

import java.util.Optional;

public final class RouteResolver {
    private final Logger logger;

    public RouteResolver(Logger logger) {
        this.logger = logger;
    }

    public Optional<RouteDefinition> resolve(PluginConfig config, VoteData vote) {
        for (RouteDefinition route : config.orderedRoutes()) {
            if (route.matches(vote)) {
                return Optional.of(route);
            }
        }

        if (config.defaultRoute().isPresent()) {
            RouteDefinition fallback = config.defaultRoute().get();
            if (fallback.matches(vote)) {
                return Optional.of(fallback);
            }
        }

        if (config.warnOnNoMatchingRoute()) {
            logger.warn(
                    "No matching route for vote (service='{}', listener='{}').",
                    vote.serviceName(),
                    vote.listenerLabel()
            );
        }
        return Optional.empty();
    }
}
