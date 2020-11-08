package com.minecampkids.protect;

import static net.minecraft.command.Commands.argument;
import static net.minecraft.command.Commands.literal;

import java.util.List;
import java.util.stream.Collectors;

import com.minecampkids.protect.MessageWhitelist.Op;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.minecraft.command.CommandSource;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.network.PacketDistributor;

public class ProtectionCommand {
	
	public static void register(CommandDispatcher<CommandSource> dispatcher) {
		dispatcher.register(literal("protect")
				.requires(s -> s.hasPermissionLevel(2))
				.then(literal("help")
					.executes(ProtectionCommand::help))
				.then(literal("list")
					.executes(ProtectionCommand::list))
				.then(literal("add")
					.then(argument("entry", StringArgumentType.string())
						.executes(ctx -> add(ctx.getSource(), StringArgumentType.getString(ctx, "entry")))))
				.then(literal("remove")
					.then(argument("entry", StringArgumentType.string())
						.executes(ctx -> remove(ctx.getSource(), StringArgumentType.getString(ctx, "entry")))))
				.then(literal("clear")
					.executes(ProtectionCommand::clear))
				.then(literal("enable")
					.executes(ProtectionCommand::enable))
				.then(literal("disable")
					.executes(ProtectionCommand::disable)));
	}
	
	private static int help(CommandContext<CommandSource> ctx) {
		ctx.getSource().sendFeedback(new StringTextComponent(
			"Use " + TextFormatting.DARK_AQUA + "/protect add|remove <pattern> " + TextFormatting.WHITE + "to modify the whitelist.\n"
                + "Pattern examples:\n"
                + TextFormatting.AQUA + "  computercraft:*" + TextFormatting.GRAY + " (matches all blocks from computercraft)\n"
                + TextFormatting.AQUA + "  minecraft:grass" + TextFormatting.GRAY + " (matches grass blocks)\n"
                + TextFormatting.AQUA + "  *:planks" + TextFormatting.GRAY + " (matches blocks named planks from any mod)\n"
                + TextFormatting.AQUA + "  minecraft:log[variant=oak]" + TextFormatting.GRAY + " (matches any rotation of oak logs)\n"
                + TextFormatting.AQUA + "  *:*" + TextFormatting.GRAY + " (matches everything)"), true);
		return Command.SINGLE_SUCCESS;
	}
	
	private static int list(CommandContext<CommandSource> ctx) {
        List<String> whitelist = MCKidsProtect.getInstance().getConfig().getWhitelist();
        if (whitelist.isEmpty()) {
            ctx.getSource().sendFeedback(new StringTextComponent("Whitelist empty!"), true);
        }
        ctx.getSource().sendFeedback(new StringTextComponent(whitelist.stream().collect(Collectors.joining(", "))), true);
        return Command.SINGLE_SUCCESS;
	}

	private static final DynamicCommandExceptionType DUPLICATE_ENTRY = new DynamicCommandExceptionType(entry -> new StringTextComponent("'" + entry + "' already on whitelist"));
	private static final DynamicCommandExceptionType MISSING_ENTRY = new DynamicCommandExceptionType(entry -> new StringTextComponent("'" + entry + "' not found in whitelist"));
	private static final DynamicCommandExceptionType INVALID_ENTRY = new DynamicCommandExceptionType(e -> new StringTextComponent(((Throwable)e).getMessage()));

	private static int add(CommandSource source, String entry) throws CommandSyntaxException {
		try {
			if (MCKidsProtect.getInstance().getConfig().addWhitelist(entry)) {
	            MCKidsProtect.CHANNEL.send(PacketDistributor.ALL.noArg(), new MessageWhitelist(Op.ADD, entry));
				source.sendFeedback(new StringTextComponent("Added '" + entry + "' to whitelist"), true);
				return Command.SINGLE_SUCCESS;
			}
			throw DUPLICATE_ENTRY.create(entry);
		} catch (IllegalArgumentException e) {
			throw INVALID_ENTRY.create(e);
		}
	}

	private static int remove(CommandSource source, String entry) throws CommandSyntaxException {
		try {
			if (MCKidsProtect.getInstance().getConfig().removeWhitelist(entry)) {
	            MCKidsProtect.CHANNEL.send(PacketDistributor.ALL.noArg(), new MessageWhitelist(Op.REMOVE, entry));
				source.sendFeedback(new StringTextComponent("Removed '" + entry + "' from whitelist"), true);
				return Command.SINGLE_SUCCESS;
			}
			throw MISSING_ENTRY.create(entry);
		} catch (IllegalArgumentException e) {
			throw INVALID_ENTRY.create(e);
		}
	}

	private static int clear(CommandContext<CommandSource> ctx) {
        MCKidsProtect.CHANNEL.send(PacketDistributor.ALL.noArg(), new MessageWhitelist(Op.CLEAR, null));
		MCKidsProtect.getInstance().getConfig().clearWhitelist();
		return Command.SINGLE_SUCCESS;
	}

	private static final SimpleCommandExceptionType ALREADY_ENABLED = new SimpleCommandExceptionType(new StringTextComponent("Whitelist already enabled"));
	private static final SimpleCommandExceptionType ALREADY_DISABLED = new SimpleCommandExceptionType(new StringTextComponent("Whitelist already disabled"));

	private static int enable(CommandContext<CommandSource> ctx) throws CommandSyntaxException {
        if (MCKidsProtect.getInstance().getConfig().enableWhitelist()) {
            MCKidsProtect.CHANNEL.send(PacketDistributor.ALL.noArg(), new MessageWhitelist(Op.ENABLE, null));
            ctx.getSource().sendFeedback(new StringTextComponent("Whitelist enabled"), true);
    		return Command.SINGLE_SUCCESS;
        }
        throw ALREADY_ENABLED.create();
	}

	private static int disable(CommandContext<CommandSource> ctx) throws CommandSyntaxException {
        if (MCKidsProtect.getInstance().getConfig().disableWhitelist()) {
            MCKidsProtect.CHANNEL.send(PacketDistributor.ALL.noArg(), new MessageWhitelist(Op.DISABLE, null));
            ctx.getSource().sendFeedback(new StringTextComponent("Whitelist disabled"), true);
    		return Command.SINGLE_SUCCESS;
        }
        throw ALREADY_DISABLED.create();
	}
}
