package com.pokato.skywyrms.entity.ai;

import com.pokato.skywyrms.entity.DragonEntity;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;

/**
 * Vanilla "stay" goal, except that it steps aside while the dragon is in love: the sit goal runs at a higher priority
 * with the MOVE flag, so in 1.2.4 and earlier it blocked {@link DragonBreedGoal} and two dragons told to stay never
 * bred. The stay order itself is kept, so the dragon sits again (where it bred) once it is no longer in love.
 */
public class DragonSitGoal extends SitWhenOrderedToGoal {
   private final DragonEntity dragon;

   public DragonSitGoal(DragonEntity dragon) {
      super(dragon);
      this.dragon = dragon;
   }

   public boolean canUse() {
      return !this.dragon.isInLove() && super.canUse();
   }

   public boolean canContinueToUse() {
      return !this.dragon.isInLove() && super.canContinueToUse();
   }
}
