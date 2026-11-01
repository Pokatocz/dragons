package com.pokato.skywyrms.neoforge;

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
import net.minecraft.core.registries.Registries;
//#if MC >= 12004
import net.minecraft.server.level.ServerPlayer;
//#endif
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTab.Builder;
import net.minecraft.world.item.CreativeModeTab.Row;
//#if MC < 12006
import net.neoforged.api.distmarker.Dist;
//#endif
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
//#if MC < 12006
import net.neoforged.fml.loading.FMLEnvironment;
//#endif
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
//#if MC < 12005
import net.neoforged.neoforge.event.TickEvent.Phase;
import net.neoforged.neoforge.event.TickEvent.ServerTickEvent;
//#endif
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
//#if MC >= 12005
import net.neoforged.neoforge.event.tick.ServerTickEvent.Post;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
//#elif MC >= 12004 && MC < 12005
import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent;
import net.neoforged.neoforge.network.handling.IPlayPayloadHandler;
//#else
import net.neoforged.neoforge.network.PlayNetworkDirection;
import net.neoforged.neoforge.network.NetworkRegistry.ChannelBuilder;
import net.neoforged.neoforge.network.simple.SimpleChannel;
//#endif
import net.neoforged.neoforge.registries.RegisterEvent;

@Mod("skywyrms")
public class SkywyrmsNeoForge {
   static Consumer<AbilityPayload> clientSender = payload -> {
      throw new IllegalStateException("sendToServer called without a client");
   };
   //#if MC < 12004
   static SimpleChannel channel;
   //#endif

   public SkywyrmsNeoForge(IEventBus modBus) {
      Platform.set(new SkywyrmsNeoForge.NeoForgePlatform());
      Skywyrms.init();
      modBus.addListener(RegisterEvent.class, SkywyrmsNeoForge::onRegister);
      modBus.addListener(EntityAttributeCreationEvent.class, event -> ModEntities.registerAttributes(event::put));
      //#if MC >= 12005
      modBus.addListener(RegisterPayloadHandlersEvent.class, SkywyrmsNeoForge::onRegisterPayloads);
      NeoForge.EVENT_BUS.addListener(Post.class, event -> ServerHooks.onServerTick(event.getServer()));
      //#elif MC >= 12004 && MC < 12005
      modBus.addListener(RegisterPayloadHandlerEvent.class, SkywyrmsNeoForge::onRegisterPayloads);
      //#else
      channel = ChannelBuilder.named(Skywyrms.id("main"))
         .networkProtocolVersion(() -> "1")
         .clientAcceptedVersions(v -> true)
         .serverAcceptedVersions(v -> true)
         .simpleChannel();
      channel.messageBuilder(AbilityPayload.class, 0, PlayNetworkDirection.PLAY_TO_SERVER)
         .encoder(AbilityPayload::write)
         .decoder(AbilityPayload::read)
         .consumerMainThread((payload, context) -> {
            if (context.getSender() != null) {
               ServerHooks.onAbility(context.getSender(), payload);
            }
         })
         .add();
      //#endif
      //#if MC < 12005
      NeoForge.EVENT_BUS.addListener(ServerTickEvent.class, event -> {
         if (event.phase == Phase.END) {
            ServerHooks.onServerTick(event.getServer());
         }
      });
      //#endif
      //#if MC < 12006
      if (FMLEnvironment.dist == Dist.CLIENT) {
         SkywyrmsNeoForgeClient.init(modBus);
      }
      //#endif
   }

   private static void onRegister(RegisterEvent event) {
      event.register(Registries.ENTITY_TYPE, helper -> ModEntities.register(helper::register));
      event.register(Registries.ITEM, helper -> ModItems.register(helper::register));
      event.register(Registries.PARTICLE_TYPE, helper -> ModParticles.register(helper::register));
      event.register(Registries.SOUND_EVENT, helper -> ModSounds.register(helper::register));
      event.register(Registries.CREATIVE_MODE_TAB, helper -> ModItems.registerTabs(helper::register));
   //#if MC >= 12004
   }

   //#endif
   //#if MC >= 12005
   private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
      event.registrar("1")
         .playToServer(AbilityPayload.TYPE, AbilityPayload.STREAM_CODEC, (payload, context) -> ServerHooks.onAbility((ServerPlayer)context.player(), payload));
   //#elif MC >= 12004 && MC < 12005
   private static void onRegisterPayloads(RegisterPayloadHandlerEvent event) {
      event.registrar("skywyrms")
         .versioned("1")
         .play(
            AbilityPayload.ID,
            AbilityPayload::read,
            handler -> handler.server(
               (IPlayPayloadHandler)(payload, context) -> context.workHandler()
                  .execute(() -> context.player().ifPresent(player -> ServerHooks.onAbility((ServerPlayer)player, payload)))
            )
         );
   //#endif
   }

   private static final class NeoForgePlatform implements Platform {
      @Override
      public String loaderName() {
         return "neoforge";
      }

      @Override
      public void sendToServer(AbilityPayload payload) {
         SkywyrmsNeoForge.clientSender.accept(payload);
      }

      @Override
      public Path configDir() {
         return FMLPaths.CONFIGDIR.get();
      }

      @Override
      public Builder creativeTabBuilder() {
         return CreativeModeTab.builder(Row.TOP, 0);
      }
   }
}
