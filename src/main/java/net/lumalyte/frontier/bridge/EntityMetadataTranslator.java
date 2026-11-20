package net.lumalyte.frontier.bridge;
import com.google.gson.JsonObject;
import com.viaversion.viaversion.api.minecraft.Particle;
import com.viaversion.viaversion.api.minecraft.entitydata.EntityData;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.type.Types;
import java.util.*;

/** Field mappings are derived from both native runtimes, including inherited fields. */
public final class EntityMetadataTranslator {
    private final CodecContext target=new CodecContext(false);
    private final BridgeMappings blocks;
    private static final JsonObject tables=ItemMappingData.resource("bridge-mappings.json");
    private static final com.google.gson.JsonArray particles=ItemMappingData.resource("codec-registries.json").getAsJsonObject("snapshot").getAsJsonArray("particles");
    private final Map<Integer,Integer> entities=new HashMap<>();
    public EntityMetadataTranslator(BridgeMappings blocks){this.blocks=blocks;}
    public void track(int id,int type){entities.put(id,type);}
    public void remove(int id){entities.remove(id);}
    public void clear(){entities.clear();}
    public List<EntityData> rewrite(int id,List<EntityData> values,ItemTranslationSession items) {
        Integer type=entities.get(id);if(type==null)throw new IllegalArgumentException("Metadata for untracked entity "+id);
        var schema=tables.getAsJsonObject("metadata").getAsJsonObject(type.toString());
        if(schema==null)throw new IllegalArgumentException("Unreviewed entity metadata schema "+type);
        var result=new ArrayList<EntityData>();var seen=new HashSet<Integer>();
        for(var entry:values) {
            var field=schema.getAsJsonObject(Integer.toString(entry.id()));
            if(field==null||field.get("serializer").getAsInt()!=entry.dataType().typeId())throw new IllegalArgumentException("Unknown entity metadata field/type");
            int index=field.get("index").getAsInt();if(index<0)continue;
            if(!seen.add(index))throw new IllegalArgumentException("Duplicate entity metadata index");
            Object value=entry.getValue();int serializer=entry.dataType().typeId();
            if(serializer==7)value=items.toClient((Item)value);
            else if(serializer==14)value=blocks.state((Integer)value);
            else if(serializer==15&&((Integer)value)!=0)value=blocks.state((Integer)value);
            else if(serializer==16)value=particle((Particle)value,items);
            else if(serializer==17){var list=(Particle[])value;for(int i=0;i<list.length;i++)list[i]=particle(list[i],items);}
            result.add(new EntityData(index,target.types.entityDataTypes.byId(serializer),value));
        }
        return result;
    }
    public Particle particle(Particle original,ItemTranslationSession items) {
        var p=original.copy();int id=p.id();
        String key=particles.get(id).getAsJsonObject().get("key").getAsString();
        if(Set.of("minecraft:block","minecraft:block_marker","minecraft:falling_dust","minecraft:dust_pillar","minecraft:block_crumble").contains(key))
            p.set(0,Types.VAR_INT,blocks.state((Integer)p.getArgument(0).getValue()));
        else if(key.equals("minecraft:item"))p.set(0,target.types.itemTemplate,items.toClient((Item)p.getArgument(0).getValue()));
        p.setId(tables.getAsJsonArray("particles").get(id).getAsInt());return p;
    }
}
