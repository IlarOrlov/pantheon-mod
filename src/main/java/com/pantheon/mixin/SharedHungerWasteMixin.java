package com.pantheon.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pantheon.PantheonConfig;
import com.pantheon.Team;
import com.pantheon.TeamManager;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;

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
 * for no reason. {@link net.minecraft.world.food.FoodProperties#canAlwaysEat()}
 * foods (golden apples, etc.) are deliberately exempt, same as vanilla exempts
 * them from the "already full" gate in the first place.
 */
@Mixin(FoodProperties.class)
public abstract class SharedHungerWasteMixin {
	private static final int MAX_FOOD_LEVEL = 20;

	@Inject(method = "onConsume", at = @At("HEAD"), cancellable = true)
	private void pantheon$skipIfAlreadyFull(final Level level, final LivingEntity entity, final ItemStack stack, final Consumable consumable, final CallbackInfo ci) {
		FoodProperties self = (FoodProperties) (Object) this;
		if (self.canAlwaysEat() || !(entity instanceof ServerPlayer player) || !PantheonConfig.get().syncHunger) {
			return;
		}
		Team team = TeamManager.teamOf(player);
		Integer sharedFood = team.sharedFoodLevel();
		Float sharedSaturation = team.sharedSaturationLevel();
		if (sharedFood != null && sharedFood >= MAX_FOOD_LEVEL
			&& sharedSaturation != null && sharedSaturation >= self.saturation()) {
			ci.cancel();
		}
	}
}
