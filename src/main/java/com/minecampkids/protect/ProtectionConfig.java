package com.minecampkids.protect;

import java.io.File;
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

import it.unimi.dsi.fastutil.objects.Object2BooleanLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.common.util.FakePlayer;

public class ProtectionConfig {
    
    private static class BlockPredicate implements Predicate<IBlockState> {

        private final String domain, path;
        
        public BlockPredicate(String domain, String path) {
            Preconditions.checkNotNull(path);
            this.domain = MoreObjects.firstNonNull(domain, "minecraft");
            this.path = path;
        }
        
        @Override
        public boolean test(IBlockState t) {
            ResourceLocation name = t.getBlock().getRegistryName();
            return (domain.equals("*") || domain.equals(name.getResourceDomain())) 
                    && (path.equals("*") || path.contentEquals(name.getResourcePath()));
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
        private final Object2BooleanMap<IBlockState> cache = new Object2BooleanLinkedOpenHashMap<>();
        
        public StatePredicate(String domain, String path, Map<String, String> props) {
            super(domain, path);
            this.props = new HashMap<>(props);
        }
        
        @SuppressWarnings({ "unchecked", "rawtypes" })
        @Override
        public boolean test(IBlockState t) {
            if (!super.test(t)) {
                return false;
            }
            return cache.computeIfAbsent(t, state -> 
               state.getProperties().entrySet().stream()
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
    
    private final Configuration config;
    
    private final Set<Predicate<IBlockState>> whitelist = new HashSet<>();
    private final Property whitelistProp;
    
    private boolean applyInCreative = false;
    private boolean preventInteract = true;
    private boolean allowFakePlayers = true;
    
    private final Property enabled;
    
    private boolean savingEnabled = true;

    public ProtectionConfig(File config) {
        this.config = new Configuration(config);
        
        this.whitelistProp = this.config.get(Configuration.CATEGORY_GENERAL, "whitelist", new String[] {"computercraft:*"}, "");
        readWhitelist();
        
        this.enabled = this.config.get(Configuration.CATEGORY_GENERAL, "whitelistEnabled", true);
        
        this.applyInCreative = this.config.get(Configuration.CATEGORY_GENERAL, "applyInCreative", applyInCreative, "Should the whitelist apply to creative players?").getBoolean();
        this.preventInteract = this.config.get(Configuration.CATEGORY_GENERAL, "preventInteract", preventInteract, "Does the whitelist also prevent interacting with blocks?").getBoolean();
        this.allowFakePlayers = this.config.get(Configuration.CATEGORY_GENERAL, "allowFakePlayers", allowFakePlayers, "Should fake players bypass protection checks").getBoolean();
        this.config.save();
    }
    
    private void readWhitelist() {
        String[] whitelistCfg = whitelistProp.getStringList();
        for (String s : whitelistCfg) {
            addWhitelist(s);
        }
    }

    public boolean isWhitelisted(EntityPlayer player, IBlockState state) {
        if (player.capabilities.isCreativeMode && !this.applyInCreative) {
            return true;
        }
        if (player instanceof FakePlayer && allowFakePlayers) {
            return true;
        }
        return !enabled.getBoolean() || whitelist.stream().anyMatch(p -> p.test(state));
    }
    
    public boolean preventInteract() {
        return preventInteract;
    }
    
    private Predicate<IBlockState> getPredicate(String s) {
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
            this.whitelistProp.set(getWhitelist().toArray(new String[0]));
            this.config.save();
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
        boolean prev = this.enabled.getBoolean();
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
}
