package com.minecampkids.protect;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

public class MessageInitialWhitelist {
    
    private List<String> whitelist = new ArrayList<>();
    
    public MessageInitialWhitelist() {
    }

    public MessageInitialWhitelist(List<String> whitelist) {
        this.whitelist = whitelist;
    }
    
    public void toBytes(PacketBuffer buf) {
        buf.writeInt(whitelist.size());
        for (String s : whitelist) {
            buf.writeString(s);
        }
    }
    
    public static MessageInitialWhitelist fromBytes(PacketBuffer buf) {
        int size = buf.readInt();
        List<String> whitelist = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            whitelist.add(buf.readString());
        }
        return new MessageInitialWhitelist(whitelist);
    }

	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> {
			MCKidsProtect.getInstance().getConfig().enableSaving(false);
			MCKidsProtect.getInstance().getConfig().clearWhitelist();
			for (String s : whitelist) {
				MCKidsProtect.getInstance().getConfig().addWhitelist(s);
			}
		});
		ctx.get().setPacketHandled(true);
	}
}
