package com.plugindetector;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PluginDetectorClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("plugindetector");

    private static final Set<String> detectedChannels = new HashSet<>();
    private static final Set<String> detectedPlugins = new HashSet<>();

    private static final String[] KNOWN_PLUGINS = {
        "bungeecord", "velocity", "essentials", "essentialsx",
        "worldedit", "fawe", "luckperms", "litebans", "advancedban",
        "coreprotect", "griefprevention", "towny", "factions", "lands",
        "plotsquared", "nocheatplus", "aac", "matrix", "spartan", "vulcan",
        "grim", "viaversion", "viabackwards", "protocollib", "citizens",
        "mythicmobs", "shopguiplus", "mcmmo", "auraskills", "aureliumskills",
        "deluxemenus", "multiverse", "worldguard", "skript", "vault",
        "placeholderapi", "tab", "chatcontrol", "clearlag", "quickshop",
        "slimefun", "jobs", "tokenmanager", "playerpoints", "geyser",
        "floodgate", "discordsrv", "dynmap", "bluemap", "squaremap",
        "cmi", "nucleus", "redisbungee", "spark", "paper", "purpur",
        "folia", "spigot", "waterfall", "wildstacker", "crazycrates",
        "excellentcrates", "quests", "betonquest", "imageonmap",
        "itemsadder", "oraxen", "superiorskyblock", "iridiumskyblock",
        "prison", "rankup", "combatlog", "pvpmanager", "minepacks",
        "headdb", "chest", "hopper", "autorank", "automessage"
    };

    @Override
    public void onInitializeClient() {
        LOGGER.info("Plugin Detector loaded! Use /server plugins");

        // Clear on join/disconnect
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            detectedChannels.clear();
            detectedPlugins.clear();

            // Read server brand
            client.execute(() -> {
                try {
                    if (handler.getBrand() != null) {
                        detectedPlugins.add("Brand: " + handler.getBrand());
                    }
                } catch (Exception ignored) {}
            });

            // Scan registered channels from the connection
            client.execute(() -> {
                try {
                    
                // Try to read plugin channels via reflection
                try {
                    java.lang.reflect.Field[] fields = handler.getClass().getDeclaredFields();
                    for (java.lang.reflect.Field field : fields) {
                        field.setAccessible(true);
                        Object val = field.get(handler);
                        if (val instanceof Set<?> set) {
                            for (Object item : set) {
                                String s = item.toString().toLowerCase();
                                if (s.contains(":")) {
                                    detectedChannels.add(s);
                                    extractPlugin(s);
                                }
                            }
                        }
                        if (val instanceof java.util.Collection<?> col) {
                            for (Object item : col) {
                                String s = item.toString().toLowerCase();
                                if (s.contains(":") && !s.contains("minecraft")
                                        && !s.contains("fabric")) {
                                    detectedChannels.add(s);
                                    extractPlugin(s);
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            });
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            detectedChannels.clear();
            detectedPlugins.clear();
        });

        // Register commands
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                ClientCommandManager.literal("server")
                    .then(ClientCommandManager.literal("plugins")
                        .executes(context -> {
                            showPlugins(context.getSource().getClient());
                            return 1;
                        })
                    )
                    .then(ClientCommandManager.literal("version")
                        .executes(context -> {
                            showVersion(context.getSource().getClient());
                            return 1;
                        })
                    )
                    .then(ClientCommandManager.literal("scan")
                        .executes(context -> {
                            scanNow(context.getSource().getClient());
                            return 1;
                        })
                    )
            );
        });
    }

    private void extractPlugin(String channel) {
        String namespace = channel.contains(":") ? channel.split(":")[0] : channel;
        namespace = namespace.trim();
        if (namespace.isEmpty() || namespace.equals("minecraft")
                || namespace.equals("fabric") || namespace.equals("fml")
                || namespace.equals("forge")) return;

        for (String known : KNOWN_PLUGINS) {
            if (namespace.equalsIgnoreCase(known) || channel.contains(known)) {
                detectedPlugins.add(capitalize(known));
                return;
            }
        }
        detectedPlugins.add(capitalize(namespace));
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void showPlugins(MinecraftClient client) {
        if (client.player == null) return;

        // Do a live scan first
        doLiveScan(client);

        client.player.sendMessage(
            Text.literal("=== Plugin Detector ===").formatted(Formatting.GOLD), false);

        // Show server brand
        if (client.getNetworkHandler() != null) {
            String brand = client.getNetworkHandler().getBrand();
            if (brand != null && !brand.isEmpty()) {
                client.player.sendMessage(
                    Text.literal("Server: ").formatted(Formatting.YELLOW)
                        .append(Text.literal(brand).formatted(Formatting.WHITE)), false);
            }
        }

        if (detectedPlugins.isEmpty() && detectedChannels.isEmpty()) {
            client.player.sendMessage(
                Text.literal("No plugins detected. Try /server scan to probe the server.")
                    .formatted(Formatting.RED), false);
            return;
        }

        // Show plugins
        List<String> plugins = new ArrayList<>(detectedPlugins);
        plugins.removeIf(p -> p.startsWith("Brand:"));
        java.util.Collections.sort(plugins);

        if (!plugins.isEmpty()) {
            client.player.sendMessage(
                Text.literal("Plugins (" + plugins.size() + "): ")
                    .formatted(Formatting.GREEN)
                    .append(Text.literal(String.join(", ", plugins))
                    .formatted(Formatting.WHITE)), false);
        }

        // Show raw channels
        if (!detectedChannels.isEmpty()) {
            List<String> channels = new ArrayList<>(detectedChannels);
            java.util.Collections.sort(channels);
            client.player.sendMessage(
                Text.literal("Channels (" + channels.size() + "):")
                    .formatted(Formatting.AQUA), false);
            for (String ch : channels) {
                client.player.sendMessage(
                    Text.literal("  " + ch).formatted(Formatting.GRAY), false);
            }
        }
    }

    private void showVersion(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) return;
        String brand = client.getNetworkHandler().getBrand();
        client.player.sendMessage(
            Text.literal("Server brand: ").formatted(Formatting.YELLOW)
                .append(Text.literal(brand != null ? brand : "Unknown")
                .formatted(Formatting.WHITE)), false);
    }

    private void scanNow(MinecraftClient client) {
        if (client.player == null) return;
        doLiveScan(client);
        client.player.sendMessage(
            Text.literal("Scan complete! Found " + detectedChannels.size()
                + " channels, " + detectedPlugins.size() + " plugins. Use /server plugins to view.")
                .formatted(Formatting.GREEN), false);
    }

    private void doLiveScan(MinecraftClient client) {
        if (client.getNetworkHandler() == null) return;
        try {
            // Deep reflection scan of network handler for channel data
            scanObject(client.getNetworkHandler(), 0);
        } catch (Exception ignored) {}
    }

    private void scanObject(Object obj, int depth) {
        if (obj == null || depth > 3) return;
        try {
            Class<?> cls = obj.getClass();
            while (cls != null && cls != Object.class) {
                for (java.lang.reflect.Field field : cls.getDeclaredFields()) {
                    try {
                        field.setAccessible(true);
                        Object val = field.get(obj);
                        if (val == null) continue;

                        if (val instanceof net.minecraft.util.Identifier id) {
                            String s = id.toString().toLowerCase();
                            if (!s.startsWith("minecraft:") && !s.startsWith("fabric:")) {
                                detectedChannels.add(s);
                                extractPlugin(s);
                            }
                        } else if (val instanceof Set<?> set) {
                            for (Object item : set) {
                                if (item instanceof net.minecraft.util.Identifier id) {
                                    String s = id.toString().toLowerCase();
                                    if (!s.startsWith("minecraft:") && !s.startsWith("fabric:")) {
                                        detectedChannels.add(s);
                                        extractPlugin(s);
                                    }
                                } else if (item instanceof String s) {
                                    if (s.contains(":") && !s.startsWith("minecraft")
                                            && !s.startsWith("fabric")) {
                                        detectedChannels.add(s.toLowerCase());
                                        extractPlugin(s.toLowerCase());
                                    }
                                }
                            }
                        } else if (val instanceof List<?> list) {
                            for (Object item : list) {
                                if (item instanceof net.minecraft.util.Identifier id) {
                                    String s = id.toString().toLowerCase();
                                    if (!s.startsWith("minecraft:") && !s.startsWith("fabric:")) {
                                        detectedChannels.add(s);
                                        extractPlugin(s);
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
                cls = cls.getSuperclass();
            }
        } catch (Exception ignored) {}
    }
}
