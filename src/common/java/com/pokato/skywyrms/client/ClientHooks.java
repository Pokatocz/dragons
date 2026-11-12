package com.pokato.skywyrms.client;

import com.pokato.skywyrms.config.ModConfig;
import com.pokato.skywyrms.entity.DragonEntity;
import com.pokato.skywyrms.net.AbilityPayload;
import com.pokato.skywyrms.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

public final class ClientHooks {
   private static boolean abilityDown;
   private static int lastInput;
   private static int ridingTicks;

   private ClientHooks() {
   }

   public static void onClientTick(Minecraft minecraft) {
      //#if AUTOTEST
      AutoTest.tick(minecraft);
      //#endif
      LocalPlayer player = minecraft.player;
      if (player == null) {
         DragonEntity.descendKeyDown = false;
         DragonEntity.sprintKeyDown = false;
         DragonEntity.ascendKeyDown = false;
         abilityDown = false;
         lastInput = 0;
         ridingTicks = 0;
      } else {
         DragonEntity dragon = player.getVehicle() instanceof DragonEntity d && d.getControllingPassenger() == player ? d : null;
         // 1.2.8, config sneakFliesDown: in the air the sneak key flies down too (the server keeps the rider on, see DragonEntity.serverTick)
         DragonEntity.descendKeyDown = dragon != null && (ModKeys.DESCEND.isDown() || minecraft.options.keyShift.isDown() && dragon.sneakFliesDown());
         DragonEntity.sprintKeyDown = dragon != null && minecraft.options.keySprint.isDown();
         DragonEntity.ascendKeyDown = dragon != null && minecraft.options.keyJump.isDown();
         //#if MC < 12102
         int input = dragon == null ? 0 : (DragonEntity.ascendKeyDown ? 1 : 0) | (DragonEntity.sprintKeyDown ? 2 : 0);
         if (input != lastInput) {
            lastInput = input;
            Platform.get().sendToServer(new AbilityPayload((byte)(16 | input)));
         }

         //#endif
         DiveWindSound.update(minecraft, player, dragon);
         boolean down = dragon != null && ModKeys.ABILITY.isDown();
         if (down != abilityDown) {
            abilityDown = down;
            if (dragon != null || !down) {
               Platform.get().sendToServer(new AbilityPayload((byte)(down ? 1 : 0)));
            }
         }

         while (ModKeys.ABILITY.consumeClick()) {
         }

         if (dragon == null) {
            ridingTicks = 0;
         } else {
            ridingTicks++;
            if (ridingTicks <= 120) {
               if (ridingTicks % 10 == 1) {
                  overlay(player, controlsHint(minecraft, dragon));
               }
            } else if (ridingTicks % 4 == 0) {
               overlay(player, HudBar.build(dragon));
            }
         }
      }
   }

   private static Component controlsHint(Minecraft minecraft, DragonEntity dragon) {
      return Component.translatable(
         ModConfig.get().sneakFliesDown ? "message.skywyrms.controls_safe" : "message.skywyrms.controls",
         new Object[]{
            minecraft.options.keyJump.getTranslatedKeyMessage(),
            ModKeys.DESCEND.getTranslatedKeyMessage(),
            minecraft.options.keySprint.getTranslatedKeyMessage(),
            ModKeys.ABILITY.getTranslatedKeyMessage(),
            Component.translatable("ability.skywyrms." + dragon.getKind().ability().id()),
            minecraft.options.keyShift.getTranslatedKeyMessage()
         }
      );
   }

   public static void overlay(LocalPlayer player, Component message) {
      //#if MC < 260100
      player.displayClientMessage(message, true);
      //#else
      player.sendOverlayMessage(message);
      //#endif
   }
}
