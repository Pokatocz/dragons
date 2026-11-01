package com.pokato.skywyrms.client.render;

import com.pokato.skywyrms.entity.DragonKind;
//#if MC >= 12102
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
//#endif

//#if MC >= 12102
public class EggRenderState extends LivingEntityRenderState {
//#else
public class EggRenderState {
//#endif
   public DragonKind body = DragonKind.EMBER;
   public DragonKind blood = DragonKind.EMBER;
   public int stage;
   public float wobble;
   public float wobblePhase;
}
