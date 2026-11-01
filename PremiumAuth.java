package com.example.premiumauth;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod(PremiumAuth.MODID)
public class PremiumAuth {
    public static final String MODID = "premiumauth";

    /** 盗版玩家：UUID -> 真实名字。由登录 Mixin 写入。 */
    public static final Map<UUID, String> CRACKED = new ConcurrentHashMap<>();
    /** 需要在聊天/玩家列表里显示前缀的盗版玩家。 */
    public static final Set<UUID> PREFIXED = ConcurrentHashMap.newKeySet();
    /** 玩家客户端语言（zh_cn / en_us ...）。由 PlayerMixin 写入。 */
    public static final Map<UUID, String> LOCALE = new ConcurrentHashMap<>();
    public static MarkStore MARKS;

    private static final int LANG_WAIT_TICKS = 40; // 最多等 2 秒拿客户端语言
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> GREET = new ConcurrentHashMap<>(); // 等待语言的正版玩家
    private final AuthStore store;

    private static final class Pending {
        final double x, y, z; final float yRot, xRot;
        int ticks = Config.loginTimeout * 20; int fails = 0; int age = 0; boolean prompted = false;
        Pending(ServerPlayer p) { x = p.getX(); y = p.getY(); z = p.getZ(); yRot = p.getYRot(); xRot = p.getXRot(); }
    }

