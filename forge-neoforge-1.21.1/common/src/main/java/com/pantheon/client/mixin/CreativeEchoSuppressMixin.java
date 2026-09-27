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
 * this". That includes contents the server itself just sent - the screen's
 * next click reports every slot that differs from what it last reported - so
 * a teammate's change got written straight back, and an echo arriving after a
 * newer change put the old item back over it. Marks everything received from
 * the server as already seen, so only the player's own edits are ever reported.
 */
@Mixin(ClientPacketListener.class)
public abstract class CreativeEchoSuppressMixin {
	// 1.21.1 sends direct inventory writes as container -2 set-slot packets,
	// so these two cover every inventory update the server can make.
	@Inject(method = {"handleContainerSetSlot", "handleContainerContent"}, at = @At("RETURN"))
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
