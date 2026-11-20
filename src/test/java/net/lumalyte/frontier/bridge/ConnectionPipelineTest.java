package net.lumalyte.frontier.bridge;
import com.google.gson.*;
import com.viaversion.viaversion.api.connection.*;
import com.viaversion.viaversion.api.protocol.packet.*;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocol.packet.PacketWrapperImpl;
import com.viaversion.viaversion.protocols.v26_2to26_3.packet.*;
import com.viaversion.viaversion.protocols.v1_21_7to1_21_9.packet.ServerboundConfigurationPackets1_21_9;
import com.viaversion.viaversion.api.type.types.chunk.ChunkType26_1;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
public final class ConnectionPipelineTest {
    static UserConnection user() {
        var objects=new HashMap<Class<?>,Object>();
        State[] states={State.HANDSHAKE,State.HANDSHAKE};
        var info=(ProtocolInfo)Proxy.newProxyInstance(ProtocolInfo.class.getClassLoader(),new Class<?>[]{ProtocolInfo.class},(proxy,method,args)->switch(method.getName()){
            case "getClientState"->states[0];case "getServerState"->states[1];
            case "setClientState"->{states[0]=(State)args[0];yield null;}case "setServerState"->{states[1]=(State)args[0];yield null;}
            default->throw new UnsupportedOperationException("Unexpected protocol info method: "+method.getName());
        });
        return (UserConnection)Proxy.newProxyInstance(UserConnection.class.getClassLoader(),new Class<?>[]{UserConnection.class},(proxy,method,args)->{
            if(method.getName().equals("put")){objects.put(args[0].getClass(),args[0]);return null;}
            if(method.getName().equals("get"))return objects.get(args[0]);
            if(method.getName().equals("getProtocolInfo"))return info;
            throw new UnsupportedOperationException("Unexpected connection method: "+method.getName());
        });
    }
    static byte[] rewrite(ProtocolSnapshot8To26_3 p,UserConnection u,Direction d,State s,int id,byte[] bytes)throws Exception {
        var in=Unpooled.wrappedBuffer(bytes);var out=Unpooled.buffer();
        try {
            var wrapper=new PacketWrapperImpl(d==Direction.CLIENTBOUND?ProtocolSnapshot8To26_3.nativeClientboundId(s,id):id,in,u);p.transform(d,s,wrapper);wrapper.writeToBuffer(out);
            BridgeTest.check(Types.VAR_INT.readPrimitive(out)==id,"Packet ID changed");
            var result=new byte[out.readableBytes()];out.readBytes(result);return result;
        }finally{in.release();out.release();}
    }
    public static void main(String[] args)throws Exception {
        var m=new BridgeMappings();var p=new ProtocolSnapshot8To26_3(m);p.initialize();var u=user();p.init(u);
        var fixtures=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        for(var value:fixtures.getAsJsonArray("registries")) {
            var row=value.getAsJsonObject();if(row.get("key").getAsString().equals("minecraft:block_sound_set"))continue; // suppression checked by RegistryTranslatorTest
            rewrite(p,u,Direction.CLIENTBOUND,State.CONFIGURATION,ClientboundConfigurationPackets26_3.REGISTRY_DATA.getId(),HexFormat.of().parseHex(row.get("hex").getAsString()));
        }
        var output=new JsonArray();int chunks=0;
        var chunkFixtures=JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonArray();
        for(var value:fixtures.getAsJsonArray("logins")) {
            var row=value.getAsJsonObject();var translated=row.deepCopy();
            for(String kind:new String[]{"login","respawn"}) {
                var packet=kind.equals("login")?ClientboundPackets26_3.LOGIN:ClientboundPackets26_3.RESPAWN;
                var bytes=rewrite(p,u,Direction.CLIENTBOUND,State.PLAY,packet.getId(),HexFormat.of().parseHex(row.get(kind).getAsString()));
                translated.addProperty(kind,HexFormat.of().formatHex(bytes));
            }
            output.add(translated);
            var state=u.get(BridgeConnectionState.class);
            for(var c:chunkFixtures) {
                var chunk=c.getAsJsonObject();if(!chunk.get("world").getAsString().equals(row.get("world").getAsString()))continue;
                var bytes=rewrite(p,u,Direction.CLIENTBOUND,State.PLAY,ClientboundPackets26_3.LEVEL_CHUNK_WITH_LIGHT.getId(),HexFormat.of().parseHex(chunk.get("hex").getAsString()));
                var buf=Unpooled.wrappedBuffer(bytes);
                try {var decoded=new ChunkType26_1(state.sections(),ChunkTranslator.bits(m.oldStateCount()),ChunkTranslator.bits(state.registries.biomeCount())).read(buf);
                    BridgeTest.check(decoded.getSections().length==chunk.get("sections").getAsInt(),"Wrong dimension height used by packet pipeline");chunks++;
                }finally{buf.release();}
            }
        }
        var packs=Unpooled.buffer();Types.VAR_INT.writePrimitive(packs,1);Types.STRING.write(packs,"minecraft");Types.STRING.write(packs,"core");Types.STRING.write(packs,"26.3");
        var offered=new byte[packs.readableBytes()];packs.readBytes(offered);packs.release();
        var accepted=rewrite(p,u,Direction.SERVERBOUND,State.CONFIGURATION,ServerboundConfigurationPackets1_21_9.SELECT_KNOWN_PACKS.getId(),offered);
        BridgeTest.check(Arrays.equals(accepted,new byte[]{0}),"Known packs must be empty for complete registries");
        var second=user();p.init(second);boolean rejected=false;try{second.get(BridgeConnectionState.class).sections();}catch(IllegalStateException e){rejected=true;}
        BridgeTest.check(rejected,"Dimension state leaked between users");
        rewrite(p,u,Direction.CLIENTBOUND,State.PLAY,ClientboundPackets26_3.START_CONFIGURATION.getId(),new byte[0]);
        rejected=false;try{u.get(BridgeConnectionState.class).sections();}catch(IllegalStateException e){rejected=true;}BridgeTest.check(rejected,"Reconfiguration reused old dimension");
        Files.writeString(Path.of(args[2]),new GsonBuilder().setPrettyPrinting().create().toJson(output));
        var report=Map.of("loginCases",output.size(),"respawnCases",output.size(),"wiredDimensionChunks",chunks,"knownPacksForcedEmpty",true,"connectionIsolationAndResetPassed",true);
        Files.writeString(Path.of(args[3]),new Gson().toJson(report));System.out.println(new Gson().toJson(report));
    }
}
