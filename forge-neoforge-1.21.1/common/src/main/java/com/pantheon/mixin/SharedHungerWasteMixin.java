package com.pantheon.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.pantheon.PantheonConfig;
import com.pantheon.Team;
import com.pantheon.TeamManager;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;

/**
 * With shared hunger on, two teammates can each start eating (an ~1.6s
 * animation) before either one's gain has synced to the other - vanilla only
 * checks {@code needsFood()} once, at the *start* of eating, so a player who
 * started when the shared pool still had room can still finish eating after
 * a teammate's own meal already filled it in the meantime. By the time this
 * fires (on actually finishing the bite) the item is already spent either
 * way - that part can't be recovered without hooking eating far earlier - but
 * skipping the nutrition/saturation gain itself at least stops it from
 * clobbering a pool that's already full, rather than briefly perturbing it
 * for no reason. {@link FoodProperties#canAlwaysEat()}
 * foods (golden apples, etc.) are deliberately exempt, same as vanilla exempts
 * them from the "already full" gate in the first place.
 *
 * <p>{@code team.sharedFoodLevel()} is only that fresh as of the *last*
 * {@code SharedStats.tick} (once per server tick, at the end of it - after
 * this tick's own entity ticking, including exhaustion drain, already
 * ran) - so it can still read "full" here even when this player's own real,
 * current hunger has since dropped below max earlier in the very same tick.
 * Requiring the player's own live food level to *also* read full before
 * skipping avoids that stale-snapshot false positive, at the cost of only
 * closing the narrower two-players-finish-eating-in-the-same-tick race this
 * exists for, not every possible staleness window - a reasonable trade for
 * what's already a rare edge case.
 */
@Mixin(Player.class)
public abstract class SharedHungerWasteMixin {
	private static final int MAX_FOOD_LEVEL = 20;

	@Redirect(
		method = "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/food/FoodProperties;)Lnet/minecraft/world/item/ItemStack;",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;eat(Lnet/minecraft/world/food/FoodProperties;)V")
	)
	private void pantheon$skipIfAlreadyFull(final FoodData foodData, final FoodProperties food) {
		if (!food.canAlwaysEat() && ((Object) this) instanceof ServerPlayer player && PantheonConfig.get().syncHunger) {
			Team team = TeamManager.teamOf(player);
			Integer sharedFood = team.sharedFoodLevel();
			Float sharedSaturation = team.sharedSaturationLevel();
			// Vanilla's own FoodData#eat clamps saturation at the current food
			// level, not a flat 20 - so "saturation pool already at its real
			// cap" means sharedSaturation >= sharedFood, not sharedSaturation
			// compared against this one item's own small saturation value,
			// which would be satisfied by almost any nonzero saturation the
			// pool happens to have.
			if (sharedFood != null && sharedFood >= MAX_FOOD_LEVEL
				&& sharedSaturation != null && sharedSaturation >= sharedFood.floatValue()
				&& foodData.getFoodLevel() >= MAX_FOOD_LEVEL) {
				return;
			}
		}
		foodData.eat(food);
	}
}
