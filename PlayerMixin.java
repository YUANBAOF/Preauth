package com.example.premiumauth.mixin;

import com.example.premiumauth.PremiumAuth;
import net.minecraft.network.protocol.game.ServerboundClientInformationPacket;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 客户端会把自己的语言设置发给服务器，这里记下来，用于选择提示语言。 */
@Mixin(ServerPlayer.class)
public abstract class PlayerMixin {
    @Inject(method = "updateOptions", at = @At("HEAD"))
    private void premiumauth$lang(ServerboundClientInformationPacket packet, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        PremiumAuth.LOCALE.put(self.getUUID(), packet.language());
    }
}
