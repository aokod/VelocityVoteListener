package aedifi.votelistener.config;

import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.Locale;

public final class ConfigLoader {
    private static final String CONFIG_FILE = "config.yml";

    private ConfigLoader() {
    }

    public static PluginConfig loadOrCreate(Path dataDirectory, Logger logger) throws IOException {
        Files.createDirectories(dataDirectory);
        Path configPath = dataDirectory.resolve(CONFIG_FILE);
        if (Files.notExists(configPath)) {
            try (InputStream in = ConfigLoader.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
                if (in == null) {
                    throw new IOException("Default config.yml resource missing from plugin jar.");
                }
                Files.copy(in, configPath);
            }
            logger.info("Created default {}", configPath.getFileName());
        }
        return load(configPath, logger);
    }

    @SuppressWarnings("unchecked")
    public static PluginConfig load(Path configPath, Logger logger) throws IOException {
        Yaml yaml = new Yaml();
        Map<String, Object> root;
        try (Reader reader = Files.newBufferedReader(configPath)) {
            Object parsed = yaml.load(reader);
            root = parsed instanceof Map<?, ?> map ? (Map<String, Object>) map : Collections.emptyMap();
        }

        List<RouteDefinition> orderedRoutes = new ArrayList<>();
        Optional<RouteDefinition> defaultRoute = Optional.empty();

        Object routesObj = root.get("routes");
        if (routesObj instanceof Map<?, ?> routesMap) {
            for (Map.Entry<?, ?> entry : routesMap.entrySet()) {
                String routeId = String.valueOf(entry.getKey()).trim();
                if (routeId.isEmpty()) {
                    continue;
                }
                if (!(entry.getValue() instanceof Map<?, ?> routeMap)) {
                    continue;
                }

                RouteDefinition route = parseRoute(routeId, routeMap, logger, "routes." + routeId);
                if (route == null) {
                    continue;
                }

                if ("default".equals(routeId)) {
                    defaultRoute = Optional.of(route);
                } else {
                    orderedRoutes.add(route);
                }
            }
        }

        Map<?, ?> delivery = mapOrEmpty(root.get("delivery"));
        Map<?, ?> logging = mapOrEmpty(root.get("logging"));
        boolean queuePrivate = parseBoolean(delivery.get("queue-private-until-target-join"), true);
        boolean warnNoMatchingRoute = parseBoolean(
                logging.containsKey("warn-on-no-matching-route")
                        ? logging.get("warn-on-no-matching-route")
                        : logging.get("warn-on-unknown-service"),
                true
        );

        return new PluginConfig(orderedRoutes, defaultRoute, queuePrivate, warnNoMatchingRoute);
    }

    private static RouteDefinition parseRoute(String routeId, Map<?, ?> routeMap, Logger logger, String path) {
        Optional<String> listenerLabel = readOptionalLowercaseString(routeMap.get("listenerLabel"));
        Set<String> services = readLowercaseSet(routeMap.get("services"));

        if (!"default".equals(routeId) && listenerLabel.isEmpty() && services.isEmpty()) {
            logger.warn(
                    "Route '{}' has no listenerLabel/services filters and will match every vote. " +
                            "Use this only for a fallback-style route.",
                    path
            );
        }

        Set<String> servers = new LinkedHashSet<>();
        for (String server : readStringList(routeMap.get("servers"))) {
            if (!server.isBlank()) {
                servers.add(server.trim());
            }
        }
        if (servers.isEmpty()) {
            logger.warn("No target servers configured at {}.servers; route will be ignored.", path);
            return null;
        }

        List<String> privateMessages = readStringList(routeMap.get("private-messages"));
        List<String> publicMessages = readStringList(routeMap.get("public-messages"));
        return new RouteDefinition(
                routeId,
                listenerLabel,
                services,
                servers,
                privateMessages,
                publicMessages
        );
    }

    private static List<String> readStringList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : rawList) {
            if (item != null) {
                out.add(String.valueOf(item));
            }
        }
        return out;
    }

    private static Map<?, ?> mapOrEmpty(Object value) {
        return value instanceof Map<?, ?> map ? map : Collections.emptyMap();
    }

    private static Optional<String> readOptionalLowercaseString(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(text.toLowerCase(Locale.ROOT));
    }

    private static Set<String> readLowercaseSet(Object value) {
        Set<String> out = new LinkedHashSet<>();
        for (String raw : readStringList(value)) {
            if (!raw.isBlank()) {
                out.add(raw.trim().toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    private static boolean parseBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return defaultValue;
    }

    public record PluginConfig(
            List<RouteDefinition> orderedRoutes,
            Optional<RouteDefinition> defaultRoute,
            boolean queuePrivateUntilTargetJoin,
            boolean warnOnNoMatchingRoute
    ) {
    }
}
