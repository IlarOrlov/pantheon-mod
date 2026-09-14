package com.pantheon;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Large pools of joke phrases for two purposes: announcing a "propagated"
 * death (someone died only because their team's shared health pool hit 0
 * from a hit someone else took, not because they themselves were struck),
 * and flavoring the client-only low-health screen tint. Every phrase is a
 * template taking the dying/hurting player's name and, for death messages,
 * the name of whoever's damage actually emptied the pool.
 */
public final class FunnyMessages {
	private FunnyMessages() {
	}

	private static final List<String> DEATH_TEMPLATES = List.of(
		"%1$s keeled over the instant %2$s stubbed a toe on the shared life bar.",
		"%1$s and %2$s were on the same life support, and %2$s just unplugged it.",
		"Somewhere, a butterfly flapped its wings, %2$s got hit, and %1$s dropped dead.",
		"%1$s discovered the hard way that %2$s's pain is everyone's pain.",
		"%1$s just experienced %2$s's near-death experience, except it wasn't near.",
		"%2$s sneezed near a creeper and %1$s's soul left the building.",
		"The group HP bar hit zero because of %2$s, and %1$s was just standing there minding their own business.",
		"%1$s has been voted off the island by %2$s's poor decision-making.",
		"%2$s took one for the team, which unfortunately meant %1$s took it too.",
		"%1$s's last words: \"why do I feel what %2$s is feeling.\"",
		"%1$s got emotionally damaged by %2$s's physical damage.",
		"Shared health: the feature where %2$s falls in lava and %1$s just... also dies.",
		"%1$s filed a complaint against %2$s, posthumously.",
		"%2$s walked into a cactus and somehow %1$s is the one respawning.",
		"%1$s was three biomes away, perfectly safe, until %2$s wasn't.",
		"%1$s's cause of death: extreme empathy toward %2$s.",
		"%2$s challenged a skeleton to a staring contest and lost, taking %1$s down with them.",
		"%1$s never even loaded the chunk %2$s died in, yet here we are.",
		"%2$s said \"watch this\" and %1$s paid the price.",
		"%1$s has entered the chat. Also, %1$s has left the chat. Thanks, %2$s.",
		"%2$s forgot which button was sprint and which was jump off the cliff. %1$s forgives them. Probably.",
		"%1$s's health bar just wanted to see the world burn, courtesy of %2$s.",
		"In an act of true friendship, %1$s died exactly when %2$s did.",
		"%2$s tried to pet a wolf. It was not a wolf. %1$s is also dead now.",
		"%1$s would like it on record that %2$s is banned from lava biomes forever.",
		"%2$s's fall damage became %1$s's whole personality, briefly.",
		"%1$s got zero warning, zero mercy, and zero HP, all thanks to %2$s.",
		"%2$s: \"I've got this.\" %1$s: *dies from across the map*",
		"%1$s just learned that friendship is a shared damage pool.",
		"%2$s punched a bee. The bee won. %1$s also lost, somehow.",
		"%1$s's tombstone reads: \"here lies someone who trusted %2$s.\"",
		"%2$s pressed F to pay respects a little too literally, and %1$s went with them.",
		"%1$s was out here vibing when %2$s decided today was not the day.",
		"%2$s's skill issue became %1$s's fatal issue.",
		"%1$s died of secondhand fall damage from %2$s.",
		"%2$s aggroed the entire zombie horde. %1$s aggroed nothing and died anyway.",
		"%1$s respectfully requests that %2$s stop doing that.",
		"%2$s lost a fight with a chicken. %1$s lost their life over it.",
		"%1$s's last thought: \"I don't even know a %2$s.\"",
		"%2$s tested if fire hurts. It does. %1$s can confirm, from experience.",
		"The bond between %1$s and %2$s was truly unbreakable, right up until %2$s broke it."
	);

	private static final List<String> LOW_HEALTH_TEMPLATES = List.of(
		"%s is basically a single hit away from a very funny death message.",
		"%s's soul is currently held together with tape and vibes.",
		"Everyone please be extremely gentle, %s is on their last legs (and everyone else's).",
		"%s has entered the \"one more hit and it's respawn screen for the whole server\" zone.",
		"%s is running on fumes and bad decisions.",
		"Whoever is near %s: maybe stop fighting things for a second.",
		"%s's health bar has left the building, mentally.",
		"%s is currently a glass cannon made entirely of glass.",
		"%s just remembered everyone shares this health bar. Too late now.",
		"%s is one skeleton arrow away from ruining everyone's day."
	);

	/** A random death broadcast for a player killed only because their teammate's damage emptied the shared pool. */
	public static Component randomPropagatedDeath(final String victimName, final String causeName) {
		String template = DEATH_TEMPLATES.get(ThreadLocalRandom.current().nextInt(DEATH_TEMPLATES.size()));
		return Component.literal(String.format(template, victimName, causeName)).withStyle(ChatFormatting.GOLD);
	}

	/** A random flavor line for the client-only low-health warning tint. */
	public static String randomLowHealthLine(final String playerName) {
		String template = LOW_HEALTH_TEMPLATES.get(ThreadLocalRandom.current().nextInt(LOW_HEALTH_TEMPLATES.size()));
		return String.format(template, playerName);
	}

	private static final List<String> SLOT_REQUEST_TEMPLATES = List.of(
		"%s is standing right behind you, staring at that slot.",
		"%s would like you to know that slot is theirs, spiritually.",
		"%s is respectfully, urgently demanding that slot back.",
		"%s is tapping their foot and pointing at your hotbar.",
		"%s has entered a formal request for that slot. Please advise.",
		"%s is one bad mood away from a slot-related incident.",
		"%s says 'any day now' about that slot you're hogging.",
		"%s would like that slot back before the sun explodes, please."
	);

	/** A random actionbar ping shown to whoever currently owns a slot someone else requested. */
	public static Component randomSlotRequest(final String requesterName) {
		String template = SLOT_REQUEST_TEMPLATES.get(ThreadLocalRandom.current().nextInt(SLOT_REQUEST_TEMPLATES.size()));
		return Component.literal(String.format(template, requesterName)).withStyle(ChatFormatting.AQUA);
	}
}
