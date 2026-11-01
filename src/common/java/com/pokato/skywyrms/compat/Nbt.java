package com.pokato.skywyrms.compat;

import net.minecraft.nbt.CompoundTag;

public final class Nbt {
   private Nbt() {
   }

   public static String getString(CompoundTag tag, String key, String fallback) {
      //#if MC >= 12105
      return tag.getStringOr(key, fallback);
      //#else
      return tag.contains(key, 8) ? tag.getString(key) : fallback;
      //#endif
   }

   public static float getFloat(CompoundTag tag, String key, float fallback) {
      //#if MC >= 12105
      return tag.getFloatOr(key, fallback);
      //#else
      return tag.contains(key, 99) ? tag.getFloat(key) : fallback;
      //#endif
   }

   public static int getInt(CompoundTag tag, String key, int fallback) {
      //#if MC >= 12105
      return tag.getIntOr(key, fallback);
      //#else
      return tag.contains(key, 99) ? tag.getInt(key) : fallback;
      //#endif
   }

   public static boolean getBoolean(CompoundTag tag, String key, boolean fallback) {
      //#if MC >= 12105
      return tag.getBooleanOr(key, fallback);
      //#else
      return tag.contains(key, 99) ? tag.getBoolean(key) : fallback;
      //#endif
   }

   public static CompoundTag getCompound(CompoundTag tag, String key) {
      //#if MC >= 12105
      return (CompoundTag)tag.getCompound(key).orElse(null);
      //#else
      return tag.contains(key, 10) ? tag.getCompound(key) : null;
      //#endif
   }
}
