package com.pokato.skywyrms.client.render;

//#if MC >= 12102
import net.minecraft.client.model.EntityModel;
//#else
import com.pokato.skywyrms.entity.DragonEggEntity;
import net.minecraft.client.model.HierarchicalModel;
//#endif
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

//#if MC >= 12102
public class EggModel extends EntityModel<EggRenderState> {
//#else
public class EggModel extends HierarchicalModel<DragonEggEntity> {
   public final EggRenderState state = new EggRenderState();
   private final ModelPart root;
//#endif
   public static final int[][] RINGS = new int[][]{
      {0, 1, 6, 36, 14}, {1, 2, 8, 32, 26}, {2, 6, 10, 0, 0}, {6, 9, 9, 0, 14}, {9, 11, 8, 0, 26}, {11, 12, 7, 0, 36}, {12, 13, 5, 40, 4}, {13, 14, 3, 40, 0}
   };
   private final ModelPart egg;

   //#if MC < 12102
   public ModelPart root() {
      return this.root;
   }

   //#endif
   public EggModel(ModelPart root) {
      //#if MC >= 12102
      super(root);
      //#else
      this.root = root;
      //#endif
      this.egg = root.getChild("egg");
   }

   public static LayerDefinition createLayer() {
      MeshDefinition mesh = new MeshDefinition();
      PartDefinition root = mesh.getRoot();
      CubeListBuilder cubes = CubeListBuilder.create();

      for (int[] ring : RINGS) {
         float w = ring[2];
         cubes.texOffs(ring[3], ring[4]).addBox(-w / 2.0F, -ring[1], -w / 2.0F, w, ring[1] - ring[0], w);
      }

      root.addOrReplaceChild("egg", cubes, PartPose.offset(0.0F, 24.0F, 0.0F));
      return LayerDefinition.create(mesh, 64, 64);
   }

   //#if MC >= 12102
   public void setupAnim(EggRenderState state) {
      super.setupAnim(state);
      this.pose(state);
   //#else
   public void setupAnim(DragonEggEntity egg, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
      this.pose(this.state);
   //#endif
   }

   private void pose(EggRenderState state) {
      this.egg.zRot = state.wobble * (float)Math.cos(state.wobblePhase * 0.37F);
      this.egg.xRot = state.wobble * (float)Math.sin(state.wobblePhase * 0.37F) * 0.6F;
   }
}
