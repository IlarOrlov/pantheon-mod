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
	 * A cruder alternate pool in the same "blame the other guy" shape as
	 * {@link #DEATH_TEMPLATES} - only mixed in when {@link PantheonConfig#crudeHumor}
	 * is on (off by default).
	 */
	private static final List<String> CRUDE_DEATH_TEMPLATES = List.of(
		"%1$s died. %2$s was just too hot to handle.",
		"%1$s couldn't resist %2$s's charms.",
		"%2$s walked in, looked good, and %1$s immediately lost all HP.",
		"%1$s's heart couldn't handle %2$s looking that damn good.",
		"%2$s didn't kill %1$s. The sexual tension did.",
		"%1$s got distracted by %2$s and forgot how to survive.",
		"%2$s brought the looks. %1$s brought absolutely no resistance.",
		"%1$s saw %2$s and suddenly forgot how legs work.",
		"%2$s is dangerously attractive. %1$s learned that the hard way.",
		"%1$s died from excessive exposure to %2$s's sex appeal.",
		"%2$s's charm dealt critical damage to %1$s.",
		"%1$s couldn't handle the heat coming from %2$s.",
		"%2$s winked. %1$s's health bar disappeared.",
		"%1$s was defeated by %2$s's irresistible energy.",
		"%2$s entered the room and %1$s immediately became weak at the knees.",
		"%1$s had one job: resist %2$s. They failed.",
		"%2$s's presence was apparently more lethal than a diamond sword.",
		"%1$s didn't stand a chance against that much attraction.",
		"%2$s was looking suspiciously good today. %1$s paid the price.",
		"%1$s got seduced by the danger and paid for it.",
		"%2$s's flirting technique: apparently lethal.",
		"%1$s's final weakness: apparently %2$s.",
		"%2$s turned up the heat. %1$s's HP turned itself off.",
		"%1$s was already weak. Then %2$s showed up looking like that.",
		"%2$s has been officially classified as a dangerous temptation.",
		"%1$s couldn't decide whether to run or flirt. They chose poorly.",
		"%2$s said \"come closer.\" %1$s should have known better.",
		"%1$s got a little too close to %2$s and paid dearly for it.",
		"%2$s's body language dealt more damage than their weapon.",
		"%1$s was flirting with danger. Unfortunately, danger was named %2$s.",
		"%2$s has enough charisma to be considered a deadly weapon.",
		"%1$s's HP wasn't ready for that much sexual tension.",
		"%2$s came looking irresistible and left %1$s dead.",
		"%1$s's downfall was having absolutely no resistance to %2$s.",
		"%2$s is proof that being attractive can be a combat ability.",
		"%1$s couldn't survive the combination of danger and good looks.",
		"%2$s gave %1$s butterflies. Unfortunately, they were fatal.",
		"%1$s's last mistake was making eye contact with %2$s.",
		"%2$s didn't need a weapon. That smile was enough.",
		"%1$s got absolutely overwhelmed by the chemistry.",
		"%2$s walked past and %1$s's survival instincts clocked out.",
		"%1$s was defeated by temptation.",
		"%2$s's aura is apparently NSFW.",
		"%1$s died, but at least they died attracted.",
		"%2$s: \"Are you sure you want to get closer?\" %1$s: \"Absolutely.\"",
		"%1$s should've known better than to flirt with %2$s.",
		"%2$s was serving looks. %1$s was serving their final death message.",
		"%1$s's health bar wasn't the only thing getting weak around %2$s.",
		"%2$s has dangerous levels of bedroom energy.",
		"%1$s got caught in %2$s's irresistible orbit.",
		"%2$s's charm stat is apparently maxed out."
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
		"%s would like that slot back before the sun explodes, please.",
		"%s is drafting a strongly worded letter about that slot.",
		"%s just filed a noise complaint, except it's a slot complaint.",
		"%s is giving you the slow, disappointed parent stare.",
		"%s would like to remind you that sharing is caring.",
		"%s is doing the 'give it here' finger wiggle at you right now.",
		"%s has started a countdown. You don't want to see what happens at zero.",
		"%s is considering violence over a hotbar slot. Consider yourself warned.",
		"%s just sent this ping with the fury of a thousand suns.",
		"%s would like that slot back. This is not a negotiation.",
		"%s is standing there like a disappointed golden retriever.",
		"%s has been waiting so long they've aged visibly.",
		"%s is refreshing their inventory screen every half second, just for you.",
		"%s says please, but they mean immediately.",
		"%s is one slot request away from rage-quitting the friendship.",
		"%s just pinged you. Again. For the same slot. Take the hint.",
		"%s would like it noted, for the record, that this is petty theft.",
		"%s is sending this with maximum passive-aggression.",
		"%s wants their slot back and is being extremely normal about it.",
		"%s has escalated this to a formal hotbar dispute.",
		"%s is staring directly into your soul through the hotbar.",
		"%s is composing a diplomatic incident over one item slot.",
		"%s would like to point out that possession is 9/10ths of a very bad idea right now.",
		"%s is quietly plotting something involving your other slots.",
		"%s says 'no rush' while radiating an enormous amount of rush.",
		"%s just requested that slot with the energy of a fire alarm.",
		"%s is quietly judging your slot management skills.",
		"%s wants it known that this ping is their final warning.",
		"%s is sending good vibes and one extremely pointed slot request.",
		"%s has begun the ancient ritual of hovering and pinging.",
		"%s would like to schedule a slot-return ceremony. Today. Now.",
		"%s says the slot misses its real owner. That's them, by the way.",
		"%s is not mad, just disappointed, about the slot situation.",
		"%s has invoked the sacred right of 'give it back'.",
		"%s is watching your hotbar like a hawk watches a field mouse.",
		"%s just sent a ping loud enough to hear without sound on."
	);

	/** A random actionbar ping shown to whoever currently owns a slot someone else requested. */
	public static Component randomSlotRequest(final String requesterName) {
		String template = SLOT_REQUEST_TEMPLATES.get(ThreadLocalRandom.current().nextInt(SLOT_REQUEST_TEMPLATES.size()));
		return Component.literal(String.format(template, requesterName)).withStyle(ChatFormatting.AQUA);
	}
}
