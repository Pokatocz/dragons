package com.pokato.skywyrms.forge;

import com.pokato.skywyrms.Skywyrms;
import com.pokato.skywyrms.net.AbilityPayload;
import com.pokato.skywyrms.platform.Platform;
import com.pokato.skywyrms.registry.ModEntities;
import com.pokato.skywyrms.registry.ModItems;
import com.pokato.skywyrms.registry.ModParticles;
import com.pokato.skywyrms.registry.ModSounds;
import com.pokato.skywyrms.server.ServerHooks;
import java.nio.file.Path;
//#if MC < 12002
import java.util.Optional;
//#endif
import net.minecraft.core.registries.Registries;
//#if MC >= 12006
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//#endif
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTab.Builder;
import net.minecraft.world.item.CreativeModeTab.Row;
import net.minecraftforge.api.distmarker.Dist;
//#if MC < 12106
import net.minecraftforge.common.MinecraftForge;
//#endif
//#if MC >= 12006
import net.minecraftforge.event.TickEvent.ServerTickEvent.Post;
//#else
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
//#endif
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
//#if MC >= 12002
import net.minecraftforge.event.network.CustomPayloadEvent.Context;
//#endif
//#if MC >= 12106
import net.minecraftforge.eventbus.api.bus.BusGroup;
//#else
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
//#endif
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
//#if MC >= 12006
import net.minecraftforge.network.Channel;
//#endif
//#if MC >= 12002
import net.minecraftforge.network.ChannelBuilder;
//#endif
//#if MC < 12006
import net.minecraftforge.network.NetworkDirection;
//#endif
//#if MC >= 12002
import net.minecraftforge.network.PacketDistributor;
//#endif
//#if MC >= 12006
import net.minecraftforge.network.payload.PayloadFlow;
//#elif MC >= 12002 && MC < 12006
import net.minecraftforge.network.SimpleChannel;
//#else
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.NetworkEvent.Context;
import net.minecraftforge.network.simple.SimpleChannel;
//#endif
import net.minecraftforge.registries.RegisterEvent;

@Mod("skywyrms")
public class SkywyrmsForge {
   //#if MC >= 12006
   private static Channel<CustomPacketPayload> channel;
   //#else
   private static SimpleChannel channel;
   //#endif

   //#if MC >= 12106
   public SkywyrmsForge(FMLJavaModLoadingContext context) {
      BusGroup modBus = context.getModBusGroup();
   //#else
   public SkywyrmsForge() {
      IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
   //#endif
      Platform.set(new SkywyrmsForge.ForgePlatform());
      Skywyrms.init();
      //#if MC >= 12106
      RegisterEvent.getBus(modBus).addListener(SkywyrmsForge::onRegister);
      //#endif
      //#if MC >= 12109
      EntityAttributeCreationEvent.BUS.addListener(event -> ModEntities.registerAttributes(event::put));
      Post.BUS.addListener(event -> ServerHooks.onServerTick(event.server()));
      //#elif MC >= 12106 && MC < 12109
      EntityAttributeCreationEvent.getBus(modBus).addListener(event -> ModEntities.registerAttributes(event::put));
      Post.BUS.addListener(event -> ServerHooks.onServerTick(event.getServer()));
      //#else
      modBus.addListener(EventPriority.NORMAL, false, RegisterEvent.class, SkywyrmsForge::onRegister);
      modBus.addListener(EventPriority.NORMAL, false, EntityAttributeCreationEvent.class, event -> ModEntities.registerAttributes(event::put));
      //#endif
      //#if MC >= 12006 && MC < 12106
      MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, Post.class, event -> ServerHooks.onServerTick(event.getServer()));
      //#endif
      //#if MC >= 12006
      channel = ChannelBuilder.named(Skywyrms.id("main")).networkProtocolVersion(1).payloadChannel().play().serverbound()
         .addMain(AbilityPayload.TYPE, AbilityPayload.STREAM_CODEC, SkywyrmsForge::onAbility)
         .build();
      //#else
      MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ServerTickEvent.class, event -> {
         if (event.phase == Phase.END) {
            ServerHooks.onServerTick(event.getServer());
         }
      });
      //#endif
      //#if MC >= 12002 && MC < 12006
      channel = ChannelBuilder.named(Skywyrms.id("main")).networkProtocolVersion(1).simpleChannel();
      channel.messageBuilder(AbilityPayload.class, 0, NetworkDirection.PLAY_TO_SERVER)
         .encoder(AbilityPayload::write)
         .decoder(AbilityPayload::read)
         .consumerMainThread(SkywyrmsForge::onAbility)
         .add();
      //#elif MC < 12002
      channel = NetworkRegistry.newSimpleChannel(Skywyrms.id("main"), () -> "1", v -> true, v -> true);
      channel.registerMessage(0, AbilityPayload.class, AbilityPayload::write, AbilityPayload::read, (payload, context) -> {
         Context ctx = (Context)context.get();
         ServerPlayer sender = ctx.getSender();
         if (sender != null) {
            ctx.enqueueWork(() -> ServerHooks.onAbility(sender, payload));
         }

         ctx.setPacketHandled(true);
      }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
      //#endif
      if (FMLEnvironment.dist == Dist.CLIENT) {
         SkywyrmsForgeClient.init(modBus);
      }
   }

   private static void onRegister(RegisterEvent event) {
      event.register(Registries.ENTITY_TYPE, helper -> ModEntities.register(helper::register));
      event.register(Registries.ITEM, helper -> ModItems.register(helper::register));
      event.register(Registries.PARTICLE_TYPE, helper -> ModParticles.register(helper::register));
      event.register(Registries.SOUND_EVENT, helper -> ModSounds.register(helper::register));
      event.register(Registries.CREATIVE_MODE_TAB, helper -> ModItems.registerTabs(helper::register));
   }

   //#if MC >= 12002
   private static void onAbility(AbilityPayload payload, Context context) {
      ServerPlayer sender = context.getSender();
      if (sender != null) {
   //#endif
         //#if MC >= 12006
         context.enqueueWork(() -> ServerHooks.onAbility(sender, payload));
         //#elif MC >= 12002 && MC < 12006
         ServerHooks.onAbility(sender, payload);
         //#endif
      //#if MC >= 12002
      }
      //#endif
      //#if MC >= 12006

      context.setPacketHandled(true);
      //#endif
   //#if MC >= 12002
   }

   //#endif
   private static final class ForgePlatform implements Platform {
      @Override
      public String loaderName() {
         return "forge";
      }

      @Override
      public void sendToServer(AbilityPayload payload) {
         //#if MC >= 12002
         SkywyrmsForge.channel.send(payload, PacketDistributor.SERVER.noArg());
         //#else
         SkywyrmsForge.channel.sendToServer(payload);
         //#endif
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
