package com.minecampkids.protect;

import static com.minecampkids.protect.MCKidsProtect.MODID;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.thread.EffectiveSide;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;

@Mod(MODID)
public class MCKidsProtect {
    
    public static final String MODID = "mckidsprotect";
    public static final String NAME = "MCKids Protect";
    public static final String VERSION = "1.0";
    public static final String PROTOCOL_VERSION = "1";
    
	public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
			new ResourceLocation(MODID, "main"),
			() -> PROTOCOL_VERSION,
			$ -> true,
			PROTOCOL_VERSION::equals
	);
    static {
        CHANNEL.registerMessage(0, MessageInitialWhitelist.class, MessageInitialWhitelist::toBytes, MessageInitialWhitelist::fromBytes, MessageInitialWhitelist::handle);
        CHANNEL.registerMessage(1, MessageWhitelist.class, MessageWhitelist::toBytes, MessageWhitelist::fromBytes, MessageWhitelist::handle);
    }

    private static MCKidsProtect instance;

    public static MCKidsProtect getInstance() {
    	return instance;
    }
    
    private final ProtectionConfig config;
    
    public MCKidsProtect() {
    	instance = this;

    	this.config = new ProtectionConfig();
    	ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, this.config.getSpec());

    	IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
    	modBus.addListener(this::init);
    	modBus.addListener(this::clientInit);

    	MinecraftForge.EVENT_BUS.addListener(this::serverStarting);
	}
    
    private void init(FMLCommonSetupEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
    }
    
    private void clientInit(FMLClientSetupEvent event) {
    	MinecraftForge.EVENT_BUS.register(new ClientEventHandler(config));
    }
    
    private void serverStarting(FMLServerStartingEvent event) {
        ProtectionCommand.register(event.getCommandDispatcher());
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!getConfig().isWhitelisted(event.getPlayer(), event.getState())) {
            event.setCanceled(true);
        }
    }
    
    @SubscribeEvent
    public void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (!getConfig().isWhitelisted(event.getPlayer(), event.getWorld().getBlockState(event.getPos()))) {
            if (event.getEntity().getEntityWorld().isRemote) {
                Minecraft.getInstance().playerController.resetBlockRemoving();
            }
        }
    }
    
    @SubscribeEvent
    public void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (getConfig().preventInteract() && !getConfig().isWhitelisted(event.getPlayer(), event.getWorld().getBlockState(event.getPos()))) {
            event.setUseBlock(Event.Result.DENY);
        }
        Block held = Block.getBlockFromItem(event.getItemStack().getItem());
        if (!getConfig().isWhitelisted(event.getPlayer(), held.getDefaultState())) {
        	event.setUseItem(Event.Result.DENY);
        }
    }
    
    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof PlayerEntity)) return;
        if (!getConfig().isWhitelisted((PlayerEntity) event.getEntity(), event.getState())) {
            event.setCanceled(true);
            Entity e = event.getEntity();
            if (e instanceof ServerPlayerEntity) {
            	ServerPlayerEntity player = (ServerPlayerEntity) e;
                if (player.container != null) {
                    player.sendContainerToPlayer(player.container);
                }
            }
        }
    }
    
    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (!getConfig().isWhitelisted(event.getPlayer(), event.getState())) {
            event.setCanceled(true);
            if (event.getEntity().getEntityWorld().isRemote) {
                Minecraft.getInstance().playerController.resetBlockRemoving();
            }
        }
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerLoggedInEvent event) {
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) { 
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) event.getPlayer()), new MessageInitialWhitelist(getConfig().getWhitelist()));
        }
    }

    public ProtectionConfig getConfig() {
        return config;
    }
}
