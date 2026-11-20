package net.lumalyte.frontier.bridge;
import com.viaversion.viaversion.api.protocol.packet.*;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.api.type.types.version.VersionedTypesHolder;
import com.viaversion.viaversion.api.minecraft.item.data.trim.ArmorTrimPattern;
import com.viaversion.viaversion.protocols.v26_2to26_3.packet.ClientboundPackets26_3;
import com.viaversion.viaversion.exception.InformativeException;
import io.netty.buffer.Unpooled;

final class RecipePackets {
    private final CodecContext source,target;
    private final VersionedTypesHolder client;
    private final ItemMappingData mappings=new ItemMappingData();
    RecipePackets(CodecContext source,CodecContext target,VersionedTypesHolder client) {
        this.source=source;this.target=target;this.client=client;
        var registries=ItemMappingData.resource("hash-registries.json");
        for(String key:new String[]{"minecraft:slot_display","minecraft:recipe_display","minecraft:recipe_book_category"}) {
            var a=registries.getAsJsonObject("snapshot").getAsJsonArray(key);var b=registries.getAsJsonObject("old").getAsJsonArray(key);
            if(!a.equals(b))throw new IllegalStateException("Recipe registry changed: "+key);
        }
    }
    void register(ProtocolSnapshot8To26_3 p) {
        SessionPackets.fields(p,State.PLAY,Direction.CLIENTBOUND,"RECIPE_BOOK_REMOVE",Types.VAR_INT_ARRAY_PRIMITIVE);
        SessionPackets.fields(p,State.PLAY,Direction.CLIENTBOUND,"RECIPE_BOOK_SETTINGS",Types.BOOLEAN,Types.BOOLEAN,Types.BOOLEAN,Types.BOOLEAN,Types.BOOLEAN,Types.BOOLEAN,Types.BOOLEAN,Types.BOOLEAN);
        SessionPackets.fields(p,State.PLAY,Direction.SERVERBOUND,"PLACE_RECIPE",Types.VAR_INT,Types.VAR_INT,Types.BOOLEAN);
        SessionPackets.fields(p,State.PLAY,Direction.SERVERBOUND,"RECIPE_BOOK_SEEN_RECIPE",Types.VAR_INT);
        SessionPackets.fields(p,State.PLAY,Direction.SERVERBOUND,"RECIPE_BOOK_CHANGE_SETTINGS",Types.VAR_INT,Types.BOOLEAN,Types.BOOLEAN);
        register(p,ClientboundPackets26_3.UPDATE_RECIPES,w->{
            int groups=count(w);for(int i=0;i<groups;i++){w.passthrough(Types.STRING);int[] ids=w.read(Types.VAR_INT_ARRAY_PRIMITIVE);w.write(Types.VAR_INT_ARRAY_PRIMITIVE,java.util.Arrays.stream(ids).map(this::itemId).distinct().toArray());}
            int recipes=count(w);for(int i=0;i<recipes;i++){ingredient(w);slot(w,0);}
        });
        register(p,ClientboundPackets26_3.RECIPE_BOOK_ADD,w->{
            int recipes=count(w);for(int i=0;i<recipes;i++) {
                w.passthrough(Types.VAR_INT);display(w);w.passthrough(Types.OPTIONAL_VAR_INT);w.passthrough(Types.VAR_INT);
                if(w.passthrough(Types.BOOLEAN)){int ingredients=count(w);for(int j=0;j<ingredients;j++)ingredient(w);}
                w.passthrough(Types.BYTE);
            }
            w.passthrough(Types.BOOLEAN); // Replace existing recipe book.
        });
        register(p,ClientboundPackets26_3.PLACE_GHOST_RECIPE,w->{w.passthrough(Types.VAR_INT);display(w);});
    }
    private void display(PacketWrapper w)throws InformativeException {
        int kind=w.passthrough(Types.VAR_INT);
        switch(kind) {
            case 0 -> {slots(w,0);slot(w,0);slot(w,0);}
            case 1 -> {w.passthrough(Types.VAR_INT);w.passthrough(Types.VAR_INT);slots(w,0);slot(w,0);slot(w,0);}
            case 2 -> {for(int i=0;i<4;i++)slot(w,0);w.passthrough(Types.VAR_INT);w.passthrough(Types.FLOAT);}
            case 3 -> {for(int i=0;i<3;i++)slot(w,0);}
            case 4 -> {for(int i=0;i<5;i++)slot(w,0);}
            default -> throw new IllegalArgumentException("Unknown recipe display "+kind);
        }
    }
    private void slot(PacketWrapper w,int depth)throws InformativeException {
        if(depth>64)throw new IllegalArgumentException("Recipe display nesting limit");int kind=w.passthrough(Types.VAR_INT);
        switch(kind) {
            case 0,1 -> { }
            case 2 -> slot(w,depth+1);
            case 3 -> {slot(w,depth+1);int id=w.read(Types.VAR_INT);int mapped=mappings.getDataComponentSerializerMappings().getNewId(id);if(mapped<0)throw new IllegalArgumentException("Unknown component");w.write(Types.VAR_INT,mapped);}
            case 4 -> w.write(Types.VAR_INT,itemId(w.read(Types.VAR_INT)));
            case 5 -> {
                var item=w.user().get(BridgeConnectionState.class).items.toClientDisplay(w.read(source.types.itemTemplate));
                var buffer=Unpooled.buffer();try{target.types.itemTemplate.write(buffer,item);w.write(client.itemTemplate(),client.itemTemplate().read(buffer));}finally{buffer.release();}
            }
            case 6 -> ingredient(w);
            case 7,9 -> {slot(w,depth+1);slot(w,depth+1);}
            case 8 -> {slot(w,depth+1);slot(w,depth+1);w.passthrough(ArmorTrimPattern.TYPE1_21_5);}
            case 10 -> slots(w,depth+1);
            default -> throw new IllegalArgumentException("Unknown slot display "+kind);
        }
    }
    private void slots(PacketWrapper w,int depth)throws InformativeException {int size=count(w);for(int i=0;i<size;i++)slot(w,depth);}
    private void ingredient(PacketWrapper w)throws InformativeException {var set=w.passthrough(Types.HOLDER_SET);if(!set.hasTagKey()){int[] ids=set.ids();for(int i=0;i<ids.length;i++)ids[i]=itemId(ids[i]);}}
    private int itemId(int id){var table=mappings.getItemMappings();if(id<0||id>=table.size())throw new IllegalArgumentException("Unknown ingredient item");int result=table.getNewId(id);if(result<0)throw new IllegalArgumentException("Unmapped ingredient item");return result;}
    private static int count(PacketWrapper w)throws InformativeException {int value=w.passthrough(Types.VAR_INT);if(value<0||value>1048576)throw new IllegalArgumentException("Invalid recipe count");return value;}
    private static void register(ProtocolSnapshot8To26_3 p,ClientboundPackets26_3 type,com.viaversion.viaversion.api.protocol.remapper.PacketHandler h){p.registerClientbound(State.PLAY,type.getId(),type.getId(),h,false);}
}
