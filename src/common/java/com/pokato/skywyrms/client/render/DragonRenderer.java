package com.pokato.skywyrms.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pokato.skywyrms.Skywyrms;
import com.pokato.skywyrms.config.ModConfig;
import com.pokato.skywyrms.entity.DragonEntity;
import com.pokato.skywyrms.entity.DragonKind;
import net.minecraft.client.Minecraft;
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

   /**
    * 1.2.4 config "riderSeeThrough": the dragon the local player rides is drawn see-through (vanilla's translucent
    * pass for bodies that are not visible) - only on this client - in first person ("first_person") or always.
    */
   public static boolean seeThrough(DragonEntity dragon) {
      int mode = ModConfig.get().seeThroughMode();
      if (mode == 0) {
         return false;
      }
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.player == null || minecraft.player.getVehicle() != dragon) {
         return false;
      }
      return mode == 2 || minecraft.options.getCameraType().isFirstPerson();
   }

   //#if MC >= 12102
   public DragonRenderState createRenderState() {
      return new DragonRenderState();
   }

   public void extractRenderState(DragonEntity entity, DragonRenderState state, float partialTick) {
      super.extractRenderState(entity, state, partialTick);
      state.kind = this.kind;
      state.blood = entity.blood();
      state.seeThrough = seeThrough(entity);
      entity.anim.fill(state.anim, entity, partialTick);
   }

   protected boolean isBodyVisible(DragonRenderState state) {
      return !state.seeThrough && super.isBodyVisible(state);
   }
   //#else
   public void render(DragonEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int light) {
      DragonRenderState state = ((DragonModel)this.model).state;
      state.kind = this.kind;
      state.blood = entity.blood();
      state.seeThrough = seeThrough(entity);
      entity.anim.fill(state.anim, entity, partialTick);
      super.render(entity, yaw, partialTick, poseStack, buffer, light);
   }

   protected boolean isBodyVisible(DragonEntity entity) {
      return !seeThrough(entity) && super.isBodyVisible(entity);
   }
   //#endif

   //#if MC >= 12111
   public Identifier getTextureLocation(DragonRenderState state) {
      return this.textures[state.blood.ordinal()];
   }
   //#elif MC >= 12102
   public ResourceLocation getTextureLocation(DragonRenderState state) {
      return this.textures[state.blood.ordinal()];
   }
   //#else
   public ResourceLocation getTextureLocation(DragonEntity entity) {
      return this.textures[entity.blood().ordinal()];
   }
   //#endif

   //#if MC >= 12102
   protected void scale(DragonRenderState state, PoseStack poseStack) {
      float scale = this.kind.scale() * state.ageScale;
      poseStack.scale(scale, scale, scale);
   }
   //#else
   protected void scale(DragonEntity entity, PoseStack poseStack, float partialTick) {
      //#if MC >= 12005
      float scale = this.kind.scale() * entity.getAgeScale();
      //#else
      float scale = this.kind.scale() * entity.getScale();
      //#endif
      poseStack.scale(scale, scale, scale);
   }
   //#endif

   //#if MC >= 260300
   protected AABB getBoundingBoxForCulling(DragonEntity entity, float partialTick) {
      return entity.renderBounds();
   }
   //#elif MC >= 12102
   protected AABB getBoundingBoxForCulling(DragonEntity entity) {
      return entity.renderBounds();
   }
   //#endif
}
