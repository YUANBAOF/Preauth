package com.example.premiumauth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 记录“用正版名字、但客户端无法通过 Mojang 验证”的 (名字, IP)。
 * 这样的连接下次起会被当作盗版玩家（走注册/登录，并带前缀），
 * 不会影响真正的正版玩家（他们走 Mojang 验证，UUID 也不同）。
 */
public final class MarkStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private Map<String, Long> data = new HashMap<>();

    public MarkStore(Path file) {
        this.file = file;
        if (Files.exists(file)) {
            try {
                Map<String, Long> m = GSON.fromJson(Files.readString(file),
                        new TypeToken<Map<String, Long>>() {}.getType());
                if (m != null) data = m;
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    private static String key(String name, String ip) { return name.toLowerCase(Locale.ROOT) + "|" + ip; }

    public synchronized void mark(String name, String ip) {
        data.put(key(name, ip), System.currentTimeMillis());
        save();
    }

    public synchronized boolean isMarked(String name, String ip) {
        String k = key(name, ip);
        Long t = data.get(k);
        if (t == null) return false;
        long ttl = Config.markDays * 86_400_000L;
        if (System.currentTimeMillis() - t > ttl) { data.remove(k); save(); return false; }
        data.put(k, System.currentTimeMillis()); // 续期
        return true;
    }

    public synchronized void unmark(String name) {
        String prefix = name.toLowerCase(Locale.ROOT) + "|";
        data.keySet().removeIf(k -> k.startsWith(prefix));
        save();
    }

    private void save() {
        try { Files.writeString(file, GSON.toJson(data)); }
        catch (IOException e) { e.printStackTrace(); }
    }
}
