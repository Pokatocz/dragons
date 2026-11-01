package com.pokato.skywyrms.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
//#if MC < 12102
import com.pokato.skywyrms.entity.DragonEntity;
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
//#if MC >= 12111
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
//#endif

//#if MC < 12102
public class DragonGlowLayer extends EyesLayer<DragonEntity, DragonModel> {
//#else
public class DragonGlowLayer extends EyesLayer<DragonRenderState, DragonModel> {
//#endif
   private final RenderType[] renderTypes = new RenderType[DragonKind.values().length];
   private DragonKind current;

   //#if MC < 12102
   public DragonGlowLayer(RenderLayerParent<DragonEntity, DragonModel> parent, DragonKind kind) {
   //#else
   public DragonGlowLayer(RenderLayerParent<DragonRenderState, DragonModel> parent, DragonKind kind) {
   //#endif
      super(parent);
      this.current = kind;

      for (DragonKind blood : DragonKind.values()) {
         //#if MC < 12111
         this.renderTypes[blood.ordinal()] = RenderType.eyes(DragonRenderer.texture(kind, blood, true));
         //#else
         this.renderTypes[blood.ordinal()] = RenderTypes.eyes(DragonRenderer.texture(kind, blood, true));
         //#endif
      }
   }

   public RenderType renderType() {
      return this.renderTypes[this.current.ordinal()];
   }

   //#if MC < 12102
   public void render(
      PoseStack poseStack,
      MultiBufferSource buffer,
      int light,
      DragonEntity entity,
      float limbSwing,
      float limbSwingAmount,
      float partialTick,
      float ageInTicks,
      float netHeadYaw,
      float headPitch
   ) {
      if (DragonRenderer.seeThrough(entity)) {
         return;
      }
      this.current = entity.blood();
      super.render(poseStack, buffer, light, entity, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
   //#elif MC >= 12109
   public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, DragonRenderState state, float yRot, float xRot) {
   //#else
   public void render(PoseStack poseStack, MultiBufferSource buffer, int light, DragonRenderState state, float yRot, float xRot) {
   //#endif
      //#if MC >= 12102
      if (state.seeThrough) {
         return;
      }
      this.current = state.blood;
      //#endif
      //#if MC >= 12109
      super.submit(poseStack, collector, light, state, yRot, xRot);
      //#elif MC >= 12102 && MC < 12109
      super.render(poseStack, buffer, light, state, yRot, xRot);
      //#endif
   }
}
