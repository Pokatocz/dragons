package com.pokato.skywyrms.forge;

import com.pokato.skywyrms.client.ClientHooks;
import com.pokato.skywyrms.client.ModKeys;
import com.pokato.skywyrms.client.SkywyrmsClient;
import com.pokato.skywyrms.client.particle.ClientParticles;
import com.pokato.skywyrms.client.render.ModModelLayers;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions;
import net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers;
//#if MC < 12106
import net.minecraftforge.common.MinecraftForge;
//#endif
//#if MC >= 12006
import net.minecraftforge.event.TickEvent.ClientTickEvent.Post;
//#endif
//#if MC >= 12106
import net.minecraftforge.eventbus.api.bus.BusGroup;
//#elif MC < 12006
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
//#endif
//#if MC < 12106
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
//#endif

public final class SkywyrmsForgeClient {
   private SkywyrmsForgeClient() {
   }

   //#if MC >= 12106
   public static void init(BusGroup modBus) {
   //#else
   public static void init(IEventBus modBus) {
   //#endif
      SkywyrmsClient.init();
      //#if MC >= 12109
      RegisterRenderers.BUS.addListener(event -> SkywyrmsClient.registerRenderers(event::registerEntityRenderer));
      RegisterLayerDefinitions.BUS.addListener(event -> ModModelLayers.register(event::registerLayerDefinition));
      RegisterKeyMappingsEvent.BUS.addListener(event -> ModKeys.register(event::register));
      RegisterParticleProvidersEvent.BUS.addListener(event -> ClientParticles.register((type, factory) -> event.registerSpriteSet(type, factory::apply)));
      //#elif MC >= 12106 && MC < 12109
      RegisterRenderers.getBus(modBus).addListener(event -> SkywyrmsClient.registerRenderers(event::registerEntityRenderer));
      RegisterLayerDefinitions.getBus(modBus).addListener(event -> ModModelLayers.register(event::registerLayerDefinition));
      RegisterKeyMappingsEvent.getBus(modBus).addListener(event -> ModKeys.register(event::register));
      RegisterParticleProvidersEvent.getBus(modBus)
         .addListener(event -> ClientParticles.register((type, factory) -> event.registerSpriteSet(type, factory::apply)));
      //#endif
      //#if MC >= 12106
      Post.BUS.addListener(event -> ClientHooks.onClientTick(Minecraft.getInstance()));
      //#else
      modBus.addListener(EventPriority.NORMAL, false, RegisterRenderers.class, event -> SkywyrmsClient.registerRenderers(event::registerEntityRenderer));
      modBus.addListener(EventPriority.NORMAL, false, RegisterLayerDefinitions.class, event -> ModModelLayers.register(event::registerLayerDefinition));
      modBus.addListener(EventPriority.NORMAL, false, RegisterKeyMappingsEvent.class, event -> ModKeys.register(event::register));
      modBus.addListener(
         EventPriority.NORMAL,
         false,
         RegisterParticleProvidersEvent.class,
         event -> ClientParticles.register((type, factory) -> event.registerSpriteSet(type, factory::apply))
      );
      //#endif
      //#if MC >= 12006 && MC < 12106
      MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, Post.class, event -> ClientHooks.onClientTick(Minecraft.getInstance()));
      //#elif MC < 12006
      MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ClientTickEvent.class, event -> {
         if (event.phase == Phase.END) {
            ClientHooks.onClientTick(Minecraft.getInstance());
         }
      });
      //#endif
   }
}
