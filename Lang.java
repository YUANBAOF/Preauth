package com.example.premiumauth;

import java.util.HashMap;
import java.util.Map;

public final class Lang {
    private record Msg(String color, String zh, String en) {}
    private static final Map<String, Msg> M = new HashMap<>();

    private static void p(String k, String color, String zh, String en) { M.put(k, new Msg(color, zh, en)); }

    static {
        p("verified", "a", "正版验证通过，已自动登录", "Premium account verified, logged in automatically");
        p("need_login", "e", "请输入 /login <密码> 登录", "Please log in with /login <password>");
        p("need_register", "e", "首次进服，请输入 /register <密码> <确认密码> 注册", "First visit: register with /register <password> <confirm>");
        p("cracked_note", "7", "你是非正版玩家，在聊天和玩家列表中你的名字前会显示 %s", "You are an offline (non-premium) player. A \"%s\" is shown before your name in chat and the player list");
        p("premium_no_register", null, "正版玩家无需注册", "Premium players do not need to register");
        p("already_logged", null, "你已经登录了", "You are already logged in");
        p("already_registered", null, "该账号已注册，请用 /login <密码>", "This account is already registered, use /login <password>");
        p("mismatch", null, "两次密码不一致", "Passwords do not match");
        p("too_short", null, "密码至少 6 位", "Password must be at least 6 characters");
        p("register_ok", "a", "注册成功，已登录", "Registered successfully, you are logged in");
        p("login_ok", "a", "登录成功", "Logged in successfully");
        p("not_registered", null, "请先 /register <密码> <确认密码>", "Please /register <password> <confirm> first");
        p("wrong_pw", null, "密码错误（%d/%d）", "Wrong password (%d/%d)");
        p("too_many", null, "密码错误次数过多", "Too many wrong passwords");
        p("timeout", null, "登录超时", "Login timed out");
        p("please_login", "c", "请先登录", "Please log in first");
        p("api_down", null, "无法连接 Mojang 验证服务，请稍后重试", "Cannot reach the Mojang authentication service, please try again later");
        p("already_online", null, "该名字的玩家已在线", "A player with this name is already online");
        p("unmark_ok", null, "已清除 %s 的记录", "Cleared records for %s");
    }

    /** locale 是客户端语言代码（如 zh_cn、en_us），未知时传 null。 */
    public static String t(String locale, String key, Object... args) {
        Msg m = M.get(key);
        if (m == null) return key;
        boolean zh = locale == null
                ? Config.fallbackLanguage.equals("zh")
                : Config.chineseLocales.contains(locale.toLowerCase());
        String c = m.color() == null ? "" : "§" + m.color();
        return c + String.format(zh ? m.zh() : m.en(), args);
    }
    private Lang() {}
}
