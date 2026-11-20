package net.lumalyte.frontier.bridge;
import com.google.gson.*;
import com.viaversion.viaversion.api.protocol.packet.*;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v26_2to26_3.packet.*;
import io.netty.buffer.Unpooled;
import java.nio.file.*;
import java.util.*;
public final class EntityMetadataTest {
    public static void main(String[] args)throws Exception {
        var fixtures=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        var old=JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonObject();
        var p=new ProtocolSnapshot8To26_3(new BridgeMappings());p.initialize();var u=ConnectionPipelineTest.user();p.init(u);
        var target=new CodecContext(false);var spawns=new HashMap<Integer,String>();
        for(var value:fixtures.getAsJsonArray("entities")){var row=value.getAsJsonObject();spawns.put(row.get("type").getAsInt(),row.get("hex").getAsString());}
        int count=0,fields=0;var results=new JsonArray();
        for(var value:fixtures.getAsJsonArray("metadata")) {
            var row=value.getAsJsonObject();if(row.has("schemaOnly"))continue;String key=row.get("key").getAsString();
            ConnectionPipelineTest.rewrite(p,u,Direction.CLIENTBOUND,State.PLAY,ClientboundPackets26_3.ADD_ENTITY.getId(),HexFormat.of().parseHex(spawns.get(row.get("type").getAsInt())));
            byte[] result=ConnectionPipelineTest.rewrite(p,u,Direction.CLIENTBOUND,State.PLAY,ClientboundPackets26_3.SET_ENTITY_DATA.getId(),HexFormat.of().parseHex(row.get("hex").getAsString()));
            var buffer=Unpooled.wrappedBuffer(result);
            try {
                BridgeTest.check(Types.VAR_INT.readPrimitive(buffer)==9123,"Entity id changed");
                var list=target.types.entityDataList.read(buffer);var schema=old.getAsJsonObject(key);
                BridgeTest.check(!buffer.isReadable()&&list.size()==schema.size(),"Metadata field count differs: "+key);
                for(var data:list){BridgeTest.check(schema.getAsJsonObject(Integer.toString(data.id())).get("serializer").getAsInt()==data.dataType().typeId(),"Wrong native target serializer: "+key);fields++;}
            }finally{buffer.release();}
            var exported=row.deepCopy();exported.addProperty("hex",HexFormat.of().formatHex(result));results.add(exported);count++;
        }
        var state=u.get(BridgeConnectionState.class);state.entities.remove(9123);
        ItemRoundTripTest.rejected(()->state.entities.rewrite(9123,List.of(),state.items));
        state.entities.track(9123,0);state.reset();ItemRoundTripTest.rejected(()->state.entities.rewrite(9123,List.of(),state.items));
        Files.writeString(Path.of(args[2]),new Gson().toJson(results));
        var report=Map.of("nativeEntityMetadataPackets",count,"nativeTargetFieldsChecked",fields,"removalAndReconfigureReset",true,"passed",true);
        Files.writeString(Path.of(args[3]),new Gson().toJson(report));System.out.println(new Gson().toJson(report));
    }
}
