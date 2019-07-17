package com.minecampkids.protect;

import static com.minecampkids.protect.MCKidsProtect.MODID;
import static com.minecampkids.protect.MCKidsProtect.NAME;
import static com.minecampkids.protect.MCKidsProtect.VERSION;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.Event.Result;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.server.FMLServerHandler;

@Mod(modid = MODID, name = NAME, version = VERSION, acceptableRemoteVersions = "*")
public class MCKidsProtect {
    
    public static final String MODID = "mckidsprotect";
    public static final String NAME = "MCKids Protect";
    public static final String VERSION = "1.0";
    
    @Instance
    public static MCKidsProtect instance;
    
    public static final SimpleNetworkWrapper network = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
    static {
        network.registerMessage(MessageInitialWhitelist.Handler.class, MessageInitialWhitelist.class, 0, Side.CLIENT);
        network.registerMessage(MessageWhitelist.Handler.class, MessageWhitelist.class, 1, Side.CLIENT);
    }
    
    private ProtectionConfig config;
    
    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        setConfig(new ProtectionConfig(event.getSuggestedConfigurationFile()));
        
        MinecraftForge.EVENT_BUS.register(this);
        if (event.getSide().isClient()) {
            MinecraftForge.EVENT_BUS.register(new ClientEventHandler(config));
        }
    }
    
    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new ProtectionCommand());
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!getConfig().isWhitelisted(event.getPlayer(), event.getState())) {
            event.setCanceled(true);
        }
    }
    
    @SubscribeEvent
    public void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (!getConfig().isWhitelisted(event.getEntityPlayer(), event.getWorld().getBlockState(event.getPos()))) {
            event.setCanceled(true);
            if (event.getEntity().getEntityWorld().isRemote) {
                Minecraft.getMinecraft().playerController.resetBlockRemoving();
            }
        }
    }
    
    @SubscribeEvent
    public void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (getConfig().preventInteract() && !getConfig().isWhitelisted(event.getEntityPlayer(), event.getWorld().getBlockState(event.getPos()))) {
            event.setUseBlock(Result.DENY);
        }
    }
    
    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof EntityPlayer)) return;
        if (!getConfig().isWhitelisted((EntityPlayer) event.getEntity(), event.getState())) {
            event.setCanceled(true);
            Entity e = event.getEntity();
            if (e instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) e;
                if (player.inventoryContainer != null) {
                    player.sendContainerToPlayer(player.inventoryContainer);
                }
            }
        }
    }
    
    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (!getConfig().isWhitelisted(event.getEntityPlayer(), event.getState())) {
            event.setCanceled(true);
            if (event.getEntity().getEntityWorld().isRemote) {
                Minecraft.getMinecraft().playerController.resetBlockRemoving();
            }
        }
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerLoggedInEvent event) {
        if (FMLCommonHandler.instance().getSide().isServer()) { 
            network.sendTo(new MessageInitialWhitelist(getConfig().getWhitelist()), (EntityPlayerMP) event.player);
        }
    }

    public ProtectionConfig getConfig() {
        return config;
    }

    public void setConfig(ProtectionConfig config) {
        this.config = config;
    }
}
