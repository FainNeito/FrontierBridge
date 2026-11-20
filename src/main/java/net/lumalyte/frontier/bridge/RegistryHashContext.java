package net.lumalyte.frontier.bridge;
import com.google.gson.JsonObject;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.data.MappingData;
import com.viaversion.viaversion.api.minecraft.data.StructuredDataKey;
import com.viaversion.viaversion.api.protocol.Protocol;
import com.viaversion.viaversion.util.Key;
import java.util.*;

/** Registry snapshot for one side of a connection. Unknown IDs must never be hashed as guessed names. */
public final class RegistryHashContext implements com.viaversion.viaversion.api.minecraft.codec.CodecContext,
        com.viaversion.viaversion.api.minecraft.codec.CodecContext.RegistryAccess {
    private final Map<String,List<Key>> registries;
    private final CodecContext codecs;
    private final boolean mapped;
    public RegistryHashContext(JsonObject rows,CodecContext codecs,boolean mapped) {
        this.codecs=codecs;this.mapped=mapped;var values=new HashMap<String,List<Key>>();
        for(var entry:rows.entrySet()) {
            var keys=new ArrayList<Key>();
            for(var value:entry.getValue().getAsJsonArray()) {
                var row=value.getAsJsonObject();if(row.get("id").getAsInt()!=keys.size())throw new IllegalArgumentException("Non-contiguous registry "+entry.getKey());
                keys.add(Key.of(row.get("key").getAsString()));
            }
            values.put(Key.of(entry.getKey()).toString(),List.copyOf(keys));
        }
        registries=Map.copyOf(values);
    }
    @Override public RegistryHashContext registryAccess(){return this;}
    @Override public boolean mapped(){return mapped;}
    @Override public boolean isSupported(StructuredDataKey<?> key){return true;}
    @Override public UserConnection connection(){return null;}
    @Override public Protocol<?,?,?,?> protocol(){return codecs;}
    @Override public Key item(int id){return registryKey("item",id);}
    @Override public Key attributeModifier(int id){return registryKey("attribute",id);}
    @Override public Key dataComponentType(int id){return registryKey("data_component_type",id);}
    @Override public Key entity(int id){return registryKey("entity_type",id);}
    @Override public Key blockEntity(int id){return registryKey("block_entity_type",id);}
    @Override public Key sound(int id){return registryKey("sound_event",id);}
    private String name(MappingData.MappingType type){return switch(type){case ITEM->"item";case BLOCK->"block";case SOUND->"sound_event";case ENTITY_TYPE->"entity_type";};}
    @Override public Key key(MappingData.MappingType type,int id){return registryKey(name(type),id);}
    @Override public int id(MappingData.MappingType type,String identifier){var values=registries.get(Key.of(name(type)).toString());return values==null?-1:values.indexOf(Key.of(identifier));}
    @Override public Key registryKey(String registry,int id) {
        var values=registries.get(Key.of(registry).toString());
        if(values==null||id<0||id>=values.size())throw new IllegalArgumentException("Missing hash registry entry "+registry+"/"+id);
        return values.get(id);
    }
    @Override public RegistryHashContext withMapped(boolean requested){if(requested!=mapped)throw new IllegalArgumentException("Use the other side's registry hash context");return this;}
}
