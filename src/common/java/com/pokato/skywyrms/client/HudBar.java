package com.pokato.skywyrms.client;

import com.pokato.skywyrms.Skywyrms;
import com.pokato.skywyrms.entity.DragonEntity;
import com.pokato.skywyrms.entity.DragonKind;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
//#if MC >= 12109
import net.minecraft.network.chat.FontDescription;
//#endif
import net.minecraft.network.chat.MutableComponent;
//#if MC >= 12109
import net.minecraft.network.chat.FontDescription.Resource;
//#else
import net.minecraft.resources.ResourceLocation;
//#endif

public final class HudBar {
   //#if MC >= 12109
   private static final FontDescription FONT = new Resource(Skywyrms.id("hud"));
   //#else
   private static final ResourceLocation FONT = Skywyrms.id("hud");
   //#endif
   private static final int WHITE = 16777215;
   private static final int SEGMENTS = 24;
   private static final int RING_ADVANCE = 20;
   private static final char ICON = '\ue010';
   private static final char RING = '\ue060';
   private static final char SEGMENT = '\ue061';

   private HudBar() {
   }

   public static Component build(DragonEntity dragon) {
      DragonKind kind = dragon.getKind();
      DragonKind blood = dragon.blood();
      int energy = Math.max(0, Math.min(100, dragon.getEnergy()));
      int lit = Math.round(energy * 24 / 100.0F);
      MutableComponent out = Component.empty();
      out.append(glyphs("\ue060" + move(-20), 16777215));

      for (int k = 0; k < lit; k++) {
         int color = blood == kind ? kind.color() : mix(kind.color(), blood.color(), k / 23.0F);
         out.append(glyphs((char)(57441 + k) + move(-20), color));
      }

      out.append(glyphs(move(4) + (char)(57360 + kind.ordinal() * 6 + blood.ordinal()) + move(4), 16777215));
      out.append(Component.literal(" "));
      //#if MC >= 12003
      out.append(Component.translatable("ability.skywyrms." + kind.ability().id()).withColor(kind.color()));
      //#else
      out.append(Component.translatable("ability.skywyrms." + kind.ability().id()).withStyle(st_ -> st_.withColor(kind.color())));
      //#endif
      if (blood != kind) {
         out.append(Component.literal(" + ").withStyle(ChatFormatting.GRAY));
         //#if MC >= 12003
         out.append(Component.translatable("element.skywyrms." + blood.element()).withColor(blood.color()));
         //#else
         out.append(Component.translatable("element.skywyrms." + blood.element()).withStyle(st_ -> st_.withColor(blood.color())));
         //#endif
      }

      out.append(Component.literal(" " + energy + "%").withStyle(ChatFormatting.GRAY));
      return out;
   }

   private static int mix(int a, int b, float t) {
      int r = Math.round((a >> 16 & 0xFF) * (1.0F - t) + (b >> 16 & 0xFF) * t);
      int g = Math.round((a >> 8 & 0xFF) * (1.0F - t) + (b >> 8 & 0xFF) * t);
      int bl = Math.round((a & 0xFF) * (1.0F - t) + (b & 0xFF) * t);
      return r << 16 | g << 8 | bl;
   }

   private static MutableComponent glyphs(String s, int rgb) {
      //#if MC >= 12104
      return Component.literal(s).withColor(rgb).withStyle(st -> st.withFont(FONT).withShadowColor(0));
      //#elif MC >= 12003 && MC < 12104
      return Component.literal(s).withColor(rgb).withStyle(st -> st.withFont(FONT));
      //#else
      return Component.literal(s).withStyle(st_ -> st_.withColor(rgb)).withStyle(st -> st.withFont(FONT));
      //#endif
   }

   static String move(int px) {
      StringBuilder sb = new StringBuilder();
      int base = px < 0 ? '\ue000' : '\ue008';
      int left = Math.abs(px);
      int[] steps = px < 0 ? new int[]{128, 64, 32, 16, 8, 4, 2, 1} : new int[]{32, 16, 8, 4, 2, 1};

      for (int step : steps) {
         for (int index = Integer.numberOfTrailingZeros(step); left >= step; left -= step) {
            sb.append((char)(base + index));
         }
      }

      return sb.toString();
   }
}
