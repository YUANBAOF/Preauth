package com.example.premiumauth.mixin;

import com.example.premiumauth.Config;
import com.example.premiumauth.Lang;
import com.example.premiumauth.PremiumAuth;
import com.example.premiumauth.PremiumChecker;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.network.protocol.login.ServerboundKeyPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.UUID;

@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class LoginMixin {
    @Shadow @Final MinecraftServer server;
    @Shadow @Final public Connection connection;
    @Shadow GameProfile gameProfile;
    @Shadow public abstract void disconnect(Component reason);

    @Unique private boolean premiumauth$force;
    @Unique private boolean premiumauth$awaitingKey;
    @Unique private boolean premiumauth$premiumName;
    @Unique private boolean premiumauth$rejected;
    @Unique private String premiumauth$name;
    @Unique private String premiumauth$ip;

    /** 1) 登录开始：正版名字 → 强制 Mojang 加密验证；已被标记的盗版客户端 → 走离线 */
    @Inject(method = "handleHello", at = @At("HEAD"), cancellable = true)
    private void premiumauth$hello(ServerboundHelloPacket packet, CallbackInfo ci) {
        if (server.usesAuthentication() || connection.isMemoryConnection()) return;
        String name = packet.name();
        SocketAddress a = connection.getRemoteAddress();
        String ip = a instanceof InetSocketAddress i ? i.getAddress().getHostAddress() : String.valueOf(a);
        premiumauth$name = name;
        premiumauth$ip = ip;

        if (PremiumAuth.MARKS.isMarked(name, ip)) {
            premiumauth$premiumName = true;
            premiumauth$force = false;
            return;
        }
        Boolean r = PremiumChecker.isPremium(name);
        if (r == null) {
            disconnect(Component.literal(Lang.t(null, "api_down")));
            ci.cancel();
            return;
        }
        premiumauth$premiumName = r;
        premiumauth$force = r;
        premiumauth$awaitingKey = r;
    }

    @Redirect(method = "handleHello", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;usesAuthentication()Z"))
    private boolean premiumauth$auth(MinecraftServer s) {
        return s.usesAuthentication() || premiumauth$force;
    }

    /** 2) 客户端回了密钥 = 它有正版会话，不是盗版 */
    @Inject(method = "handleKey", at = @At("HEAD"))
    private void premiumauth$key(ServerboundKeyPacket packet, CallbackInfo ci) {
        premiumauth$awaitingKey = false;
    }

    /** 3) 连接断开时仍没收到密钥 = 盗版客户端用了正版名字，记录下来，下次按盗版处理 */
    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void premiumauth$disconnect(Component reason, CallbackInfo ci) {
        if (premiumauth$awaitingKey && premiumauth$name != null) {
            premiumauth$awaitingKey = false;
            PremiumAuth.MARKS.mark(premiumauth$name, premiumauth$ip);
        }
    }

    /** 4) 盗版玩家放行时：名字和 UUID 都保持原样，只登记“盗版”和“是否显示前缀” */
    @Inject(method = "handleAcceptedLogin", at = @At("HEAD"), cancellable = true)
    private void premiumauth$accept(CallbackInfo ci) {
        if (premiumauth$rejected) { ci.cancel(); return; }
        if (server.usesAuthentication() || connection.isMemoryConnection()) return;
        if (gameProfile == null || gameProfile.getId() != null) return; // 正版已验证

        String original = gameProfile.getName();
        UUID id = UUIDUtil.createOfflinePlayerUUID(original); // 与原版离线 UUID 一致

        // 防止别人用同名把在线的盗版玩家顶下线
        if (server.getPlayerList().getPlayer(id) != null) {
            premiumauth$rejected = true;
            disconnect(Component.literal(Lang.t(null, "already_online")));
            ci.cancel();
            return;
        }

        boolean prefix = switch (Config.prefixMode) {
            case "off" -> false;
            case "all" -> true;
            default -> premiumauth$premiumName;
        };
        PremiumAuth.CRACKED.put(id, original);
        if (prefix) PremiumAuth.PREFIXED.add(id);
    }
}
