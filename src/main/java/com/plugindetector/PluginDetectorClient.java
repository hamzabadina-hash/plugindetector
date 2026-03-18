package com.plugindetector;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PluginDetectorClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("plugindetector");

    // Stores detected plugin channels
    private static final Set<String> detectedChannels = new HashSet<>();
    // Stores detected plugins extracted from channels
    private static final Set<String> detectedPlugins = new HashSet<>();

    // Known plugin channel prefixes to identify plugins
    private static final String[] KNOWN_CHANNELS = {
        "bungeecord", "bungeecord:main",
        "velocity", "velocity:player_info",
        "essentials", "essentialsx",
        "worldedit", "fawe",
        "luckperms",
        "litebans",
        "advancedban",
        "coreprotect",
        "griefprevention",
        "towny",
        "factions",
        "lands",
        "plotsquared",
        "grief", "anticheat",
        "nocheatplus", "aac", "matrix", "spartan", "vulcan", "grim",
        "viaversion", "viabackwards",
        "protocollib", "protocolsupport",
        "citizens", "mythicmobs",
        "shopguiplus", "shopgui",
        "mcmmo",
        "aureliumskills", "auraskills",
        "deluxemenus", "chestsort",
        "multiverse", "mv",
        "worldguard",
        "skript",
        "vault",
        "placeholderapi",
        "tab", "tablist",
        "chatcontrol",
        "clearlag",
        "combatlog",
        "pvpmanager",
        "quickshop",
        "bentobox", "bskyblock",
        "imageframe",
        "minepacks",
        "headdb",
        "itemsadder",
        "oraxen",
        "mythiccrucible",
        "superiorskyblock",
        "iridiumskyblock",
        "askyblock",
        "uSkyBlock",
        "slimefun",
        "jobs",
        "tokenenchant",
        "tokenmanager",
        "playerpoints",
        "economy",
        "craftconomy",
        "iconomy",
        "geyser",
        "floodgate",
        "discord", "discordsrv",
        "dynmap",
        "bluemap",
        "squaremap",
        "cmi",
        "nucleus",
        "redisbungee",
        "redispy",
        "signshop",
        "chest", "hopper",
        "spark",
        "timings",
        "paper", "purpur", "folia",
        "spigot", "bukkit",
        "waterfall", "travertine",
        "silkspawners",
        "spawnercontrol",
        "wildstacker",
        "stackmob",
        "xconomy",
        "chronicler",
        "prism",
        "hawkeye",
        "minigames",
        "skywars",
        "bedwars",
        "eggwars",
        "uhc",
        "hungergames",
        "kitpvp",
        "prison",
        "rankup",
        "automessage",
        "autorank",
        "cratesplus", "crazycrates",
        "excellentcrates",
        "crazy", "crate",
        "advancedachievements",
        "achievements",
        "quests",
        "betonquest"
    };

    @Override
    public void onInitializeClient() {
        LOGGER.info("Plugin Detector loaded! Use /server plugins to detect server plugins.");

        // Listen for incoming plugin channel packets
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            detectedChannels.clear();
            detectedPlugins.clear();

            // Send REGISTER packet to trigger server channel response
            // This asks the server to tell us what channels it supports
            try {
                net.minecraft.network.PacketByteBuf buf = PacketByteBufs.create();
                buf.writeBytes("REGISTER".getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {}
        });

        // Intercept custom payload packets to detect plugin channels
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            detectedChannels.clear();
            detectedPlugins.clear();
        });

        // Register the /server plugins command
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
                            showServerVersion(context.getSource().getClient());
                            return 1;
                        })
                    )
            );
        });

        // Listen for plugin channel registrations from server
        // This catches the REGISTER channel which lists all plugin channels
        ClientPlayNetworking.registerGlobalReceiver(
            new net.minecraft.util.Identifier("minecraft", "register"),
            (client, handler, buf, responseSender) -> {
                try {
                    byte[] bytes = new byte[buf.readableBytes()];
                    buf.readBytes(bytes);
                    String channelList = new String(bytes, StandardCharsets.UTF_8);
                    // Channels are null-separated
                    String[] channels = channelList.split("\0");
                    for (String channel : channels) {
                        channel = channel.trim().toLowerCase();
                        if (!channel.isEmpty()) {
                            detectedChannels.add(channel);
                            extractPlugin(channel);
                        }
                    }
                } catch (Exception ignored) {}
            }
        );

        // Also catch bungeecord channel
        ClientPlayNetworking.registerGlobalReceiver(
            new net.minecraft.util.Identifier("bungeecord", "main"),
            (client, handler, buf, responseSender) -> {
                detectedChannels.add("bungeecord:main");
                detectedPlugins.add("BungeeCord/Waterfall");
            }
        );

        // Catch velocity channel
        try {
            ClientPlayNetworking.registerGlobalReceiver(
                new net.minecraft.util.Identifier("velocity", "player_info"),
                (client, handler, buf, responseSender) -> {
                    detectedChannels.add("velocity:player_info");
                    detectedPlugins.add("Velocity");
                }
            );
        } catch (Exception ignored) {}
    }

    private void extractPlugin(String channel) {
        // Channel format is usually "pluginname:subcommand"
        String pluginName = channel.contains(":") ?
                channel.split(":")[0] : channel;

        // Clean up and capitalize
        pluginName = pluginName.replace("_", " ").replace("-", " ").trim();

        // Check against known plugins
        for (String known : KNOWN_CHANNELS) {
            if (channel.contains(known.toLowerCase())) {
                String display = capitalize(known.split(":")[0]);
                detectedPlugins.add(display);
                return;
            }
        }

        // Add unknown plugin from channel namespace
        if (!pluginName.isEmpty() && !pluginName.equals("minecraft")
                && !pluginName.equals("fabric") && !pluginName.equals("fml")) {
            detectedPlugins.add(capitalize(pluginName));
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void showPlugins(MinecraftClient client) {
        if (client.player == null) return;

        client.player.sendMessage(
            Text.literal("=== Server Plugin Detection ===").formatted(Formatting.GOLD),
            false
        );

        // Try to get server brand
        String brand = "Unknown";
        if (client.getNetworkHandler() != null) {
            String serverBrand = client.getNetworkHandler().getBrand();
            if (serverBrand != null) brand = serverBrand;
        }

        client.player.sendMessage(
            Text.literal("Server Brand: ").formatted(Formatting.YELLOW)
                .append(Text.literal(brand).formatted(Formatting.WHITE)),
            false
        );

        if (detectedPlugins.isEmpty() && detectedChannels.isEmpty()) {
            client.player.sendMessage(
                Text.literal("No plugins detected yet. Try moving or interacting to trigger channel detection.")
                    .formatted(Formatting.RED),
                false
            );

            // Try to trigger detection by sending a plugin message
            triggerDetection(client);
            return;
        }

        if (!detectedPlugins.isEmpty()) {
            List<String> sorted = new ArrayList<>(detectedPlugins);
            java.util.Collections.sort(sorted);

            client.player.sendMessage(
                Text.literal("Detected Plugins (" + sorted.size() + "):")
                    .formatted(Formatting.GREEN),
                false
            );

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < sorted.size(); i++) {
                sb.append(sorted.get(i));
                if (i < sorted.size() - 1) sb.append(", ");
            }
            client.player.sendMessage(
                Text.literal(sb.toString()).formatted(Formatting.WHITE),
                false
            );
        }

        if (!detectedChannels.isEmpty()) {
            client.player.sendMessage(
                Text.literal("Raw Channels (" + detectedChannels.size() + "):")
                    .formatted(Formatting.AQUA),
                false
            );
            List<String> sorted = new ArrayList<>(detectedChannels);
            java.util.Collections.sort(sorted);
            for (String ch : sorted) {
                client.player.sendMessage(
                    Text.literal("  - " + ch).formatted(Formatting.GRAY),
                    false
                );
            }
        }
    }

    private void showServerVersion(MinecraftClient client) {
        if (client.player == null) return;
        String brand = "Unknown";
        if (client.getNetworkHandler() != null) {
            String serverBrand = client.getNetworkHandler().getBrand();
            if (serverBrand != null) brand = serverBrand;
        }
        client.player.sendMessage(
            Text.literal("Server: ").formatted(Formatting.YELLOW)
                .append(Text.literal(brand).formatted(Formatting.WHITE)),
            false
        );
    }

    private void triggerDetection(MinecraftClient client) {
        if (client.getNetworkHandler() == null) return;
        try {
            // Send a BungeeCord plugin message to probe
            net.minecraft.network.PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString("GetServer");
            ClientPlayNetworking.send(
                new net.minecraft.util.Identifier("bungeecord", "main"),
                buf
            );
        } catch (Exception ignored) {}
    }
}
