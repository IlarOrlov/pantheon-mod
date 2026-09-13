package com.pantheon.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class PantheonNetworking {
	private PantheonNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(HotbarOwnersPayload.TYPE, HotbarOwnersPayload.CODEC);
	}
}
