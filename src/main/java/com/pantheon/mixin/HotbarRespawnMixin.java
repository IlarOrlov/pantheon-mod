package com.pantheon.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pantheon.HotbarOwnership;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;

/**
 * Respawning hands a player a brand new {@code ServerPlayer}/{@code Inventory}
 * whose selected slot is carried over from before they died - normally that's
 * still exclusively theirs (nobody else can select a slot that's locked to an
 * online player, dead or not), but re-checking here too means a respawn can
 * never leave two players stuck sharing one slot, regardless of how that
 * might happen.
 */
@Mixin(PlayerList.class)
public abstract class HotbarRespawnMixin {
	@Inject(method = "respawn", at = @At("RETURN"))
	private void pantheon$resolveRespawnConflict(final ServerPlayer oldPlayer, final boolean keepEverything,
			final Entity.RemovalReason reason, final CallbackInfoReturnable<ServerPlayer> cir) {
		ServerPlayer respawned = cir.getReturnValue();
		if (respawned == null) {
			return;
		}
		MinecraftServer server = ((PlayerList) (Object) this).getServer();
		HotbarOwnership.resolveSlotConflict(server, respawned);
		respawned.inventoryMenu.broadcastFullState();
		HotbarOwnership.broadcast(server);
	}
}
