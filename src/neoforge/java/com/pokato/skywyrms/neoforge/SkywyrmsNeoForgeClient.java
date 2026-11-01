package com.pokato.skywyrms.neoforge;

import com.pokato.skywyrms.client.ClientHooks;
import com.pokato.skywyrms.client.ModKeys;
import com.pokato.skywyrms.client.SkywyrmsClient;
import com.pokato.skywyrms.client.particle.ClientParticles;
import com.pokato.skywyrms.client.render.ModModelLayers;
import net.minecraft.client.Minecraft;
//#if MC >= 12004
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//#endif
//#if MC >= 12006
import net.neoforged.api.distmarker.Dist;
//#endif
import net.neoforged.bus.api.IEventBus;
//#if MC >= 12006
import net.neoforged.fml.common.Mod;
//#endif
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
//#if MC >= 12005
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
//#endif
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;
//#if MC >= 12107
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
//#endif
import net.neoforged.neoforge.common.NeoForge;
//#if MC < 12005
import net.neoforged.neoforge.event.TickEvent.ClientTickEvent;
import net.neoforged.neoforge.event.TickEvent.Phase;
//#endif
//#if MC >= 12004 && MC < 12107
import net.neoforged.neoforge.network.PacketDistributor;
//#endif

//#if MC >= 12006
@Mod(
   value = "skywyrms",
   dist = {Dist.CLIENT}
)
//#endif
public class SkywyrmsNeoForgeClient {
   //#if MC >= 12006
   public SkywyrmsNeoForgeClient(IEventBus modBus) {
      init(modBus);
   }

   //#endif
   static void init(IEventBus modBus) {
      SkywyrmsClient.init();
      //#if MC >= 12005 && MC < 12107
      SkywyrmsNeoForge.clientSender = payload -> PacketDistributor.sendToServer(payload, new CustomPacketPayload[0]);
      //#elif MC >= 12107
      SkywyrmsNeoForge.clientSender = x$0 -> ClientPacketDistributor.sendToServer(x$0, new CustomPacketPayload[0]);
      //#elif MC >= 12004 && MC < 12005
      SkywyrmsNeoForge.clientSender = payload -> PacketDistributor.SERVER.noArg().send(new CustomPacketPayload[]{payload});
      //#else
      SkywyrmsNeoForge.clientSender = payload -> SkywyrmsNeoForge.channel.sendToServer(payload);
      //#endif
      modBus.addListener(RegisterRenderers.class, event -> SkywyrmsClient.registerRenderers(event::registerEntityRenderer));
      modBus.addListener(RegisterLayerDefinitions.class, event -> ModModelLayers.register(event::registerLayerDefinition));
      modBus.addListener(
         RegisterParticleProvidersEvent.class, event -> ClientParticles.register((type, factory) -> event.registerSpriteSet(type, factory::apply))
      );
      //#if MC < 12109
      modBus.addListener(RegisterKeyMappingsEvent.class, event -> ModKeys.register(event::register));
      //#else
      modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
         event.registerCategory(ModKeys.CATEGORY);
         ModKeys.register(event::register);
      //#endif
      //#if MC < 12005
      NeoForge.EVENT_BUS.addListener(ClientTickEvent.class, event -> {
         if (event.phase == Phase.END) {
            ClientHooks.onClientTick(Minecraft.getInstance());
         }
      //#endif
      //#if MC < 12005 || MC >= 12109
      });
      //#endif
      //#if MC >= 12005
      NeoForge.EVENT_BUS.addListener(Post.class, event -> ClientHooks.onClientTick(Minecraft.getInstance()));
      //#endif
   }
}
