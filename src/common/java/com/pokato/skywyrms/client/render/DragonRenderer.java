package com.pokato.skywyrms.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pokato.skywyrms.Skywyrms;
import com.pokato.skywyrms.entity.DragonEntity;
import com.pokato.skywyrms.entity.DragonKind;
//#if MC < 12102
import net.minecraft.client.renderer.MultiBufferSource;
//#endif
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
//#if MC < 12111
import net.minecraft.resources.ResourceLocation;
//#else
import net.minecraft.resources.Identifier;
//#endif
//#if MC >= 12102
import net.minecraft.world.phys.AABB;
//#endif

//#if MC >= 12102
public class DragonRenderer extends MobRenderer<DragonEntity, DragonRenderState, DragonModel> {
//#else
public class DragonRenderer extends MobRenderer<DragonEntity, DragonModel> {
//#endif
   private final DragonKind kind;
   //#if MC < 12111
   private final ResourceLocation[] textures = new ResourceLocation[DragonKind.values().length];
   //#else
   private final Identifier[] textures = new Identifier[DragonKind.values().length];
   //#endif

   public DragonRenderer(Context context, DragonKind kind) {
      super(context, new DragonModel(context.bakeLayer(ModModelLayers.dragon(kind)), kind.geo()), 1.5F * kind.scale());
      this.kind = kind;

      for (DragonKind blood : DragonKind.values()) {
         this.textures[blood.ordinal()] = texture(kind, blood, false);
      }

      this.addLayer(new DragonGlowLayer(this, kind));
   }

   //#if MC < 12111
   public static ResourceLocation texture(DragonKind body, DragonKind blood, boolean glow) {
   //#else
   public static Identifier texture(DragonKind body, DragonKind blood, boolean glow) {
   //#endif
      String suffix = glow ? "_glow.png" : ".png";
      return body == blood
         ? Skywyrms.id("textures/entity/" + body.id() + suffix)
         : Skywyrms.id("textures/entity/hybrid/" + body.element() + "_" + blood.element() + suffix);
   }

   //#if MC >= 12102
   public DragonRenderState createRenderState() {
      return new DragonRenderState();
   }

   public void extractRenderState(DragonEntity entity, DragonRenderState state, float partialTick) {
      super.extractRenderState(entity, state, partialTick);
   //#else
   public void render(DragonEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int light) {
      DragonRenderState state = ((DragonModel)this.model).state;
   //#endif
      state.kind = this.kind;
      state.blood = entity.blood();
      entity.anim.fill(state.anim, entity, partialTick);
      //#if MC < 12102
      super.render(entity, yaw, partialTick, poseStack, buffer, light);
      //#endif
   }

   //#if MC >= 12102 && MC < 12111
   public ResourceLocation getTextureLocation(DragonRenderState state) {
   //#elif MC >= 12111
   public Identifier getTextureLocation(DragonRenderState state) {
   //#endif
      //#if MC >= 12102
      return this.textures[state.blood.ordinal()];
      //#else
   public ResourceLocation getTextureLocation(DragonEntity entity) {
      return this.textures[entity.blood().ordinal()];
      //#endif
   }

   //#if MC >= 12102
   protected void scale(DragonRenderState state, PoseStack poseStack) {
      float scale = this.kind.scale() * state.ageScale;
   //#else
   protected void scale(DragonEntity entity, PoseStack poseStack, float partialTick) {
   //#endif
      //#if MC >= 12005 && MC < 12102
      float scale = this.kind.scale() * entity.getAgeScale();
      //#elif MC < 12005
      float scale = this.kind.scale() * entity.getScale();
      //#endif
      poseStack.scale(scale, scale, scale);
   //#if MC >= 12102
   }

   //#endif
   //#if MC >= 12102 && MC < 260300
   protected AABB getBoundingBoxForCulling(DragonEntity entity) {
   //#elif MC >= 260300
   protected AABB getBoundingBoxForCulling(DragonEntity entity, float partialTick) {
   //#endif
      //#if MC >= 12102
      return entity.renderBounds();
      //#endif
   }
}
