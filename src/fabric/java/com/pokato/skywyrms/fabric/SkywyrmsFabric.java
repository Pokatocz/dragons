package com.pokato.skywyrms.fabric;

import com.pokato.skywyrms.Skywyrms;
import com.pokato.skywyrms.net.AbilityPayload;
import com.pokato.skywyrms.platform.Platform;
import com.pokato.skywyrms.registry.ModEntities;
import com.pokato.skywyrms.registry.ModItems;
import com.pokato.skywyrms.registry.ModParticles;
import com.pokato.skywyrms.registry.ModSounds;
import com.pokato.skywyrms.server.ServerHooks;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.fabricmc.api.ModInitializer;
//#if MC >= 260100
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
//#endif
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
//#if MC < 260100
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
//#endif
//#if MC >= 12005
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
//#endif
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab.Builder;

public class SkywyrmsFabric implements ModInitializer {
   static Consumer<AbilityPayload> clientSender = payload -> {
      throw new IllegalStateException("sendToServer called without a client");
   };

   public void onInitialize() {
      Platform.set(new SkywyrmsFabric.FabricPlatform());
      Skywyrms.init();
      ModEntities.register((id, type) -> Registry.register(BuiltInRegistries.ENTITY_TYPE, id, type));
      ModEntities.registerAttributes(FabricDefaultAttributeRegistry::register);
      ModItems.register((id, item) -> Registry.register(BuiltInRegistries.ITEM, id, item));
      ModParticles.register((id, type) -> Registry.register(BuiltInRegistries.PARTICLE_TYPE, id, type));
      ModSounds.register((id, sound) -> Registry.register(BuiltInRegistries.SOUND_EVENT, id, sound));
      ModItems.registerTabs((id, tab) -> Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id, tab));
      //#if MC >= 12005 && MC < 260100
      PayloadTypeRegistry.playC2S().register(AbilityPayload.TYPE, AbilityPayload.STREAM_CODEC);
      //#elif MC >= 260100
      PayloadTypeRegistry.serverboundPlay().register(AbilityPayload.TYPE, AbilityPayload.STREAM_CODEC);
      //#endif
      //#if MC >= 12005
      ServerPlayNetworking.registerGlobalReceiver(AbilityPayload.TYPE, (payload, context) -> ServerHooks.onAbility(context.player(), payload));
      //#else
      ServerPlayNetworking.registerGlobalReceiver(AbilityPayload.ID, (server, player, handler, buf, responseSender) -> {
         AbilityPayload payload = AbilityPayload.read(buf);
         server.execute(() -> ServerHooks.onAbility(player, payload));
      });
      //#endif
      ServerTickEvents.END_SERVER_TICK.register(ServerHooks::onServerTick);
   }

   private static final class FabricPlatform implements Platform {
      @Override
      public String loaderName() {
         return "fabric";
      }

      @Override
      public void sendToServer(AbilityPayload payload) {
         SkywyrmsFabric.clientSender.accept(payload);
      }

      @Override
      public Path configDir() {
         return FabricLoader.getInstance().getConfigDir();
      }

      @Override
      public Builder creativeTabBuilder() {
         //#if MC < 260100
         return FabricItemGroup.builder();
         //#else
         return FabricCreativeModeTab.builder();
         //#endif
      }
   }
}
