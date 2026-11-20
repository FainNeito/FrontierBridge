package net.lumalyte.frontier.bridge;

import com.viaversion.nbt.tag.*;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.api.minecraft.RegistryEntry;
import io.netty.buffer.ByteBuf;
import java.util.*;

/** One instance per connection/configuration cycle. Registry entry ordering is preserved. */
public final class RegistryTranslator {
    public record Dimension(String key,int minY,int height) {
        public int sections() { return height/16; }
    }
    private final BridgeMappings mappings;
    private List<Dimension> dimensions=List.of();
    private int biomeCount;
    private static final com.google.gson.JsonObject hashBase=ItemMappingData.resource("hash-registries.json");
    private final com.google.gson.JsonObject dynamicKeys=new com.google.gson.JsonObject();
    public com.google.gson.JsonObject keyRegistries(boolean mapped) {
        var rows=hashBase.getAsJsonObject(mapped?"old":"snapshot").deepCopy();
        dynamicKeys.entrySet().forEach(entry->{if(!mapped||!entry.getKey().equals("minecraft:block_sound_set"))rows.add(entry.getKey(),entry.getValue());});
        return rows;
    }
    public RegistryHashContext hashContext(boolean mapped) {return new RegistryHashContext(keyRegistries(mapped),new CodecContext(!mapped),mapped);}
    public RegistryTranslator(BridgeMappings mappings) {this.mappings=mappings;}
    public int biomeCount() {
        if(biomeCount==0)throw new IllegalStateException("Biome registry not received");return biomeCount;
    }
    public Dimension dimension(int id) {
        if(id<0||id>=dimensions.size())throw new IllegalArgumentException("Unknown dimension registry ID "+id);
        return dimensions.get(id);
    }
    public void reset() {dimensions=List.of();biomeCount=0;dynamicKeys.entrySet().clear();}

    /** False means suppress this entire registry packet. */
    public boolean rewrite(ByteBuf input,ByteBuf output) {
        String registry=Types.STRING.read(input);
        RegistryEntry[] entries=Types.REGISTRY_ENTRY_ARRAY.read(input);
        if(input.isReadable())throw new IllegalArgumentException("Trailing registry packet bytes");
        var keys=new com.google.gson.JsonArray();
        for(int i=0;i<entries.length;i++){var key=new com.google.gson.JsonObject();key.addProperty("id",i);key.addProperty("key",entries[i].key());keys.add(key);}
        dynamicKeys.add(registry,keys);
        if(registry.equals("minecraft:block_sound_set"))return false;
        var newDimensions=new ArrayList<Dimension>();
        for(var entry:entries) {
            if(entry.tag()==null)throw new IllegalArgumentException("Full registry data required; negotiate empty known packs");
            if(!(entry.tag() instanceof CompoundTag tag)) {
                if(registry.equals("minecraft:dimension_type"))throw new IllegalArgumentException("Dimension data must be a compound");
                continue;
            }
            if(registry.equals("minecraft:dimension_type")) {
                var attributes=tag.getCompoundTag("attributes");
                if(attributes!=null) {
                    attributes.remove("minecraft:visual/has_sky_occluder");
                    attributes.remove("visual/has_sky_occluder");
                }
                int height=tag.getInt("height",0),minY=tag.getInt("min_y",0);
                if(height<=0 || height%16!=0 || minY%16!=0 || height>4096)throw new IllegalArgumentException("Invalid dimension height");
                newDimensions.add(new Dimension(entry.key(),minY,height));
            }
            if(registry.equals("minecraft:chicken_sound_variant"))rewriteSoundNames(tag);
            if(registry.equals("minecraft:jukebox_song")) {
                Tag sound=tag.get("sound_event");
                if(sound!=null)rewriteSoundNames(sound);
            }
        }
        if(registry.equals("minecraft:dimension_type"))dimensions=List.copyOf(newDimensions);
        if(registry.equals("minecraft:worldgen/biome"))biomeCount=entries.length;
        Types.STRING.write(output,registry);Types.REGISTRY_ENTRY_ARRAY.write(output,entries);
        return true;
    }
    private void rewriteSoundNames(Tag tag) {
        if(tag instanceof StringTag value)value.setValue(mappings.soundName(value.getValue()));
        else if(tag instanceof CompoundTag compound)compound.values().forEach(this::rewriteSoundNames);
        else if(tag instanceof ListTag<?> list)list.forEach(this::rewriteSoundNames);
    }
}
