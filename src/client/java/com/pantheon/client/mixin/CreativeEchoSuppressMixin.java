package com.pantheon.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

/**
 * While the Creative inventory is open, every change to the player's
 * inventory menu is reported to the server as an absolute "slot N now holds
 * this". That includes contents the server itself just sent - vanilla even
 * forces the report on each slot update - so a teammate's change got written
 * straight back, and an echo arriving after a newer change put the old item
 * back over it. Marks everything received from the server as already seen, so
 * only the player's own edits are ever reported.
 */
@Mixin(ClientPacketListener.class)
public abstract class CreativeEchoSuppressMixin {
	@Inject(
		method = "handleContainerSetSlot",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/InventoryMenu;broadcastChanges()V")
	)
	private void pantheon$beforeEcho(final CallbackInfo ci) {
		pantheon$markInventorySeen();
	}

	@Inject(method = {"handleContainerSetSlot", "handleContainerContent", "handleSetPlayerInventory"}, at = @At("RETURN"))
	private void pantheon$afterUpdate(final CallbackInfo ci) {
		pantheon$markInventorySeen();
	}

	@Unique
	private static void pantheon$markInventorySeen() {
		if (Minecraft.getInstance().player == null) {
			return;
		}
		InventoryMenu menu = Minecraft.getInstance().player.inventoryMenu;
		NonNullList<ItemStack> lastSlots = ((MenuLastSlotsAccessor) menu).pantheon$getLastSlots();
		for (int i = 0; i < menu.slots.size() && i < lastSlots.size(); i++) {
			lastSlots.set(i, menu.slots.get(i).getItem().copy());
		}
	}
}
