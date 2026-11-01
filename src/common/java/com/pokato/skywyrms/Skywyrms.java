package com.pokato.skywyrms;

import com.pokato.skywyrms.platform.Platform;
//#if MC < 12111
import net.minecraft.resources.ResourceLocation;
//#else
import net.minecraft.resources.Identifier;
//#endif
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Skywyrms {
   public static final String MOD_ID = "skywyrms";
   public static final Logger LOGGER = LoggerFactory.getLogger("Skywyrms");

   private Skywyrms() {
   }

   //#if MC < 12111
   public static ResourceLocation id(String path) {
   //#endif
      //#if MC >= 12100 && MC < 12111
      return ResourceLocation.fromNamespaceAndPath("skywyrms", path);
      //#elif MC >= 12111
   public static Identifier id(String path) {
      return Identifier.fromNamespaceAndPath("skywyrms", path);
      //#else
      return new ResourceLocation("skywyrms", path);
      //#endif
   }

   public static void init() {
      LOGGER.info("Skywyrms init on {}", Platform.get().loaderName());
   }
}
