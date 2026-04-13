package aedifi.votelistener.config;

import aedifi.votelistener.vote.VotifierBridge.VoteData;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public record RouteDefinition(
        String routeId,
        Optional<String> listenerLabel,
        Set<String> services,
        Set<String> targetServers,
        List<String> privateMessages,
        List<String> publicMessages
) {
    public boolean matches(VoteData vote) {
        if (listenerLabel.isPresent() && !listenerLabel.get().equals(vote.listenerLabel())) {
            return false;
        }

        if (!services.isEmpty()) {
            return services.contains(vote.serviceName());
        }

        return true;
    }
}
