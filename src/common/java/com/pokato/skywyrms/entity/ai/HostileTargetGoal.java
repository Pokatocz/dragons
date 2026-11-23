package com.pokato.skywyrms.entity.ai;

import com.pokato.skywyrms.entity.DragonEntity;
import java.util.EnumSet;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.Goal.Flag;
import net.minecraft.world.entity.player.Player;

/**
 * 1.2.9: a hostile wild dragon (config hostileChance) goes for the nearest player it can see within 24 blocks and keeps
 * at it up to 40 blocks. Not in peaceful, not players in creative or spectator (Player.canBeSeenAsEnemy). The fighting
 * itself is the same as when a wild dragon is hit: melee and its ability (AbilityAttackGoal).
 */
public class HostileTargetGoal extends Goal {
   private static final double RANGE = 24.0;
   private static final double KEEP_RANGE = 40.0;
   private final DragonEntity dragon;
   private Player player;
   private int cooldown;

   public HostileTargetGoal(DragonEntity dragon) {
      this.dragon = dragon;
      this.setFlags(EnumSet.of(Flag.TARGET));
   }

   public boolean canUse() {
      if (!this.dragon.isHostile() || this.dragon.isBaby() || this.dragon.isVehicle() || this.dragon.getTarget() != null) {
         return false;
      } else if (--this.cooldown > 0) {
         return false;
      } else {
         this.cooldown = reducedTickDelay(10);
         if (this.dragon.level().getDifficulty() == Difficulty.PEACEFUL) {
            return false;
         }

         this.player = this.nearestPlayer();
         return this.player != null;
      }
   }

   private Player nearestPlayer() {
      Player best = null;
      double bestDistance = RANGE * RANGE;

      for (Player candidate : this.dragon.level().players()) {
         if (this.valid(candidate)) {
            double distance = this.dragon.distanceToSqr(candidate);
            if (distance < bestDistance && this.dragon.hasLineOfSight(candidate)) {
               best = candidate;
               bestDistance = distance;
            }
         }
      }

      return best;
   }

   private boolean valid(Player candidate) {
      return candidate != null && candidate.isAlive() && candidate.canBeSeenAsEnemy() && candidate.level() == this.dragon.level();
   }

   public void start() {
      this.dragon.setTarget(this.player);
      this.dragon.roar();
   }

   public boolean canContinueToUse() {
      LivingEntity target = this.dragon.getTarget();
      return this.dragon.isHostile()
         && target == this.player
         && this.valid(this.player)
         && this.dragon.distanceToSqr(this.player) < KEEP_RANGE * KEEP_RANGE
         && this.dragon.level().getDifficulty() != Difficulty.PEACEFUL;
   }

   public void stop() {
      if (this.player != null && this.dragon.getTarget() == this.player) {
         this.dragon.setTarget(null);
      }

      this.player = null;
   }
}
