package com.minecampkids.protect;

import io.netty.channel.local.LocalAddress;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RayTraceResult.Type;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.DrawHighlightEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ClientEventHandler {
    
    private final ProtectionConfig config;
    
    public ClientEventHandler(ProtectionConfig config) {
        this.config = config;
    }

    @SubscribeEvent
    public void onDrawBlockHighlight(DrawHighlightEvent.HighlightBlock event) {
    	ClientPlayerEntity player = Minecraft.getInstance().player;
        ItemStack main = player.getHeldItemMainhand();
        ItemStack off = player.getHeldItemOffhand();
        
        // FIXME
        BlockState mainstate = Block.getBlockFromItem(main.getItem()).getDefaultState();
        BlockState offstate = Block.getBlockFromItem(off.getItem()).getDefaultState();
        
        if (config.isWhitelisted(player, mainstate) || config.isWhitelisted(player, offstate)) {
            return;
        }

        if (event.getTarget().getType() == Type.BLOCK 
                && !config.isWhitelisted(player, player.world.getBlockState(event.getTarget().getPos()))) {
            event.setCanceled(true);
        }
    }
    
    @SubscribeEvent
    public void onClientConnectToServer(ClientPlayerNetworkEvent.LoggedInEvent event) {
        if (!(event.getNetworkManager().getRemoteAddress() instanceof LocalAddress)) {
            config.enableSaving(false);
        }
    }
    
    @SubscribeEvent
    public void onClientDisconnectFromServer(ClientPlayerNetworkEvent.LoggedOutEvent event) {
        config.restore();
    }
}
