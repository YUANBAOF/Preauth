package com.example.premiumauth;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public final class Config {
    public static String fallbackLanguage = "en";
    public static Set<String> chineseLocales = Set.of("zh_cn");
    public static String prefix = "-";
    public static String prefixMode = "conflict"; // conflict | all | off
    public static int loginTimeout = 60;
    public static int maxFails = 5;
    public static int markDays = 30;

    private static final String DEFAULT = """
            # 提示语言由玩家 Minecraft 客户端语言决定：
            # 客户端语言在 chineseLocales 里 -> 中文，其他任何语言（含英语）-> 英语
            # Message language follows each player's client language:
            # listed in chineseLocales -> Chinese, anything else (incl. English) -> English
            chineseLocales=zh_cn
            # 还没拿到客户端语言时（例如登录阶段的断开提示）使用的语言: en / zh
            # Language used when the client language is not known yet: en / zh
            fallbackLanguage=en
            # 盗版玩家在聊天和玩家列表里显示的前缀（只改显示，不改真实名字）。可用 & 当颜色符号，如 &7-
            # Prefix shown before offline players in chat / tab list (display only, real name unchanged). & = color code
            prefix=-
            # conflict = 仅当名字也是正版账号名时显示前缀；all = 所有盗版玩家都显示；off = 不显示
            prefixMode=conflict
            # 未登录多少秒后踢出 / seconds before kicking unauthenticated players
            loginTimeout=60
            # 最多密码错误次数 / max wrong passwords
            maxFails=5
            # 盗版客户端使用正版名字的记录保留天数 / days to remember offline clients using premium names
            markDays=30
            """;

    public static void load(Path file) {
        try {
            if (!Files.exists(file)) Files.writeString(file, DEFAULT);
            Properties p = new Properties();
            try (Reader r = Files.newBufferedReader(file)) { p.load(r); }
            fallbackLanguage = p.getProperty("fallbackLanguage", "en").trim().equalsIgnoreCase("zh") ? "zh" : "en";
            chineseLocales = Arrays.stream(p.getProperty("chineseLocales", "zh_cn").split(","))
                    .map(s -> s.trim().toLowerCase()).filter(s -> !s.isEmpty()).collect(Collectors.toCollection(HashSet::new));
            prefix = p.getProperty("prefix", "-").trim().replace('&', '§');
            prefixMode = p.getProperty("prefixMode", "conflict").trim().toLowerCase();
            loginTimeout = Integer.parseInt(p.getProperty("loginTimeout", "60").trim());
            maxFails = Integer.parseInt(p.getProperty("maxFails", "5").trim());
            markDays = Integer.parseInt(p.getProperty("markDays", "30").trim());
        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
        }
        if (prefix.isEmpty() || prefix.length() > 16) prefix = "-";
        if (!prefixMode.equals("all") && !prefixMode.equals("off")) prefixMode = "conflict";
    }
    private Config() {}
}
