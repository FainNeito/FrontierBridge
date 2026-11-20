package net.lumalyte.frontier.bridge;
import com.viaversion.viaversion.api.type.Types;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import com.google.gson.*;
import java.util.*;

/** Tags use canonical registry identity, never a visual fallback that changes another block's behavior. */
public final class TagTranslator {
    private static int count(ByteBuf input,int maximum){int n=Types.VAR_INT.readPrimitive(input);if(n<0||n>maximum)throw new IllegalArgumentException("Invalid tag count");return n;}
    public void rewrite(ByteBuf input,ByteBuf output,RegistryTranslator registries) {
        var source=registries.keyRegistries(false);var target=registries.keyRegistries(true);
        var payload=Unpooled.buffer();int written=0,total=0;var seenRegistries=new HashSet<String>();
        try {
            int registryCount=count(input,1024);
            for(int r=0;r<registryCount;r++) {
                String registry=Types.STRING.read(input);if(!seenRegistries.add(registry))throw new IllegalArgumentException("Duplicate tag registry");
                var sourceRows=source.getAsJsonArray(registry);var targetRows=target.getAsJsonArray(registry);
                if(sourceRows==null)throw new IllegalArgumentException("Unknown tag registry "+registry);
                boolean suppressed=registry.equals("minecraft:block_sound_set");
                if(!suppressed&&targetRows==null)throw new IllegalArgumentException("Missing target tag registry "+registry);
                var ids=new HashMap<String,Integer>();if(targetRows!=null)for(var row:targetRows){var value=row.getAsJsonObject();ids.put(value.get("key").getAsString(),value.get("id").getAsInt());}
                int tags=count(input,65536);var seenTags=new HashSet<String>();
                if(!suppressed){written++;Types.STRING.write(payload,registry);Types.VAR_INT.writePrimitive(payload,tags);}
                for(int t=0;t<tags;t++) {
                    String name=Types.STRING.read(input);if(!seenTags.add(name))throw new IllegalArgumentException("Duplicate tag name");
                    int length=count(input,1048576);total+=length;if(total>1048576)throw new IllegalArgumentException("Tag payload exceeds entry bound");
                    var members=new LinkedHashSet<Integer>();
                    for(int i=0;i<length;i++) {
                        int id=Types.VAR_INT.readPrimitive(input);if(id<0||id>=sourceRows.size())throw new IllegalArgumentException("Unknown tag member "+registry+"/"+id);
                        Integer mapped=ids.get(sourceRows.get(id).getAsJsonObject().get("key").getAsString());if(mapped!=null)members.add(mapped);
                    }
                    if(!suppressed){Types.STRING.write(payload,name);Types.VAR_INT.writePrimitive(payload,members.size());for(int id:members)Types.VAR_INT.writePrimitive(payload,id);}
                }
            }
            if(input.isReadable())throw new IllegalArgumentException("Trailing tag packet bytes");
            Types.VAR_INT.writePrimitive(output,written);output.writeBytes(payload);
        }finally{payload.release();}
    }
}
