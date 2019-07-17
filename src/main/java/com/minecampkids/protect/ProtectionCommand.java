package com.minecampkids.protect;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.minecampkids.protect.MessageWhitelist.Op;

import net.minecraft.block.Block;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.server.FMLServerHandler;

public class ProtectionCommand extends CommandBase {
    
    @FunctionalInterface
    interface SubCommand {
        String execute(String arg) throws CommandException;
    }
    
    enum EnumSubCommand implements SubCommand {
        HELP(s -> "Use " + TextFormatting.DARK_AQUA + "/protect add|remove <pattern> " + TextFormatting.WHITE + "to modify the whitelist.\n"
                + "Pattern examples:\n"
                + TextFormatting.AQUA + "  computercraft:*" + TextFormatting.GRAY + " (matches all blocks from computercraft)\n"
                + TextFormatting.AQUA + "  minecraft:grass" + TextFormatting.GRAY + " (matches grass blocks)\n"
                + TextFormatting.AQUA + "  *:planks" + TextFormatting.GRAY + " (matches blocks named planks from any mod)\n"
                + TextFormatting.AQUA + "  minecraft:log[variant=oak]" + TextFormatting.GRAY + " (matches any rotation of oak logs)\n"
                + TextFormatting.AQUA + "  *:*" + TextFormatting.GRAY + " (matches everything)"),
        LIST(s -> {
            List<String> whitelist = MCKidsProtect.instance.getConfig().getWhitelist();
            if (whitelist.isEmpty()) {
                return "Whitelist empty!";
            }
            return whitelist.stream().collect(Collectors.joining(", "));
        }),
        ADD(s -> {
            if (s == null) {
                throw new CommandException("Missing value to add to whitelist");
            }
            try {
                if (MCKidsProtect.instance.getConfig().addWhitelist(s)) {
                    return "Added '" + s + "' to whitelist";
                }
                throw new CommandException("'" + s + "' already on whitelist");
            } catch (IllegalArgumentException e) {
                throw new CommandException(e.getMessage());
            }
        }),
        REMOVE(s -> {
            if (s == null) {
                throw new CommandException("Missing value to remove from whitelist");
            }
            try {
                if (MCKidsProtect.instance.getConfig().removeWhitelist(s)) {
                    return "Removed '" + s + "' from whitelist";
                }
                throw new CommandException("'" + s + "' not found in whitelist");
            } catch (IllegalArgumentException e) {
                throw new CommandException(e.getMessage());
            }
        }),
        CLEAR(s -> {
            MCKidsProtect.instance.getConfig().clearWhitelist();
            return "Cleared whitelist";
        }),
        ENABLE(s -> {
            if (MCKidsProtect.instance.getConfig().enableWhitelist()) {
                return "Whitelist enabled";
            }
            throw new CommandException("Whitelist already enabled");
        }),
        DISABLE(s -> {
            if (MCKidsProtect.instance.getConfig().disableWhitelist()) {
                return "Whitelist disabled";
            }
            throw new CommandException("Whitelist already disabled");
        }),
        ;
        
        private final SubCommand func;
        
        private EnumSubCommand(SubCommand func) {
            this.func = func;
        }
        
        @Override
        public String execute(String arg) throws CommandException {
            return func.execute(arg);
        }
        
        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    @Override
    public String getName() {
        return "protect";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/protect <" + Arrays.stream(EnumSubCommand.values())
                                    .map(Object::toString)
                                    .collect(Collectors.joining("|"))
                + "> [predicate]";
    }
    
    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1) {
            throw new WrongUsageException(getUsage(sender));
        }
        
        EnumSubCommand subcommand;
        try {
            subcommand = EnumSubCommand.valueOf(args[0].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new WrongUsageException("Invalid sub-command: " + args[0]);
        }
        
        String reply = subcommand.execute(args.length > 1 ? args[1] : null);
        if (subcommand != EnumSubCommand.HELP && subcommand != EnumSubCommand.LIST && FMLServerHandler.instance().getServer().isDedicatedServer()) {
            // FIXME !!!
            MCKidsProtect.network.sendTo(new MessageWhitelist(Op.values()[subcommand.ordinal() - 2], args.length > 1 ? args[1] : null), getCommandSenderAsPlayer(sender));
        }
        Arrays.stream(reply.split("\n"))
              .map(TextComponentString::new)
              .forEach(sender::sendMessage);
    }
    
    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, Arrays.asList(EnumSubCommand.values()));
        } else if (args.length == 2) {
            return getListOfStringsMatchingLastWord(args, Block.REGISTRY.getKeys());
        }
        return Collections.emptyList();
    }
}
