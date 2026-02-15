package com.eliteessentials.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.io.netty.NettyUtil;
import io.netty.channel.Channel;

import java.io.*;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages IP-based bans.
 * Persists to ipbans.json keyed by IP address string.
 * 
 * IP extraction uses PacketHandler -> Netty Channel -> NettyUtil.getRemoteSocketAddress()
 * which works with both TCP and QUIC connections.
 */
public class IpBanService {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Type IPBAN_MAP_TYPE = new TypeToken<Map<String, IpBanEntry>>(){}.getType();

    private final File ipBanFile;
    private final Object fileLock = new Object();
    private final Map<String, IpBanEntry> ipBans = new ConcurrentHashMap<>();

    public IpBanService(File dataFolder) {
        this.ipBanFile = new File(dataFolder, "ipbans.json");
        load();
    }

    public void load() {
        if (!ipBanFile.exists()) {
            return;
        }
        synchronized (fileLock) {
            try (Reader reader = new InputStreamReader(new FileInputStream(ipBanFile), StandardCharsets.UTF_8)) {
                Map<String, IpBanEntry> loaded = gson.fromJson(reader, IPBAN_MAP_TYPE);
                ipBans.clear();
                if (loaded != null) {
                    ipBans.putAll(loaded);
                }
                logger.info("[IpBanService] Loaded " + ipBans.size() + " IP bans.");
            } catch (IOException e) {
                logger.severe("Could not load ipbans.json: " + e.getMessage());
            }
        }
    }

    private void save() {
        synchronized (fileLock) {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(ipBanFile), StandardCharsets.UTF_8)) {
                gson.toJson(ipBans, IPBAN_MAP_TYPE, writer);
            } catch (IOException e) {
                logger.severe("Could not save ipbans.json: " + e.getMessage());
            }
        }
    }

    /**
     * Extract IP address from a PacketHandler via Netty channel.
     * @return IP string or null if extraction fails
     */
    public static String getIpFromPacketHandler(PacketHandler packetHandler) {
        try {
            if (packetHandler == null) return null;
            Channel channel = packetHandler.getChannel();
            if (channel == null) return null;
            SocketAddress addr = NettyUtil.getRemoteSocketAddress(channel);
            if (addr instanceof InetSocketAddress) {
                return ((InetSocketAddress) addr).getAddress().getHostAddress();
            }
        } catch (Exception e) {
            logger.warning("[IpBanService] Error extracting IP from PacketHandler: " + e.getMessage());
        }
        return null;
    }

    public boolean banIp(String ip, UUID playerId, String playerName, String bannedBy, String reason) {
        if (ipBans.containsKey(ip)) {
            return false;
        }
        IpBanEntry entry = new IpBanEntry();
        entry.playerUuid = playerId != null ? playerId.toString() : null;
        entry.playerName = playerName;
        entry.bannedBy = bannedBy;
        entry.reason = reason != null && !reason.trim().isEmpty() ? reason : "No reason specified";
        entry.bannedAt = System.currentTimeMillis();
        ipBans.put(ip, entry);
        save();
        logger.info("[IpBanService] IP banned: " + ip + " (" + playerName + ") - " + entry.reason);
        return true;
    }

    public boolean unbanIp(String ip) {
        if (ipBans.remove(ip) != null) {
            save();
            logger.info("[IpBanService] IP unbanned: " + ip);
            return true;
        }
        return false;
    }

    /**
     * Find and unban an IP by the player name associated with it.
     * @return the IP that was unbanned, or null if not found
     */
    public String unbanByName(String playerName) {
        for (Map.Entry<String, IpBanEntry> entry : ipBans.entrySet()) {
            if (entry.getValue().playerName != null && entry.getValue().playerName.equalsIgnoreCase(playerName)) {
                String ip = entry.getKey();
                ipBans.remove(ip);
                save();
                logger.info("[IpBanService] IP unbanned by name: " + ip + " (" + playerName + ")");
                return ip;
            }
        }
        return null;
    }

    public boolean isBanned(String ip) {
        return ip != null && ipBans.containsKey(ip);
    }

    public IpBanEntry getBanEntry(String ip) {
        return ipBans.get(ip);
    }

    public void reload() {
        load();
    }

    public static class IpBanEntry {
        public String playerUuid;
        public String playerName;
        public String bannedBy;
        public String reason;
        public long bannedAt;
    }
}
