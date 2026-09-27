package com.pantheon.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;

/**
 * Exposes the protected {@code key} field so the "request slot" ping can
 * poll the physical key currently bound to it directly (see
 * {@code PantheonModClient#isRequestSlotKeyPhysicallyDown}), as a fallback
 * in case {@link KeyMapping#consumeClick()} ever misses a press while an
 * inventory screen has input focus.
 */
@Mixin(KeyMapping.class)
public interface KeyMappingKeyAccessor {
	@Accessor("key")
	InputConstants.Key pantheon$getKey();
}
