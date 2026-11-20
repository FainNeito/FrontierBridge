package net.lumalyte.frontier.bridge;
import com.google.gson.*;
import com.viaversion.viaversion.api.data.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Uses canonical reverse IDs, never the arbitrary inverse of a many-to-one fallback. */
public final class ItemMappingData extends MappingDataBase {
    public ItemMappingData() {
        super("26.4-snapshot-8","26.3");
        var rows=resource("codec-registries.json");var maps=resource("bridge-mappings.json");
        itemMappings=full(rows,maps,"items");blockMappings=full(rows,maps,"blocks");
        soundMappings=full(rows,maps,"sounds");entityMappings=full(rows,maps,"entities");
        var names=names(rows.getAsJsonObject("snapshot").getAsJsonArray("components"));
        itemDataSerializerMappings=FullMappingsBase.of(new MappingDataLoader.IdentifiersPair(names,names,true),new IdentityMappings(names.size(),names.size()));
        blockStateMappings=IntArrayMappings.of(array(maps.getAsJsonArray("blockStates")),maps.get("oldStateCount").getAsInt());
    }
    private static FullMappings full(JsonObject rows,JsonObject maps,String key) {
        var source=names(rows.getAsJsonObject("snapshot").getAsJsonArray(key));var target=names(rows.getAsJsonObject("old").getAsJsonArray(key));
        var forward=IntArrayMappings.of(array(maps.getAsJsonArray(key)),target.size());
        var reverse=IntArrayMappings.of(array(maps.getAsJsonObject("canonicalReverse").getAsJsonArray(key)),source.size());
        return FullMappingsBase.of(new MappingDataLoader.IdentifiersPair(source,target,false),BiMappings.of(forward,reverse));
    }
    static JsonObject resource(String name) {
        try(var stream=ItemMappingData.class.getResourceAsStream("/"+name);var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {return JsonParser.parseReader(reader).getAsJsonObject();}
        catch(IOException e){throw new IllegalStateException(e);}
    }
    private static int[] array(JsonArray values){var result=new int[values.size()];for(int i=0;i<result.length;i++)result[i]=values.get(i).getAsInt();return result;}
    private static List<String> names(JsonArray rows){var result=new ArrayList<String>();for(var value:rows)result.add(value.getAsJsonObject().get("key").getAsString().replace("minecraft:",""));return result;}
}
