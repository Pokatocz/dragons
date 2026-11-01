package com.pokato.skywyrms.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Key;
//#if MC >= 12109
import com.pokato.skywyrms.Skywyrms;
//#endif
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
//#if MC >= 12109
import net.minecraft.client.KeyMapping.Category;
//#endif

public final class ModKeys {
   //#if MC < 12109
   public static final String CATEGORY = "key.category.skywyrms.main";
   //#elif NEOFORGE && MC >= 12109
   public static final Category CATEGORY = new Category(Skywyrms.id("main"));
   //#else
   public static final Category CATEGORY = Category.register(Skywyrms.id("main"));
   //#endif
   public static final KeyMapping ABILITY = key("key.skywyrms.ability", "key.keyboard.r");
   public static final KeyMapping DESCEND = key("key.skywyrms.descend", "key.keyboard.x");

   private ModKeys() {
   }

   private static KeyMapping key(String name, String defaultKey) {
      Key key = InputConstants.getKey(defaultKey);
      //#if MC < 12109
      return new KeyMapping(name, key.getType(), key.getValue(), "key.category.skywyrms.main");
      //#else
      return new KeyMapping(name, key.getType(), key.getValue(), CATEGORY);
      //#endif
   }

   public static void register(Consumer<KeyMapping> registrar) {
      registrar.accept(ABILITY);
      registrar.accept(DESCEND);
   }
}
