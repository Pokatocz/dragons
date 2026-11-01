package com.pokato.skywyrms.registry;

import com.pokato.skywyrms.Skywyrms;
import com.pokato.skywyrms.entity.DragonGenes;
import com.pokato.skywyrms.entity.DragonKind;
import com.pokato.skywyrms.item.DragonEggItem;
import com.pokato.skywyrms.platform.Platform;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
//#if MC >= 12102
import net.minecraft.core.registries.Registries;
//#endif
import net.minecraft.network.chat.Component;
//#if MC >= 12102 && MC < 12111
import net.minecraft.resources.ResourceKey;
//#endif
//#if MC < 12111
import net.minecraft.resources.ResourceLocation;
//#else
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
//#endif
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.Item.Properties;

public final class ModItems {
   //#if MC < 12111
   public static final ResourceLocation MAIN_TAB_ID = Skywyrms.id("main");
   //#else
   public static final Identifier MAIN_TAB_ID = Skywyrms.id("main");
   //#endif
   private static final Map<DragonKind, Item> SPAWN_EGGS = new EnumMap<>(DragonKind.class);
   private static final Map<DragonKind, DragonEggItem> DRAGON_EGGS = new EnumMap<>(DragonKind.class);
   private static CreativeModeTab mainTab;

   private ModItems() {
   }

   //#if MC < 12111
   public static ResourceLocation spawnEggId(DragonKind kind) {
   //#else
   public static Identifier spawnEggId(DragonKind kind) {
   //#endif
      return Skywyrms.id(kind.id() + "_spawn_egg");
   }

   public static synchronized Item spawnEgg(DragonKind kind) {
      Item egg = SPAWN_EGGS.get(kind);
      if (egg == null) {
         egg = spawnEgg(spawnEggId(kind), ModEntities.type(kind));
         SPAWN_EGGS.put(kind, egg);
      }

      return egg;
   }

   //#if MC < 12111
   public static ResourceLocation dragonEggId(DragonKind kind) {
   //#else
   public static Identifier dragonEggId(DragonKind kind) {
   //#endif
      return Skywyrms.id(kind.id() + "_egg");
   }

   public static synchronized DragonEggItem dragonEgg(DragonKind kind) {
      DragonEggItem egg = DRAGON_EGGS.get(kind);
      if (egg == null) {
         egg = new DragonEggItem(kind, properties(dragonEggId(kind)).stacksTo(1).fireResistant().rarity(Rarity.RARE));
         DRAGON_EGGS.put(kind, egg);
      }

      return egg;
   }

   public static ItemStack eggStack(DragonGenes genes, UUID owner, int progress) {
      ItemStack stack = new ItemStack(dragonEgg(genes.body()));
      DragonEggItem.write(stack, genes, owner, progress);
      return stack;
   }

   public static CreativeModeTab mainTab() {
      if (mainTab == null) {
         mainTab = Platform.get()
            .creativeTabBuilder()
            .title(Component.translatable("itemGroup.skywyrms.main"))
            .icon(() -> new ItemStack(spawnEgg(DragonKind.EMBER)))
            .displayItems((parameters, output) -> {
               for (DragonKind kind : DragonKind.values()) {
                  output.accept(spawnEgg(kind));
               }

               for (DragonKind kind : DragonKind.values()) {
                  output.accept(dragonEgg(kind));
               }
            })
            .build();
      }

      return mainTab;
   }

   //#if MC < 12111
   public static Properties properties(ResourceLocation id) {
   //#endif
      //#if MC < 12102
      return new Properties();
      //#elif MC >= 12111
   public static Properties properties(Identifier id) {
      //#endif
      //#if MC >= 12102
      return new Properties().setId(ResourceKey.create(Registries.ITEM, id));
      //#endif
   }

   //#if MC < 12111
   private static Item spawnEgg(ResourceLocation id, EntityType<? extends Mob> type) {
   //#endif
      //#if MC < 12104
      return new SpawnEggItem(type, 16777215, 16777215, properties(id));
      //#elif MC >= 12111
   private static Item spawnEgg(Identifier id, EntityType<? extends Mob> type) {
      //#endif
      //#if MC >= 12109
      return new SpawnEggItem(properties(id).spawnEgg(type));
      //#elif MC >= 12104 && MC < 12109
      return new SpawnEggItem(type, properties(id));
      //#endif
   }

   //#if MC < 12111
   public static void register(BiConsumer<ResourceLocation, Item> registrar) {
   //#else
   public static void register(BiConsumer<Identifier, Item> registrar) {
   //#endif
      for (DragonKind kind : DragonKind.values()) {
         registrar.accept(spawnEggId(kind), spawnEgg(kind));
      }

      for (DragonKind kind : DragonKind.values()) {
         registrar.accept(dragonEggId(kind), dragonEgg(kind));
      }
   }

   //#if MC < 12111
   public static void registerTabs(BiConsumer<ResourceLocation, CreativeModeTab> registrar) {
   //#else
   public static void registerTabs(BiConsumer<Identifier, CreativeModeTab> registrar) {
   //#endif
      registrar.accept(MAIN_TAB_ID, mainTab());
   }
}
