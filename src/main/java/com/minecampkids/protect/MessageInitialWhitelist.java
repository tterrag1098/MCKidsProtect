package com.minecampkids.protect;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class MessageInitialWhitelist implements IMessage {
    
    private List<String> whitelist = new ArrayList<>();
    
    public MessageInitialWhitelist() {
    }

    public MessageInitialWhitelist(List<String> whitelist) {
        this.whitelist = whitelist;
    }
    
    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(whitelist.size());
        for (String s : whitelist) {
            ByteBufUtils.writeUTF8String(buf, s);
        }
    }
    
    @Override
    public void fromBytes(ByteBuf buf) {
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            whitelist.add(ByteBufUtils.readUTF8String(buf));
        }
    }
    
    public static class Handler implements IMessageHandler<MessageInitialWhitelist, IMessage> {
        
        @Override
        public IMessage onMessage(MessageInitialWhitelist message, MessageContext ctx) {
            FMLClientHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> {
                MCKidsProtect.instance.getConfig().enableSaving(false);
                MCKidsProtect.instance.getConfig().clearWhitelist();
                for (String s : message.whitelist) {
                    MCKidsProtect.instance.getConfig().addWhitelist(s);
                }
            });
            return null;
        }
    }
}
