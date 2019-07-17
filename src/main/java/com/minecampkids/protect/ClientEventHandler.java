package com.minecampkids.protect;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RayTraceResult.Type;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientConnectedToServerEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent;

public class ClientEventHandler {
    
    private final ProtectionConfig config;
    
    public ClientEventHandler(ProtectionConfig config) {
        this.config = config;
    }

    @SuppressWarnings("deprecation")
    @SubscribeEvent
    public void onDrawBlockHighlight(DrawBlockHighlightEvent event) {
        ItemStack main = event.getPlayer().getHeldItemMainhand();
        ItemStack off = event.getPlayer().getHeldItemOffhand();
        
        // FIXME
        IBlockState mainstate = Block.getBlockFromItem(main.getItem()).getStateFromMeta(main.getMetadata());
        IBlockState offstate = Block.getBlockFromItem(off.getItem()).getStateFromMeta(off.getMetadata());
        
        if (config.isWhitelisted(event.getPlayer(), mainstate) || config.isWhitelisted(event.getPlayer(), offstate)) {
            return;
        }

        if (event.getTarget().typeOfHit == Type.BLOCK 
                && !config.isWhitelisted(event.getPlayer(), Minecraft.getMinecraft().world.getBlockState(event.getTarget().getBlockPos()))) {
            event.setCanceled(true);
        }
    }
    
    @SubscribeEvent
    public void onClientConnectToServer(ClientConnectedToServerEvent event) {
        if (!event.isLocal()) {
            config.enableSaving(false);
        }
    }
    
    @SubscribeEvent
    public void onClientDisconnectFromServer(ClientDisconnectionFromServerEvent event) {
        config.restore();
    }
}
