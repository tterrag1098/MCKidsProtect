package com.minecampkids.protect;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import it.unimi.dsi.fastutil.objects.Object2BooleanLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.state.IProperty;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.config.ModConfig;

@EventBusSubscriber(modid = MCKidsProtect.MODID, bus = Bus.MOD)
public class ProtectionConfig {
    
    private static class BlockPredicate implements Predicate<BlockState> {

        private final String domain, path;
        
        public BlockPredicate(String domain, String path) {
            Preconditions.checkNotNull(path);
            this.domain = MoreObjects.firstNonNull(domain, "minecraft");
            this.path = path;
        }
        
        @Override
        public boolean test(BlockState t) {
            ResourceLocation name = t.getBlock().getRegistryName();
            return (domain.equals("*") || domain.equals(name.getNamespace())) 
                    && (path.equals("*") || path.contentEquals(name.getPath()));
        }

        @Override
        public int hashCode() {
            return Objects.hash(domain, path);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            BlockPredicate other = (BlockPredicate) obj;
            return Objects.equals(domain, other.domain) && Objects.equals(path, other.path);
        }
        
        @Override
        public String toString() {
            return domain + ":" + path;
        }
    }
    
    private static class StatePredicate extends BlockPredicate {
        
        private final Map<String, String> props;
        private final Object2BooleanMap<BlockState> cache = new Object2BooleanLinkedOpenHashMap<>();
        
        public StatePredicate(String domain, String path, Map<String, String> props) {
            super(domain, path);
            this.props = new HashMap<>(props);
        }
        
        @SuppressWarnings({ "unchecked", "rawtypes" })
        @Override
        public boolean test(BlockState t) {
            if (!super.test(t)) {
                return false;
            }
            return cache.computeIfAbsent(t, state -> 
               state.getValues().entrySet().stream()
                    .map(e -> (Entry<IProperty, Comparable>) (Entry) e) // cast hack
                    .allMatch(e -> !props.containsKey(e.getKey().getName()) // ignore properties not in the map
                                 || props.get(e.getKey().getName()).equals(e.getKey().getName(e.getValue()))));
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = super.hashCode();
            result = prime * result + Objects.hash(props);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (!super.equals(obj))
                return false;
            if (getClass() != obj.getClass())
                return false;
            StatePredicate other = (StatePredicate) obj;
            return Objects.equals(props, other.props);
        }
        
        @Override
        public String toString() {
            return super.toString() + "[" + props.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(",")) + "]";
        }
    }
    
    private static final Pattern STATE = Pattern.compile(
            "(?:(?<domain>\\w+|\\*):)?" // Optionally match domain
          + "(?<path>\\w+|\\*)" // Always match a path, or * for wildcard
          + "(?:\\[(?<props>(?:\\w+=\\w+,)*(?:\\w+=\\w+))\\])?"); // Optionally match property values
    
    private final ForgeConfigSpec spec;
    
    private final Set<Predicate<BlockState>> whitelist = new HashSet<>();
    private final ConfigValue<List<? extends String>> whitelistProp;
    
    private final BooleanValue enabled;

    private final BooleanValue applyInCreative;
    private final BooleanValue preventInteract;
    private final BooleanValue allowFakePlayers;
        
    private boolean savingEnabled = true;

    public ProtectionConfig() {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        
        this.whitelistProp = builder.defineList("whitelist", Lists.newArrayList("computercraft:*"), o -> {
        	if (o instanceof String) {
	        	try {
	        		getPredicate((String) o);
	        	} catch (Exception e) {
	        		return false;
	        	}
	        	return true;
        	}
        	return false;
        });
        
        this.enabled = builder.define("whitelistEnabled", true);
        
        this.applyInCreative = builder.comment("Should the whitelist apply to creative players?").define("applyInCreative", false);
        this.preventInteract = builder.comment("Does the whitelist also prevent interacting with blocks?").define("preventInteract", true);
        this.allowFakePlayers = builder.comment("Should fake players bypass protection checks").define("allowFakePlayers", true);
        
        this.spec = builder.build();
    }
    
    public ForgeConfigSpec getSpec() {
    	return spec;
    }
    
    private void readWhitelist() {
    	boolean saving = this.savingEnabled;
    	enableSaving(false);
        for (String s : whitelistProp.get()) {
            addWhitelist(s);
        }
        enableSaving(saving);
    }

    public boolean isWhitelisted(PlayerEntity player, BlockState state) {
        if (player.abilities.isCreativeMode && !this.applyInCreative.get()) {
            return true;
        }
        if (player instanceof FakePlayer && allowFakePlayers.get()) {
            return true;
        }
        return !enabled.get() || whitelist.stream().anyMatch(p -> p.test(state));
    }
    
    public boolean preventInteract() {
        return preventInteract.get();
    }
    
    private Predicate<BlockState> getPredicate(String s) {
        Matcher m = STATE.matcher(s);
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid whitelist string: " + s);
        }
        final String domain = m.group("domain");
        final String path = m.group("path");
        final String props = m.group("props");
        
        if (props != null) {
            Map<String, String> propValues = new HashMap<>();
            for (String prop : props.split(",")) {
                String[] keyval = prop.split("=");
                propValues.put(keyval[0], keyval[1]);
            }
            return new StatePredicate(domain, path, propValues);
        } else {
            return new BlockPredicate(domain, path);
        }
    }
    
    public List<String> getWhitelist() {
        return whitelist.stream().map(Object::toString).collect(Collectors.toList());
    }
    
    private void save() {
        if (savingEnabled) {
            this.whitelistProp.set(getWhitelist());
            this.spec.save();
        }
    }

    public boolean addWhitelist(String s) {
        boolean ret = whitelist.add(getPredicate(s));
        save();
        return ret;
    }
    
    public boolean removeWhitelist(String s) {
        boolean ret = whitelist.remove(getPredicate(s));
        save();
        return ret;
    }

    public void clearWhitelist() {
        whitelist.clear();
        save();
    }

    private boolean setWhitelistEnabled(boolean enabled) {
        boolean prev = this.enabled.get();
        if (prev == enabled) {
            return false;
        }
        this.enabled.set(enabled);
        save();
        return true;
    }
    
    public boolean enableWhitelist() {
        return setWhitelistEnabled(true);
    }
    
    public boolean disableWhitelist() {
        return setWhitelistEnabled(false);
    }
    
    public void enableSaving(boolean enable) {
        this.savingEnabled = enable;
    }
    
    public void restore() {
        readWhitelist();
        enableSaving(true);
    }

    @SubscribeEvent
    public static void onConfigLoad(ModConfig.Loading event) {
    	MCKidsProtect.getInstance().getConfig().readWhitelist();
    }

    @SubscribeEvent
    public static void onConfigReload(ModConfig.Reloading event) {
    	MCKidsProtect.getInstance().getConfig().readWhitelist();
    }
}
