package com.pokato.skywyrms.server;

import com.pokato.skywyrms.entity.DragonEntity;
import com.pokato.skywyrms.net.AbilityPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ServerHooks {
   private ServerHooks() {
   }

   public static void onServerTick(MinecraftServer server) {
      DragonSpawner.tick(server);
   }

   public static void onAbility(ServerPlayer player, AbilityPayload payload) {
      //#if MC >= 12102
      if (player.getVehicle() instanceof DragonEntity dragon) {
         dragon.onAbilityKey(player, payload.ability() != 0);
      //#else
      int bits = payload.ability();
      if ((bits & 16) != 0) {
         if (player.getVehicle() instanceof DragonEntity dragon) {
            dragon.setRiderInput((bits & 1) != 0, (bits & 2) != 0);
         }
      } else {
         if (player.getVehicle() instanceof DragonEntity dragon) {
            dragon.onAbilityKey(player, payload.ability() != 0);
         }
      //#endif
      }
   }
}
