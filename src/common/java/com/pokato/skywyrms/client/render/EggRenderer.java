package com.pokato.skywyrms.client.render;

//#if MC < 12102
import com.mojang.blaze3d.vertex.PoseStack;
//#endif
import com.pokato.skywyrms.Skywyrms;
import com.pokato.skywyrms.entity.DragonEggEntity;
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
public class EggRenderer extends MobRenderer<DragonEggEntity, EggRenderState, EggModel> {
//#else
public class EggRenderer extends MobRenderer<DragonEggEntity, EggModel> {
//#endif
   private static final int KINDS = DragonKind.values().length;
   //#if MC < 12111
   private final ResourceLocation[] textures = new ResourceLocation[KINDS * KINDS];
   //#else
   private final Identifier[] textures = new Identifier[KINDS * KINDS];
   //#endif

   public EggRenderer(Context context) {
      super(context, new EggModel(context.bakeLayer(ModModelLayers.EGG)), 0.32F);

      for (DragonKind body : DragonKind.values()) {
         for (DragonKind blood : DragonKind.values()) {
            this.textures[body.ordinal() * KINDS + blood.ordinal()] = Skywyrms.id("textures/entity/egg/" + body.element() + "_" + blood.element() + ".png");
         }
      }

      this.addLayer(new EggGlowLayer(this));
   }

   //#if MC >= 12102
   public EggRenderState createRenderState() {
      return new EggRenderState();
   //#else
   public void render(DragonEggEntity egg, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int light) {
      fill(egg, ((EggModel)this.model).state, partialTick);
      super.render(egg, yaw, partialTick, poseStack, buffer, light);
   //#endif
   }

   //#if MC >= 12102
   public void extractRenderState(DragonEggEntity egg, EggRenderState state, float partialTick) {
      super.extractRenderState(egg, state, partialTick);
      fill(egg, state, partialTick);
   }

   //#endif
   //#if MC >= 12102 && MC < 12111
   public ResourceLocation getTextureLocation(EggRenderState state) {
   //#elif MC >= 12111
   public Identifier getTextureLocation(EggRenderState state) {
   //#endif
      //#if MC >= 12102
      return this.textures[state.body.ordinal() * KINDS + state.blood.ordinal()];
      //#else
   public ResourceLocation getTextureLocation(DragonEggEntity egg) {
      return this.textures[egg.body().ordinal() * KINDS + egg.blood().ordinal()];
      //#endif
   }

   private static void fill(DragonEggEntity egg, EggRenderState state, float partialTick) {
      state.body = egg.body();
      state.blood = egg.blood();
      float progress = egg.progressFraction();
      state.stage = progress >= 0.9F ? 2 : (progress >= 0.75F ? 1 : 0);
      float t = egg.wobbleTicks > 0 ? Math.max(0.0F, egg.wobbleTicks - partialTick) : 0.0F;
      state.wobble = t <= 0.0F ? 0.0F : egg.wobbleStrength * 0.28F * (t / 24.0F) * (float)Math.sin(t * 1.25F);
      state.wobblePhase = egg.getId() * 3.1F;
   }
}
