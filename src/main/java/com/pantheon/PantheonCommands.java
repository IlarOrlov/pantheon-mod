package com.pantheon;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import com.pantheon.network.PantheonNetworking;
import com.pantheon.network.SyncConfigPayload;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /pantheon config <setting> <value>} - a text alternative to the
 * in-game settings screen, for dedicated-server admins driving the console or
 * who would rather not (re)join to change a setting.
 */
public final class PantheonCommands {
	private PantheonCommands() {
	}

	private static final SuggestionProvider<CommandSourceStack> SETTING_NAMES = (context, builder) -> {
		for (String name : new String[] {"syncArmor", "syncOffhand", "enableHotbarOwnership", "syncHealth", "syncHunger", "syncExperience", "teamsEnabled", "crudeHumor"}) {
			builder.suggest(name);
		}
		return builder.buildFuture();
	};

	private static final SuggestionProvider<CommandSourceStack> TEAM_NAMES = (context, builder) -> {
		for (Team team : TeamManager.allTeams()) {
			builder.suggest(team.name);
		}
		return builder.buildFuture();
	};

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("pantheon")
				.requires(PantheonCommands::isOperator)
				.then(Commands.literal("config")
					.executes(PantheonCommands::showConfig)
					.then(Commands.argument("setting", StringArgumentType.word())
						.suggests(SETTING_NAMES)
						.then(Commands.argument("value", StringArgumentType.word())
							.executes(PantheonCommands::setConfig))))
				.then(Commands.literal("team")
					.then(Commands.literal("list")
						.executes(PantheonCommands::listTeams))
					.then(Commands.literal("create")
						.then(Commands.argument("name", StringArgumentType.word())
							.executes(PantheonCommands::createTeam)))
					.then(Commands.literal("delete")
						.then(Commands.argument("name", StringArgumentType.word())
							.suggests(TEAM_NAMES)
							.executes(PantheonCommands::deleteTeam)))
					.then(Commands.literal("join")
						.then(Commands.argument("name", StringArgumentType.word())
							.suggests(TEAM_NAMES)
							.executes(PantheonCommands::joinTeam)))
					.then(Commands.literal("leave")
						.executes(PantheonCommands::leaveTeam))
					.then(Commands.literal("assign")
						.then(Commands.argument("player", EntityArgument.player())
							.then(Commands.argument("name", StringArgumentType.word())
								.suggests(TEAM_NAMES)
								.executes(PantheonCommands::assignTeam))))));
		});
	}

	private static boolean isOperator(final CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			// console / command blocks are always allowed
			return true;
		}
		return PantheonNetworking.canConfigure(source.getServer(), player);
	}

	private static int showConfig(final com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
		PantheonConfig config = PantheonConfig.get();
		context.getSource().sendSuccess(() -> Component.literal(
			"syncArmor=" + config.syncArmor
				+ ", syncOffhand=" + config.syncOffhand
				+ ", enableHotbarOwnership=" + config.enableHotbarOwnership
				+ ", syncHealth=" + config.syncHealth
				+ ", syncHunger=" + config.syncHunger
				+ ", syncExperience=" + config.syncExperience
				+ ", teamsEnabled=" + config.teamsEnabled
				+ ", crudeHumor=" + config.crudeHumor
		), false);
		return 1;
	}

	private static int setConfig(final com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
		String setting = StringArgumentType.getString(context, "setting");
		String value = StringArgumentType.getString(context, "value");
		CommandSourceStack source = context.getSource();

		PantheonConfig config = PantheonConfig.get().copy();
		try {
			switch (setting) {
				case "syncArmor" -> config.syncArmor = Boolean.parseBoolean(value);
				case "syncOffhand" -> config.syncOffhand = Boolean.parseBoolean(value);
				case "enableHotbarOwnership" -> config.enableHotbarOwnership = Boolean.parseBoolean(value);
				case "syncHealth" -> config.syncHealth = Boolean.parseBoolean(value);
				case "syncHunger" -> config.syncHunger = Boolean.parseBoolean(value);
				case "syncExperience" -> config.syncExperience = Boolean.parseBoolean(value);
				case "teamsEnabled" -> config.teamsEnabled = Boolean.parseBoolean(value);
				case "crudeHumor" -> config.crudeHumor = Boolean.parseBoolean(value);
				default -> {
					source.sendFailure(Component.literal("Unknown setting: " + setting));
					return 0;
				}
			}
		} catch (IllegalArgumentException e) {
			source.sendFailure(Component.literal("Invalid value for " + setting + ": " + value));
			return 0;
		}

		MinecraftServer server = source.getServer();
		PantheonConfig updated = PantheonConfig.applyAndSave(config);

		SyncConfigPayload syncPayload = SyncConfigPayload.fromConfig(updated);
		for (ServerPlayer online : server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(online, syncPayload);
		}
		// teamsEnabled flipping changes what every online player's Inventory
		// should point at (the global team vs. their individually assigned
		// one) - re-point them all rather than waiting for their next rejoin.
		TeamManager.reassignAllOnline(server);
		HotbarOwnership.broadcast(server);

		source.sendSuccess(() -> Component.literal(setting + " = " + value), true);
		return 1;
	}

	/** Team commands still work while {@code teamsEnabled} is off (so they can be set up in advance), but nothing they do actually affects sharing until it's on - make that obvious instead of a silent no-op. */
	private static void warnIfTeamsDisabled(final CommandSourceStack source) {
		if (!PantheonConfig.get().teamsEnabled) {
			source.sendSystemMessage(Component.literal(
				"Warning: teams are OFF (teamsEnabled=false) - sharing is still one server-wide pool. "
					+ "Turn it on with /pantheon config teamsEnabled true."
			).withStyle(ChatFormatting.YELLOW));
		}
	}

	private static int listTeams(final com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		warnIfTeamsDisabled(source);
		MinecraftServer server = source.getServer();
		StringBuilder builder = new StringBuilder();
		for (Team team : TeamManager.allTeams()) {
			if (!builder.isEmpty()) {
				builder.append(", ");
			}
			int online = TeamManager.onlineMembersOf(team, server).size();
			builder.append(team.name).append(" (").append(online).append(" online)");
		}
		source.sendSuccess(() -> Component.literal(builder.isEmpty() ? "No teams." : builder.toString()), false);
		return 1;
	}

	private static int createTeam(final com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
		String name = StringArgumentType.getString(context, "name");
		CommandSourceStack source = context.getSource();
		warnIfTeamsDisabled(source);
		if (!TeamManager.create(name)) {
			source.sendFailure(Component.literal("A team named '" + name + "' already exists."));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Created team '" + name + "'."), true);
		return 1;
	}

	private static int deleteTeam(final com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
		String name = StringArgumentType.getString(context, "name");
		CommandSourceStack source = context.getSource();
		warnIfTeamsDisabled(source);
		if (!TeamManager.delete(name)) {
			source.sendFailure(Component.literal("Can't delete '" + name + "' (it doesn't exist, or it's the global team)."));
			return 0;
		}
		TeamManager.reassignAllOnline(source.getServer());
		HotbarOwnership.broadcast(source.getServer());
		source.sendSuccess(() -> Component.literal("Deleted team '" + name + "'. Its members fall back to the global pool."), true);
		return 1;
	}

	private static int joinTeam(final com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
		ServerPlayer player = context.getSource().getPlayer();
		if (player == null) {
			context.getSource().sendFailure(Component.literal("Only a player can join a team - use /pantheon team assign from console."));
			return 0;
		}
		return assignPlayerToTeam(context, player);
	}

	private static int leaveTeam(final com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
		ServerPlayer player = context.getSource().getPlayer();
		if (player == null) {
			context.getSource().sendFailure(Component.literal("Only a player can leave a team."));
			return 0;
		}
		warnIfTeamsDisabled(context.getSource());
		TeamManager.unassign(player.getUUID());
		TeamManager.reassignInventory(player);
		HotbarOwnership.broadcast(context.getSource().getServer());
		context.getSource().sendSuccess(() -> Component.literal(player.getGameProfile().name() + " left their team and is now on the global pool."), true);
		return 1;
	}

	private static int assignTeam(final com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(context, "player");
		return assignPlayerToTeam(context, player);
	}

	private static int assignPlayerToTeam(final com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, final ServerPlayer player) {
		String name = StringArgumentType.getString(context, "name");
		CommandSourceStack source = context.getSource();
		MinecraftServer server = source.getServer();
		warnIfTeamsDisabled(source);

		if (!TeamManager.exists(name) && !TeamManager.create(name)) {
			source.sendFailure(Component.literal("Couldn't find or create team '" + name + "'."));
			return 0;
		}

		if (PantheonConfig.get().enableHotbarOwnership) {
			Team target = TeamManager.allTeams().stream().filter(t -> t.name.equals(name)).findFirst().orElse(null);
			int currentSize = target == null ? 0 : TeamManager.onlineMembersOf(target, server).size();
			boolean alreadyOnTeam = TeamManager.assignedTeamName(player.getUUID()).equals(name);
			if (!alreadyOnTeam && currentSize >= PantheonConfig.HOTBAR_OWNERSHIP_PLAYER_CAP) {
				source.sendFailure(Component.literal(
					"Team '" + name + "' is full: hotbar-ownership mode supports at most "
						+ PantheonConfig.HOTBAR_OWNERSHIP_PLAYER_CAP + " players per team."
				));
				return 0;
			}
		}

		TeamManager.assign(player.getUUID(), name);
		TeamManager.reassignInventory(player);
		HotbarOwnership.broadcast(server);

		source.sendSuccess(() -> Component.literal(player.getGameProfile().name() + " is now on team '" + name + "'."), true);
		return 1;
	}
}
