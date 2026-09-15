package com.pantheon;

import java.util.ArrayList;
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

	/** Bright, easy-to-read colors for chat, cycled randomly so death messages don't all look the same. */
	private static final List<ChatFormatting> DEATH_COLORS = List.of(
		ChatFormatting.RED, ChatFormatting.GOLD, ChatFormatting.YELLOW, ChatFormatting.GREEN,
		ChatFormatting.AQUA, ChatFormatting.LIGHT_PURPLE, ChatFormatting.BLUE
	);

	/** Simple, blunt, "blame the other guy" one-liners - the joke should land in one read, no unpacking required. */
	private static final List<String> DEATH_TEMPLATES = List.of(
		"%1$s died because %2$s can't aim.",
		"%1$s got yeeted into the void, courtesy of %2$s.",
		"%2$s sneezed. %1$s died. Science can't explain it.",
		"%1$s: \"I didn't even do anything!\" Correct. %2$s did.",
		"%1$s has died. Cause of death: being friends with %2$s.",
		"%2$s said \"hold my beer\" and now %1$s is dead.",
		"%1$s got got. Blame %2$s.",
		"RIP %1$s. %2$s says sorry. Kind of.",
		"%1$s is dead because %2$s exists.",
		"%1$s just learned the hard way that %2$s is bad at this game.",
		"%2$s: \"oops.\" %1$s: dead.",
		"%1$s died. Somewhere, %2$s is laughing nervously.",
		"%1$s's HP hit zero because %2$s hit a skeleton with their face.",
		"%2$s discovered gravity. %1$s discovered death.",
		"%1$s would like everyone to know %2$s did this.",
		"%2$s messed up big time, and %1$s paid for it. With their life.",
		"%1$s is now a ghost thanks to %2$s's terrible decisions.",
		"%2$s pressed the wrong button. %1$s pressed uninstall on life.",
		"%1$s died of embarrassment on %2$s's behalf.",
		"%2$s got outplayed by a chicken. %1$s got outplayed by fate.",
		"%2$s: \"trust me.\" %1$s: dies.",
		"%1$s died. Please direct all complaints to %2$s.",
		"%2$s took fall damage. %1$s took the L.",
		"%1$s's last words: \"not like this... because of %2$s.\"",
		"%2$s walked into fire. %1$s felt the burn from across the map.",
		"%1$s didn't stand a chance. Neither did %2$s's common sense.",
		"%2$s made a bad call. %1$s made the ultimate sacrifice.",
		"%1$s is deceased. %2$s is the reason. Everyone knows it.",
		"%2$s got clowned by a zombie. %1$s got clowned by association.",
		"%1$s never even saw %2$s do it. Lucky them."
	);

	/**
	 * Cruder, swear-heavier one-liners in the same "blame the other guy" shape
	 * as {@link #DEATH_TEMPLATES} - only mixed in when {@link PantheonConfig#crudeHumor}
	 * is on (off by default). Profanity and insults only - no sexual content.
	 */
	private static final List<String> CRUDE_DEATH_TEMPLATES = List.of(
		"%1$s is dead. %2$s is a fucking idiot.",
		"%2$s screwed up, and %1$s paid with their damn life.",
		"%1$s died. %2$s can go to hell for that one.",
		"%2$s is dogshit at this game and %1$s suffered for it.",
		"%1$s got wrecked because %2$s is a clown-ass moron.",
		"%2$s: \"my bad.\" %1$s: \"you're a dumbass.\"",
		"%1$s is dead. Someone tell %2$s they suck.",
		"%2$s pulled some bullshit and %1$s died for it.",
		"%1$s died. %2$s owes everyone a damn apology.",
		"%2$s is a walking disaster and %1$s just found out the hard way.",
		"%1$s got screwed over by %2$s's garbage-ass decisions.",
		"%2$s: \"oops.\" %1$s: \"...you absolute donkey.\"",
		"%1$s is toast. %2$s, you absolute muppet.",
		"%2$s's brain wasn't loaded in, and now %1$s is dead.",
		"%1$s died screaming %2$s's name. Not in a good way, you idiot."
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

	/**
	 * A random death broadcast (random color too, so a chat full of them
	 * doesn't blur together) for a player killed only because their
	 * teammate's damage emptied the shared pool. Draws from the cruder pool
	 * too when {@code allowCrude} is on (mixed in with, not instead of, the
	 * regular one), otherwise sticks to the regular pool entirely.
	 */
	public static Component randomPropagatedDeath(final String victimName, final String causeName, final boolean allowCrude) {
		List<String> pool = allowCrude ? combine(DEATH_TEMPLATES, CRUDE_DEATH_TEMPLATES) : DEATH_TEMPLATES;
		String template = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
		ChatFormatting color = DEATH_COLORS.get(ThreadLocalRandom.current().nextInt(DEATH_COLORS.size()));
		return Component.literal(String.format(template, victimName, causeName)).withStyle(color);
	}

	private static List<String> combine(final List<String> first, final List<String> second) {
		List<String> combined = new ArrayList<>(first.size() + second.size());
		combined.addAll(first);
		combined.addAll(second);
		return combined;
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
