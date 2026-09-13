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
 * slots map back to the local player's own hotbar.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class HotbarLockFrameMixin {
	@Shadow
	protected int leftPos;

	@Shadow
	protected int topPos;

	@Shadow
	@Final
	protected AbstractContainerMenu menu;

	@Inject(method = "extractSlots", at = @At("TAIL"))
	private void pantheon$drawLockFrames(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}

		List<UUID> owners = PantheonModClient.getHotbarOwners();
		List<Integer> colors = PantheonModClient.getHotbarOwnerColors();
		UUID self = minecraft.player.getUUID();

		for (Slot slot : this.menu.slots) {
			if (slot.container != minecraft.player.getInventory()) {
				continue;
			}
			int index = slot.getContainerSlot();
			if (index < 0 || index >= HotbarOwnersPayload.SLOT_COUNT) {
				continue;
			}

			UUID owner = owners.get(index);
			if (owner.equals(HotbarOwnersPayload.NO_OWNER) || owner.equals(self)) {
				continue;
			}

			int color = HotbarColors.colorFor(colors.get(index));
			int x = this.leftPos + slot.x - 1;
			int y = this.topPos + slot.y - 1;
			graphics.fill(x, y, x + 18, y + 1, color);
			graphics.fill(x, y + 17, x + 18, y + 18, color);
			graphics.fill(x, y, x + 1, y + 18, color);
			graphics.fill(x + 17, y, x + 18, y + 18, color);
		}
	}
}
