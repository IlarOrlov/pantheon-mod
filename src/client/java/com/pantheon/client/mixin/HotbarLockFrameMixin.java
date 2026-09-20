package com.pantheon.client.mixin;

import java.util.List;
import java.util.UUID;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pantheon.client.HotbarColors;
import com.pantheon.client.PantheonModClient;
import com.pantheon.network.HotbarOwnersPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * The hotbar is also drawn as the bottom row of every container screen
 * (inventory, chests, crafting table, ...), not just the HUD - so the same
 * per-slot lock frame needs to be drawn there too, over whichever of those
 * slots map back to the local player's own hotbar. Two-hand mode's "hide
 * every slot but the one usable one" also has to be redone here for the same
 * reason - see {@link com.pantheon.client.TwoHandHotbarOverlay} for its HUD
 * counterpart.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class HotbarLockFrameMixin {
	@Shadow
	@Final
	protected AbstractContainerMenu menu;

	@Inject(method = "extractSlots", at = @At("TAIL"))
	private void pantheon$drawLockFrames(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}

		boolean twoHandMode = PantheonModClient.getLastKnownConfig().twoHandSlotMode;
		List<UUID> owners = PantheonModClient.getHotbarOwners();
		UUID self = minecraft.player.getUUID();

		for (Slot slot : this.menu.slots) {
			if (slot.container != minecraft.player.getInventory()) {
				continue;
			}
			int index = slot.getContainerSlot();
			if (index < 0 || index >= HotbarOwnersPayload.SLOT_COUNT) {
				continue;
			}

			// extractSlots() runs inside a render matrix already translated by
			// (leftPos, topPos) - see AbstractContainerScreen#extractContents -
			// so slot.x/slot.y alone are already correct here; adding leftPos/topPos
			// again double-shifts the frame down and to the right of the real slot.
			int x = slot.x - 1;
			int y = slot.y - 1;

			if (twoHandMode) {
				if (index != HotbarOwnersPayload.TWO_HAND_ACTIVE_SLOT) {
					// Two-hand mode only has one usable hotbar slot - cover the
					// other eight here too, not just on the main HUD, with a
					// plain panel-gray fill matching vanilla's own inventory
					// background so the icon underneath reads as blank rather
					// than a jarring box. Ownership is always off while this
					// mode is on, so there's no lock frame to draw underneath
					// it anyway.
					graphics.fill(x, y, x + 18, y + 18, 0xFFC6C6C6);
				}
				continue;
			}

			UUID owner = owners.get(index);
			if (owner.equals(HotbarOwnersPayload.NO_OWNER) || owner.equals(self)) {
				continue;
			}

			Integer assigned = PantheonModClient.getHotbarColors().get(owner);
			int color = assigned != null ? assigned : HotbarColors.vanillaColorFor(owner);
			graphics.fill(x, y, x + 18, y + 1, color);
			graphics.fill(x, y + 17, x + 18, y + 18, color);
			graphics.fill(x, y, x + 1, y + 18, color);
			graphics.fill(x + 17, y, x + 18, y + 18, color);
		}
	}
}
