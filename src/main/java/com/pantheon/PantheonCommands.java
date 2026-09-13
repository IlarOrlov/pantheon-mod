package com.pantheon;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import com.pantheon.network.PantheonNetworking;
import com.pantheon.network.SyncConfigPayload;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
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
		for (String name : new String[] {"syncArmor", "syncOffhand", "enableHotbarOwnership"}) {
			builder.suggest(name);
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
							.executes(PantheonCommands::setConfig)))));
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
		HotbarOwnership.broadcast(server);

		source.sendSuccess(() -> Component.literal(setting + " = " + value), true);
		return 1;
	}
}
