package com.pokato.skywyrms.net;

import com.pokato.skywyrms.Skywyrms;
import net.minecraft.network.FriendlyByteBuf;
//#if MC >= 12005
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
//#endif
//#if MC >= 12002
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//#endif
//#if MC >= 12005
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
//#endif
//#if MC < 12111
import net.minecraft.resources.ResourceLocation;
//#else
import net.minecraft.resources.Identifier;
//#endif

//#if MC >= 12002
public record AbilityPayload(byte ability) implements CustomPacketPayload {
//#else
public record AbilityPayload(byte ability) {
//#endif
   //#if MC < 12111
   public static final ResourceLocation ID = Skywyrms.id("ability");
   //#else
   public static final Identifier ID = Skywyrms.id("ability");
   //#endif
   //#if MC >= 12005
   public static final Type<AbilityPayload> TYPE = new Type(ID);
   public static final StreamCodec<RegistryFriendlyByteBuf, AbilityPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.BYTE, AbilityPayload::ability, AbilityPayload::new
   );
   //#endif
//#if MC >= 12002

//#endif
   //#if MC >= 12005
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   //#elif MC >= 12002 && MC < 12005
   public ResourceLocation id() {
      return ID;
   }
   //#endif
   //#if MC < 12005

   public void write(FriendlyByteBuf buf) {
      buf.writeByte(this.ability);
   //#endif
   }

   public static AbilityPayload read(FriendlyByteBuf buf) {
      return new AbilityPayload(buf.readByte());
   }
}
