package com.minecampkids.protect;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class MessageWhitelist implements IMessage {
    
    enum Op {
        ADD(true),
        REMOVE(true),
        CLEAR(false),
        ENABLE(false),
        DISABLE(false),
        ;
        
        final boolean hasParam;
        
        private Op(boolean hasParam) {
            this.hasParam = hasParam;
        }
    }

    private Op op;
    private String param;
    
    public MessageWhitelist() {
        this(Op.ADD, "INVALID");
    }
    
    public MessageWhitelist(Op op) {
        this(op, null);
    }
    
    public MessageWhitelist(Op op, @Nullable String param) {
        this.op = op;
        this.param = param;
    }
    
    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(op.ordinal());
        if (op.hasParam) {
            ByteBufUtils.writeUTF8String(buf, param);
        }
    }
    
    @Override
    public void fromBytes(ByteBuf buf) {
        op = Op.values()[buf.readByte()];
        if (op.hasParam) {
            param = ByteBufUtils.readUTF8String(buf);
        }
    }
    
    public static class Handler implements IMessageHandler<MessageWhitelist, IMessage> {
        
        @Override
        public IMessage onMessage(MessageWhitelist message, MessageContext ctx) {
            FMLClientHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> {
                ProtectionConfig config = MCKidsProtect.instance.getConfig();
                switch(message.op) {
                case ADD:
                    config.addWhitelist(message.param);
                    break;
                case REMOVE:
                    config.removeWhitelist(message.param);
                    break;
                case CLEAR:
                    config.clearWhitelist();
                    break;
                case ENABLE:
                    config.enableWhitelist();
                    break;
                case DISABLE:
                    config.disableWhitelist();
                    break;
                default: break;
                }
            });
            return null;
        }
    }
}
