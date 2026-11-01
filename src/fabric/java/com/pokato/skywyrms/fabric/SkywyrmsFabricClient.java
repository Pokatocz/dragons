package com.pokato.skywyrms.fabric;

import com.pokato.skywyrms.client.ClientHooks;
import com.pokato.skywyrms.client.ModKeys;
import com.pokato.skywyrms.client.SkywyrmsClient;
import com.pokato.skywyrms.client.particle.ClientParticles;
import com.pokato.skywyrms.client.render.ModModelLayers;
//#if MC < 12005
import com.pokato.skywyrms.net.AbilityPayload;
//#endif
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
//#if MC < 260100
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
//#else
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
//#endif
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
//#if MC < 260100
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
//#else
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
//#endif
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
//#if MC >= 260100
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
//#elif MC < 12005
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
//#endif

public class SkywyrmsFabricClient implements ClientModInitializer {
   public void onInitializeClient() {
      SkywyrmsClient.init();
      //#if MC >= 12005
      SkywyrmsFabric.clientSender = ClientPlayNetworking::send;
      //#else
      SkywyrmsFabric.clientSender = payload -> {
         FriendlyByteBuf buf = PacketByteBufs.create();
         payload.write(buf);
         ClientPlayNetworking.send(AbilityPayload.ID, buf);
      };
      //#endif
      SkywyrmsClient.registerRenderers(EntityRendererRegistry::register);
      //#if MC < 260100
      ClientParticles.register((type, factory) -> ParticleFactoryRegistry.getInstance().register(type, factory::apply));
      ModModelLayers.register((location, definition) -> EntityModelLayerRegistry.registerModelLayer(location, definition::get));
      ModKeys.register(KeyBindingHelper::registerKeyBinding);
      //#else
      ClientParticles.register((type, factory) -> ParticleProviderRegistry.getInstance().register(type, factory::apply));
      ModModelLayers.register((location, definition) -> ModelLayerRegistry.registerModelLayer(location, definition::get));
      ModKeys.register(KeyMappingHelper::registerKeyMapping);
      //#endif
      ClientTickEvents.END_CLIENT_TICK.register(ClientHooks::onClientTick);
   }
}
