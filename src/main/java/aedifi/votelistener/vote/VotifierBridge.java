package aedifi.votelistener.vote;

import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.PostOrder;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

public final class VotifierBridge {
    private static final String VOTIFIER_EVENT_CLASS = "com.vexsoftware.votifier.velocity.event.VotifierEvent";

    private final EventManager eventManager;
    private final Object pluginInstance;
    private final Logger logger;

    public VotifierBridge(EventManager eventManager, Object pluginInstance, Logger logger) {
        this.eventManager = eventManager;
        this.pluginInstance = pluginInstance;
        this.logger = logger;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean register(Consumer<VoteData> voteConsumer) {
        try {
            Class<?> eventClass = Class.forName(VOTIFIER_EVENT_CLASS);
            eventManager.register(pluginInstance, (Class) eventClass, PostOrder.NORMAL, event -> {
                extractVote(event).ifPresent(voteConsumer);
            });
            logger.info("NuVotifier has been successfully hooked.");
            return true;
        } catch (ClassNotFoundException e) {
            logger.warn("NuVotifier not found. The vote listener will be idle until NuVotifier is installed on this proxy.");
            return false;
        } catch (Exception e) {
            logger.error("Failed to hook into NuVotifier. Are you using the correct fork?", e);
            return false;
        }
    }

    private Optional<VoteData> extractVote(Object votifierEvent) {
        try {
            Method getVote = votifierEvent.getClass().getMethod("getVote");
            Object vote = getVote.invoke(votifierEvent);
            if (vote == null) {
                return Optional.empty();
            }

            String service = invokeString(vote, "getServiceName");
            String username = invokeString(vote, "getUsername");
            String timestamp = invokeString(vote, "getTimeStamp");
            String listenerLabel = invokeString(votifierEvent, "getListenerLabel");
            if (service == null || username == null) {
                return Optional.empty();
            }
            return Optional.of(new VoteData(
                    service.toLowerCase(Locale.ROOT).trim(),
                    username.trim(),
                    timestamp == null ? "" : timestamp.trim(),
                    listenerLabel == null || listenerLabel.isBlank()
                            ? "default"
                            : listenerLabel.toLowerCase(Locale.ROOT).trim()
            ));
        } catch (Exception e) {
            logger.warn("Could not parse incoming vote event.", e);
            return Optional.empty();
        }
    }

    private static String invokeString(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object result = method.invoke(target);
            return result == null ? null : String.valueOf(result);
        } catch (Exception ignored) {
            return null;
        }
    }

    public record VoteData(String serviceName, String username, String timestamp, String listenerLabel) {
    }
}
