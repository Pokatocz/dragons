package com.pokato.skywyrms.entity.ai;

import com.pokato.skywyrms.config.ModConfig;
import com.pokato.skywyrms.entity.DragonEntity;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

/** Random stroll; a tamed dragon told to roam (1.3.0) keeps within config wanderRadius of its spot and walks back when too far. */
public class DragonStrollGoal extends WaterAvoidingRandomStrollGoal {
   private final DragonEntity dragon;

   public DragonStrollGoal(DragonEntity dragon, double speed) {
      super(dragon, speed);
      this.dragon = dragon;
   }

   protected Vec3 getPosition() {
      if (!this.dragon.isWandering()) {
         return super.getPosition();
      }
      Vec3 home = Vec3.atBottomCenterOf(this.dragon.wanderHome());
      double radius = ModConfig.get().wanderRadius;
      if (this.dragon.position().distanceToSqr(home) > radius * radius * 0.36) {
         return DefaultRandomPos.getPosTowards(this.dragon, 10, 7, home, Math.PI / 2.0);
      }
      Vec3 pos = super.getPosition();
      return pos != null && pos.distanceToSqr(home) <= radius * radius ? pos : null;
   }
}
