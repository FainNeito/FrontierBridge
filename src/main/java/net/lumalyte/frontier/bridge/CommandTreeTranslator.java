package net.lumalyte.frontier.bridge;

import com.viaversion.viaversion.api.type.Types;
import io.netty.buffer.ByteBuf;
import java.util.*;

/** Native snapshot-8 command serialization to the 26.3 Brigadier wire format. */
final class CommandTreeTranslator {
    private static final Set<String> SERVER_ONLY_RESOURCES = Set.of("minecraft:advancement", "minecraft:recipe", "minecraft:worldgen/structure", "minecraft:worldgen/template_pool");
    private static final String[] SOURCE;
    private static final Map<String,Integer> TARGET=new HashMap<>();
    private static final Map<Integer,String> TARGET_NAMES=new HashMap<>();
    static {
        var registries=ItemMappingData.resource("hash-registries.json");
        var rows=registries.getAsJsonObject("snapshot").getAsJsonArray("minecraft:command_argument_type");
        SOURCE=new String[rows.size()];
        for(var row:rows){var r=row.getAsJsonObject();SOURCE[r.get("id").getAsInt()]=r.get("key").getAsString();}
        for(var row:registries.getAsJsonObject("old").getAsJsonArray("minecraft:command_argument_type")){
            var r=row.getAsJsonObject();TARGET.put(r.get("key").getAsString(),r.get("id").getAsInt());
            TARGET_NAMES.put(r.get("id").getAsInt(),r.get("key").getAsString());
        }
    }
    // Downstream Via protocols read typed fields after resetReader, not an opaque byte array.
    static void writeTyped(ByteBuf in,com.viaversion.viaversion.api.protocol.packet.PacketWrapper w) {
        int count=copy(in,w,Types.VAR_INT);
        for(int i=0;i<count;i++) {
            byte flags=copy(in,w,Types.BYTE);int kind=flags&3;
            copy(in,w,Types.VAR_INT_ARRAY_PRIMITIVE);
            if((flags&8)!=0)copy(in,w,Types.VAR_INT);
            if(kind!=0)copy(in,w,Types.STRING);
            if(kind==2) {
                String name=TARGET_NAMES.get(copy(in,w,Types.VAR_INT));
                if(name==null)throw new IllegalArgumentException("Unknown target command argument");
                switch(name) {
                    case "brigadier:double","brigadier:float","brigadier:integer","brigadier:long" -> {
                        byte bounds=copy(in,w,Types.BYTE);
                        com.viaversion.viaversion.api.type.Type<?> type=switch(name){case "brigadier:double"->Types.DOUBLE;case "brigadier:float"->Types.FLOAT;case "brigadier:integer"->Types.INT;default->Types.LONG;};
                        if((bounds&1)!=0)copy(in,w,type);if((bounds&2)!=0)copy(in,w,type);
                    }
                    case "brigadier:string" -> copy(in,w,Types.VAR_INT);
                    case "minecraft:entity","minecraft:score_holder" -> copy(in,w,Types.BYTE);
                    case "minecraft:time" -> copy(in,w,Types.INT);
                    case "minecraft:resource","minecraft:resource_key","minecraft:resource_or_tag","minecraft:resource_or_tag_key","minecraft:resource_selector" -> copy(in,w,Types.STRING);
                    default -> { }
                }
            }
            if((flags&16)!=0)copy(in,w,Types.STRING);
        }
        copy(in,w,Types.VAR_INT);
        if(in.isReadable())throw new IllegalArgumentException("Trailing typed command bytes");
    }
    private static <T> T copy(ByteBuf in,com.viaversion.viaversion.api.protocol.packet.PacketWrapper w,com.viaversion.viaversion.api.type.Type<T> type){T value=type.read(in);w.write(type,value);return value;}
    static int rewrite(ByteBuf input,ByteBuf output) {
        int count=Types.VAR_INT.readPrimitive(input);bounded(count,1,1048576);Types.VAR_INT.writePrimitive(output,count);
        for(int node=0;node<count;node++) {
            int flags=input.readUnsignedByte(),kind=flags&3;
            if(kind==3||(flags&~63)!=0)throw new IllegalArgumentException("Invalid command flags");
            output.writeByte(flags&31); // Older clients lack restricted-client execution hints; server permissions remain authoritative.
            int[] children=Types.VAR_INT_ARRAY_PRIMITIVE.read(input);
            for(int child:children)bounded(child,0,count-1);
            Types.VAR_INT_ARRAY_PRIMITIVE.write(output,children);
            if((flags&8)!=0){int redirect=Types.VAR_INT.readPrimitive(input);bounded(redirect,0,count-1);Types.VAR_INT.writePrimitive(output,redirect);}
            if(kind!=0)Types.STRING.write(output,Types.STRING.read(input));
            if(kind==2) {
                int id=Types.VAR_INT.readPrimitive(input);bounded(id,0,SOURCE.length-1);String name=SOURCE[id],mapped=name;
                if(Set.of("bool","double","float","integer","long","string").contains(name.substring(10)))mapped="brigadier:"+name.substring(10);
                if(name.equals("minecraft:pattern"))mapped="brigadier:string";
                // These server-owned registry entries are addressed by identifier on 26.3 clients.
                // ResourceKeyArgument retains identifier parsing without requiring that registry.
                if(name.equals("minecraft:resource") || name.equals("minecraft:resource_or_tag")) {
                    int propertyStart=input.readerIndex();
                    String registry=Types.STRING.read(input);
                    input.readerIndex(propertyStart);
                    if (SERVER_ONLY_RESOURCES.contains(registry)) mapped = name + "_key";
                }
                Integer target=TARGET.get(mapped);if(target==null)throw new IllegalArgumentException("Unmapped command argument "+name);
                Types.VAR_INT.writePrimitive(output,target);
                properties(name,input,output);
            }
            if((flags&16)!=0) {
                int suggestion=Types.VAR_INT.readPrimitive(input);bounded(suggestion,0,4);
                if(suggestion==4)Types.STRING_ARRAY.read(input); // Literal lists are requested from the authoritative server on older clients.
                Types.STRING.write(output,switch(suggestion){case 1->"minecraft:available_sounds";case 2->"minecraft:summonable_entities";default->"minecraft:ask_server";});
            }
        }
        int root=Types.VAR_INT.readPrimitive(input);bounded(root,0,count-1);Types.VAR_INT.writePrimitive(output,root);
        if(input.isReadable())throw new IllegalArgumentException("Trailing command bytes: "+input.readableBytes());
        return count;
    }
    private static void properties(String name,ByteBuf in,ByteBuf out) {
        switch(name) {
            case "minecraft:float","minecraft:double","minecraft:integer","minecraft:long" -> {
                int flags=in.readUnsignedByte();bounded(flags,0,3);out.writeByte(flags);
                int size=name.equals("minecraft:double")||name.equals("minecraft:long")?8:4;
                if((flags&1)!=0)out.writeBytes(in,size);if((flags&2)!=0)out.writeBytes(in,size);
            }
            case "minecraft:string" -> {int type=Types.VAR_INT.readPrimitive(in);bounded(type,0,2);Types.VAR_INT.writePrimitive(out,type);}
            case "minecraft:entity","minecraft:score_holder" -> out.writeByte(in.readUnsignedByte());
            case "minecraft:vec2","minecraft:vec3" -> in.readBoolean();
            case "minecraft:context_float_provider","minecraft:context_int_provider","minecraft:loot_modifier","minecraft:loot_predicate","minecraft:loot_table","minecraft:slot_source" -> Types.VAR_INT.readPrimitive(in);
            case "minecraft:time" -> {
                int flags=in.readUnsignedByte();bounded(flags,0,3);int min=(flags&1)!=0?in.readInt():Integer.MIN_VALUE;
                if((flags&2)!=0)in.readInt();out.writeInt(min);
            }
            case "minecraft:resource","minecraft:resource_or_tag","minecraft:resource_selector" -> Types.STRING.write(out,Types.STRING.read(in));
            case "minecraft:pattern" -> {Types.STRING.read(in);Types.TRUSTED_OPTIONAL_TAG.read(in);Types.VAR_INT.writePrimitive(out,0);}
            default -> { }
        }
    }
    private static void bounded(int value,int min,int max){if(value<min||value>max)throw new IllegalArgumentException("Command field out of range: "+value);}
}


