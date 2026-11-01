package com.pokato.skywyrms.entity;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

public final class DragonInfo {
   private DragonInfo() {
   }

   public static MutableComponent element(DragonKind kind) {
      //#if MC >= 12003
      return Component.translatable("element.skywyrms." + kind.element()).withColor(kind.color());
      //#else
      return Component.translatable("element.skywyrms." + kind.element()).withStyle(st_ -> st_.withColor(kind.color()));
      //#endif
   }

   private static String percent(float factor) {
      return Integer.toString(Math.round(factor * 100.0F));
   }

   public static List<Component> geneLines(DragonGenes genes) {
      List<Component> lines = new ArrayList<>();
      MutableComponent genesLine = Component.translatable("info.skywyrms.genes", new Object[]{element(genes.body()), element(genes.blood())});
      genesLine.append(Component.literal("  "));
      genesLine.append(Component.translatable(genes.purebred() ? "info.skywyrms.purebred" : "info.skywyrms.hybrid").withStyle(ChatFormatting.GRAY));
      lines.add(genesLine);
      lines.add(
         Component.translatable("info.skywyrms.stats", new Object[]{percent(genes.healthFactor()), percent(genes.speedFactor()), percent(genes.powerFactor())})
            .withStyle(ChatFormatting.GRAY)
      );
      if (genes.vigor()) {
         lines.add(Component.translatable("info.skywyrms.vigor").withStyle(ChatFormatting.GREEN));
      }

      //#if MC >= 12003
      Component ability = Component.translatable("ability.skywyrms." + genes.body().ability().id()).withColor(genes.body().color());
      //#else
      Component ability = Component.translatable("ability.skywyrms." + genes.body().ability().id()).withStyle(st_ -> st_.withColor(genes.body().color()));
      //#endif
      if (genes.purebred()) {
         lines.add(Component.translatable("info.skywyrms.ability", new Object[]{ability}));
         lines.add(Component.translatable("info.skywyrms.pure_bonus").withStyle(ChatFormatting.GRAY));
      } else {
         lines.add(
            Component.translatable(
               "info.skywyrms.ability_infused",
               //#if MC >= 12003
               new Object[]{ability, Component.translatable("infusion.skywyrms." + genes.blood().element()).withColor(genes.blood().color())}
               //#else
               new Object[]{
                  ability, Component.translatable("infusion.skywyrms." + genes.blood().element()).withStyle(st_ -> st_.withColor(genes.blood().color()))
               }
               //#endif
            )
         );
      }

      MutableComponent traits = Component.translatable("trait.skywyrms." + genes.body().element());
      if (!genes.purebred()) {
         traits = traits.append(Component.literal(", ")).append(Component.translatable("trait.skywyrms." + genes.blood().element()));
      }

      lines.add(Component.translatable("info.skywyrms.traits", new Object[]{traits}).withStyle(ChatFormatting.GRAY));
      lines.add(Component.translatable("info.skywyrms.generation", new Object[]{genes.generation()}).withStyle(ChatFormatting.DARK_GRAY));
      return lines;
   }

   public static void send(ServerPlayer player, DragonEntity dragon) {
      DragonGenes genes = dragon.genes();
      //#if MC >= 12003
      player.sendSystemMessage(Component.literal("").append(dragon.getDisplayName().copy().withColor(dragon.getKind().color()).withStyle(ChatFormatting.BOLD)));
      //#else
      player.sendSystemMessage(
         Component.literal("").append(dragon.getDisplayName().copy().withStyle(st_ -> st_.withColor(dragon.getKind().color())).withStyle(ChatFormatting.BOLD))
      );
      //#endif
      if (dragon.isBaby()) {
         player.sendSystemMessage(
            Component.translatable("info.skywyrms.growth", new Object[]{Math.round(dragon.growth() * 100.0F)}).withStyle(ChatFormatting.YELLOW)
         );
      }

      for (Component line : geneLines(genes)) {
         player.sendSystemMessage(line);
      }
   }

   public static void sendEgg(ServerPlayer player, DragonEggEntity egg) {
      DragonGenes genes = egg.genes();
      //#if MC >= 12003
      player.sendSystemMessage(Component.literal("").append(egg.getDisplayName().copy().withColor(genes.body().color()).withStyle(ChatFormatting.BOLD)));
      //#else
      player.sendSystemMessage(
         Component.literal("").append(egg.getDisplayName().copy().withStyle(st_ -> st_.withColor(genes.body().color())).withStyle(ChatFormatting.BOLD))
      );
      //#endif
      player.sendSystemMessage(
         Component.translatable("info.skywyrms.egg_progress", new Object[]{Math.round(egg.progressFraction() * 100.0F)}).withStyle(ChatFormatting.YELLOW)
      );
      if (egg.isWarm()) {
         player.sendSystemMessage(Component.translatable("info.skywyrms.egg_warm").withStyle(ChatFormatting.GOLD));
      } else {
         player.sendSystemMessage(Component.translatable("egg.skywyrms.hint." + genes.body().element()).withStyle(ChatFormatting.GRAY));
         if (!genes.purebred()) {
            player.sendSystemMessage(Component.translatable("egg.skywyrms.hint." + genes.blood().element()).withStyle(ChatFormatting.GRAY));
         }
      }

      for (Component line : geneLines(genes)) {
         player.sendSystemMessage(line);
      }
   }
}
