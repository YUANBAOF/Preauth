package com.example.premiumauth;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 查询某个名字是否是 Mojang 正版账号。返回 null 表示查询失败。 */
public final class PremiumChecker {
    private record Entry(boolean premium, long expires) {}
    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();

    public static Boolean isPremium(String name) {
        if (name == null || name.length() > 16) return false;
        String key = name.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        Entry e = CACHE.get(key);
        if (e != null && e.expires() > now) return e.premium();
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/"
                    + URLEncoder.encode(name, StandardCharsets.UTF_8));
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(3000);
            c.setReadTimeout(3000);
            int code = c.getResponseCode();
            if (code == 200) { CACHE.put(key, new Entry(true, now + 24L * 3600_000)); return true; }
            if (code == 204 || code == 404) { CACHE.put(key, new Entry(false, now + 10L * 60_000)); return false; }
        } catch (IOException ignored) {}
        return e != null ? Boolean.valueOf(e.premium()) : null;
    }
    private PremiumChecker() {}
}
