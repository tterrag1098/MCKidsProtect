package com.minecampkids.protect;

import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

public class MessageWhitelist {
    
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

	public void toBytes(PacketBuffer buf) {
		buf.writeByte(op.ordinal());
		if (op.hasParam) {
			buf.writeString(param);
		}
	}

	public static MessageWhitelist fromBytes(PacketBuffer buf) {
		Op op = Op.values()[buf.readByte()];
		String param = null;
		if (op.hasParam) {
			param = buf.readString();
		}
		return new MessageWhitelist(op, param);
	}

	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> {
			ProtectionConfig config = MCKidsProtect.getInstance().getConfig();
			switch (op) {
			case ADD:
				config.addWhitelist(param);
				break;
			case REMOVE:
				config.removeWhitelist(param);
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
			default:
				break;
			}
		});
		ctx.get().setPacketHandled(true);
	}
}
