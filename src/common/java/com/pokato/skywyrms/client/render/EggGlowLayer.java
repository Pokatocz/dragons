package com.pokato.skywyrms.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pokato.skywyrms.Skywyrms;
//#if MC < 12102
import com.pokato.skywyrms.entity.DragonEggEntity;
//#endif
import com.pokato.skywyrms.entity.DragonKind;
//#if MC < 12109
import net.minecraft.client.renderer.MultiBufferSource;
//#endif
//#if MC < 12111
import net.minecraft.client.renderer.RenderType;
//#endif
//#if MC >= 12109
import net.minecraft.client.renderer.SubmitNodeCollector;
//#endif
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
//#if MC < 12111
import net.minecraft.resources.ResourceLocation;
//#else
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
//#endif

//#if MC < 12102
public class EggGlowLayer extends EyesLayer<DragonEggEntity, EggModel> {
//#else
public class EggGlowLayer extends EyesLayer<EggRenderState, EggModel> {
//#endif
   private final RenderType[] renderTypes = new RenderType[DragonKind.values().length * 3];
   private int current;

   //#if MC < 12102
   public EggGlowLayer(RenderLayerParent<DragonEggEntity, EggModel> parent) {
   //#else
   public EggGlowLayer(RenderLayerParent<EggRenderState, EggModel> parent) {
   //#endif
      super(parent);

      for (DragonKind blood : DragonKind.values()) {
         for (int stage = 0; stage < 3; stage++) {
            //#if MC < 12111
            ResourceLocation texture = Skywyrms.id("textures/entity/egg/glow_" + blood.element() + "_" + stage + ".png");
            this.renderTypes[blood.ordinal() * 3 + stage] = RenderType.eyes(texture);
            //#else
            Identifier texture = Skywyrms.id("textures/entity/egg/glow_" + blood.element() + "_" + stage + ".png");
            this.renderTypes[blood.ordinal() * 3 + stage] = RenderTypes.eyes(texture);
            //#endif
         }
      }
   }

   public RenderType renderType() {
      return this.renderTypes[this.current];
   }

   //#if MC < 12102
   public void render(
      PoseStack poseStack,
      MultiBufferSource buffer,
      int light,
      DragonEggEntity egg,
      float limbSwing,
      float limbSwingAmount,
      float partialTick,
      float ageInTicks,
      float netHeadYaw,
      float headPitch
   ) {
      EggRenderState state = ((EggModel)this.getParentModel()).state;
   //#elif MC >= 12109
   public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, EggRenderState state, float yRot, float xRot) {
   //#else
   public void render(PoseStack poseStack, MultiBufferSource buffer, int light, EggRenderState state, float yRot, float xRot) {
   //#endif
      this.current = state.blood.ordinal() * 3 + state.stage;
      //#if MC < 12102
      super.render(poseStack, buffer, light, egg, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
      //#elif MC >= 12109
      super.submit(poseStack, collector, light, state, yRot, xRot);
      //#else
      super.render(poseStack, buffer, light, state, yRot, xRot);
      //#endif
   }
}
