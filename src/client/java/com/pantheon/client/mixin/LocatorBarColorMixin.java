package com.pantheon.client.mixin;

import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pantheon.client.PantheonModClient;

import net.minecraft.client.gui.contextualbar.LocatorBar;

/**
 * Vanilla's own locator bar (the colored dots that take over the XP bar's
 * spot to point out nearby players) computes each player's dot color
 * independently from {@link com.pantheon.client.HotbarColors} - a
 * brightened hash of their UUID, with no de-duplication at all. Once
 * {@code HotbarColors} started resolving collisions between teammates'
 * hotbar-lock frame colors (so nobody on the same team ever shares one),
 * that left the two out of sync for whoever got a resolved fallback color
 * instead of their raw vanilla one: their hotbar frame and their
 * locator-bar dot would show two different colors for the exact same
 * player, defeating the entire point of matching them in the first place.
 *
 * <p>Overrides the color for exactly the UUIDs {@link PantheonModClient}
 * currently has a resolved color for - an online teammate, and only while
 * {@code enableHotbarOwnership} is on, since that's the only time it has
 * any colors resolved at all - leaving every other waypoint (a different
 * team, a lodestone, a recovery compass, anything this mod has no opinion
 * about) to vanilla's own formula untouched. (Turning ownership back off
 * briefly leaves the just-disowned teammates' dots showing their old
 * resolved color for the one network round trip until this client's next
 * {@code HotbarOwnersPayload} clears {@code hotbarColors} back to empty -
 * the same kind of momentary, self-correcting staleness this mod already
 * tolerates elsewhere rather than adding machinery to close a sub-second
 * window.)
 *
 * <p><b>Fragile across Minecraft updates:</b> {@code lambda$extractRenderState$4}
 * is vanilla's own compiler-assigned name for this specific lambda, not a
 * stable method signature - confirmed correct for 26.3 by decompiling
 * {@code LocatorBar} directly (its body is exactly
 * {@code ARGB.setBrightness(ARGB.color(255, uuid.hashCode()), 0.9f)},
 * matching {@link com.pantheon.client.HotbarColors#vanillaColorFor} bit for
 * bit), but a future Minecraft version reordering or adding lambdas inside
 * {@code extractRenderState} could silently shift this name onto a
 * different lambda with the same {@code (UUID) -> Integer} shape - Mixin
 * would keep applying without complaint, just to the wrong target. Re-verify
 * against the decompiled class (not just "it still compiles") whenever
 * bumping the Minecraft version.
 */
@Mixin(LocatorBar.class)
public abstract class LocatorBarColorMixin {
	@Inject(method = "lambda$extractRenderState$4", at = @At("HEAD"), cancellable = true)
	private static void pantheon$matchHotbarFrameColor(final UUID uuid, final CallbackInfoReturnable<Integer> cir) {
		Integer color = PantheonModClient.getHotbarColors().get(uuid);
		if (color != null) {
			cir.setReturnValue(color);
		}
	}
}