    public PremiumAuth() {
        // 纯服务端模组：客户端不用装
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> IExtensionPoint.DisplayTest.IGNORESERVERONLY, (a, b) -> true));
        Path cfg = FMLPaths.CONFIGDIR.get();
        Config.load(cfg.resolve("premiumauth.properties"));
        store = new AuthStore(cfg.resolve("premiumauth-accounts.json"));
        MARKS = new MarkStore(cfg.resolve("premiumauth-marks.json"));
        MinecraftForge.EVENT_BUS.register(this);
    }

    // ---------- 工具 ----------
    private static boolean locked(Player p) { return p != null && PENDING.containsKey(p.getUUID()); }
    private static String loc(Player p) { return p == null ? null : LOCALE.get(p.getUUID()); }
    private static void send(ServerPlayer p, String key, Object... a) {
        p.sendSystemMessage(Component.literal(Lang.t(loc(p), key, a)));
    }
    private static String tr(CommandContext<CommandSourceStack> c, String key, Object... a) {
        return Lang.t(c.getSource().getEntity() instanceof Player p ? loc(p) : null, key, a);
    }
    private static void fail(CommandContext<CommandSourceStack> c, String key, Object... a) {
        c.getSource().sendFailure(Component.literal(tr(c, key, a)));
    }
    private static void ok(CommandContext<CommandSourceStack> c, String key, Object... a) {
        String m = tr(c, key, a);
        c.getSource().sendSuccess(() -> Component.literal(m), false);
    }

    // ---------- 登录 / 退出 ----------
    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        if (sp.server.usesAuthentication()) return;
        if (!CRACKED.containsKey(sp.getUUID())) { GREET.put(sp.getUUID(), 0); return; } // 通过 Mojang 验证的正版
        PENDING.put(sp.getUUID(), new Pending(sp)); // 提示语在 tick 里等拿到客户端语言后再发
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        PENDING.remove(id); GREET.remove(id); CRACKED.remove(id); PREFIXED.remove(id); LOCALE.remove(id);
    }

    // ---------- 只改“显示”，不改真实名字 ----------
    @SubscribeEvent
    public void onNameFormat(PlayerEvent.NameFormat e) {
        if (PREFIXED.contains(e.getEntity().getUUID()))
            e.setDisplayname(Component.literal(Config.prefix).append(e.getDisplayname()));
    }

    @SubscribeEvent
    public void onTabName(PlayerEvent.TabListNameFormat e) {
        if (!PREFIXED.contains(e.getEntity().getUUID())) return;
        Component base = e.getDisplayName() != null ? e.getDisplayName() : e.getEntity().getName();
        e.setDisplayName(Component.literal(Config.prefix).append(base));
    }

    // ---------- 命令 ----------
    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("register")
                .then(Commands.argument("password", StringArgumentType.word())
                        .then(Commands.argument("confirm", StringArgumentType.word())
                                .executes(this::register))));
        d.register(Commands.literal("login")
                .then(Commands.argument("password", StringArgumentType.word())
                        .executes(this::login)));
        d.register(Commands.literal("premiumauth").requires(s -> s.hasPermission(3))
                .then(Commands.literal("unmark")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(c -> {
                                    String n = StringArgumentType.getString(c, "name");
                                    MARKS.unmark(n);
                                    String m = tr(c, "unmark_ok", n);
                                    c.getSource().sendSuccess(() -> Component.literal(m), true);
                                    return 1;
                                }))));
    }

    private int register(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String name = CRACKED.get(p.getUUID());
        String pw = StringArgumentType.getString(c, "password");
        String cf = StringArgumentType.getString(c, "confirm");
        if (name == null) { fail(c, "premium_no_register"); return 0; }
        if (!locked(p)) { fail(c, "already_logged"); return 0; }
        if (store.has(name)) { fail(c, "already_registered"); return 0; }
        if (!pw.equals(cf)) { fail(c, "mismatch"); return 0; }
        if (pw.length() < 6) { fail(c, "too_short"); return 0; }
        store.register(name, pw);
        PENDING.remove(p.getUUID());
        ok(c, "register_ok");
        return 1;
    }

    private int login(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String name = CRACKED.get(p.getUUID());
        Pending pend = PENDING.get(p.getUUID());
        if (name == null || pend == null) { fail(c, "already_logged"); return 0; }
        if (!store.has(name)) { fail(c, "not_registered"); return 0; }
        if (store.check(name, StringArgumentType.getString(c, "password"))) {
            PENDING.remove(p.getUUID());
            ok(c, "login_ok");
            return 1;
        }
        if (++pend.fails >= Config.maxFails) p.connection.disconnect(Component.literal(tr(c, "too_many")));
        else fail(c, "wrong_pw", pend.fails, Config.maxFails);
        return 0;
    }

    // ---------- 每 tick：发提示、冻结未登录玩家、超时踢出 ----------
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || (PENDING.isEmpty() && GREET.isEmpty())) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (UUID id : new ArrayList<>(GREET.keySet())) {
            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            int age = GREET.getOrDefault(id, 0) + 1;
            if (sp == null) { GREET.remove(id); continue; }
            if (LOCALE.containsKey(id) || age > LANG_WAIT_TICKS) { send(sp, "verified"); GREET.remove(id); }
            else GREET.put(id, age);
        }

        for (UUID id : new ArrayList<>(PENDING.keySet())) {
            Pending p = PENDING.get(id);
            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            if (p == null || sp == null) { PENDING.remove(id); continue; }
            if (!p.prompted && (LOCALE.containsKey(id) || ++p.age > LANG_WAIT_TICKS)) {
                p.prompted = true;
                send(sp, store.has(CRACKED.get(id)) ? "need_login" : "need_register");
                if (PREFIXED.contains(id)) send(sp, "cracked_note", Config.prefix.replace("§", "&"));
            }
            if (--p.ticks <= 0) { sp.connection.disconnect(Component.literal(Lang.t(loc(sp), "timeout"))); continue; }
            double dx = sp.getX() - p.x, dy = sp.getY() - p.y, dz = sp.getZ() - p.z;
            if (dx * dx + dy * dy + dz * dz > 0.25) sp.connection.teleport(p.x, p.y, p.z, p.yRot, p.xRot);
        }
    }

    // ---------- 未登录限制 ----------
    @SubscribeEvent
    public void onChat(ServerChatEvent e) { if (locked(e.getPlayer())) e.setCanceled(true); }

    @SubscribeEvent
    public void onCommand(CommandEvent e) {
        if (!(e.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer p) || !locked(p)) return;
        String s = e.getParseResults().getReader().getString().trim();
        if (s.startsWith("/")) s = s.substring(1);
        String root = s.split(" ")[0].toLowerCase();
        if (!root.equals("login") && !root.equals("register")) {
            e.setCanceled(true);
            send(p, "please_login");
        }
    }

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent e) { if (e.isCancelable() && locked(e.getEntity())) e.setCanceled(true); }

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent e) { if (locked(e.getPlayer())) e.setCanceled(true); }

    @SubscribeEvent
    public void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (e.getEntity() instanceof Player p && locked(p)) e.setCanceled(true);
    }

    @SubscribeEvent
    public void onPickup(EntityItemPickupEvent e) { if (locked(e.getEntity())) e.setCanceled(true); }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent e) { if (locked(e.getEntity())) e.setCanceled(true); }

    @SubscribeEvent
    public void onHurt(LivingAttackEvent e) {
        if (e.getEntity() instanceof Player p && locked(p)) e.setCanceled(true);
    }
}
