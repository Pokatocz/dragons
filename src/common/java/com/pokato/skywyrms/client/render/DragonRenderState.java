package com.pokato.skywyrms.client.render;

import com.pokato.skywyrms.anim.AnimState;
import com.pokato.skywyrms.entity.DragonKind;
//#if MC >= 12102
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
//#endif

//#if MC >= 12102
public class DragonRenderState extends LivingEntityRenderState {
//#else
public class DragonRenderState {
//#endif
   public DragonKind kind = DragonKind.EMBER;
   public DragonKind blood = DragonKind.EMBER;
   public final AnimState anim = new AnimState();
}
