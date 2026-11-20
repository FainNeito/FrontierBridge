package net.lumalyte.frontier.bridge;
import com.google.gson.*;
import com.viaversion.viaversion.api.protocol.packet.*;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocol.packet.PacketWrapperImpl;
import com.viaversion.viaversion.protocols.v26_2to26_3.packet.ClientboundPackets26_3;
import com.viaversion.viaversion.exception.CancelException;
import io.netty.buffer.*;
import java.nio.file.*;
import java.util.*;
public final class Snapshot8NetworkDeltaTest {
    static byte[] bytes(ByteBuf b){byte[] v=new byte[b.readableBytes()];b.readBytes(v);return v;}
    public static void main(String[] args)throws Exception {
        var debug=new com.viaversion.viaversion.debug.DebugHandlerImpl();
        com.viaversion.viaversion.api.Via.init((com.viaversion.viaversion.api.ViaManager)java.lang.reflect.Proxy.newProxyInstance(com.viaversion.viaversion.api.ViaManager.class.getClassLoader(),new Class<?>[]{com.viaversion.viaversion.api.ViaManager.class},(proxy,method,a)->{
            if(method.getName().equals("debugHandler"))return debug;throw new UnsupportedOperationException(method.getName());
        }));
        var p=new ProtocolSnapshot8To26_3(new BridgeMappings());p.initialize();var u=ConnectionPipelineTest.user();p.init(u);
        var ids=new HashSet<Integer>();int shifted=0;
        for(var type:ClientboundPackets26_3.values()) {
            int nativeId=ProtocolSnapshot8To26_3.nativeClientboundId(State.PLAY,type.getId());
            BridgeTest.check(ids.add(nativeId),"Duplicate native ID");if(nativeId!=type.getId())shifted++;
        }
        BridgeTest.check(!ids.contains(2),"New additive motion must not alias an old packet");
        var fixture=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        byte[] commands=HexFormat.of().parseHex(fixture.get("hex").getAsString());
        var input=Unpooled.wrappedBuffer(commands);var expected=Unpooled.buffer();
        CommandTreeTranslator.rewrite(input,expected);input.release();byte[] translated=bytes(expected);expected.release();
        input=Unpooled.wrappedBuffer(commands);
        try {
            boolean cancelled=false;try{p.transform(Direction.CLIENTBOUND,State.CONFIGURATION,new PacketWrapperImpl(21,input,u));}catch(CancelException e){cancelled=true;}
            BridgeTest.check(cancelled&&!input.isReadable(),"Config commands were forwarded or incompletely read");
        }finally{input.release();}
        var state=u.get(BridgeConnectionState.class);
        BridgeTest.check(Arrays.equals(translated,state.takeCommands()),"Deferred commands changed");
        BridgeTest.check(state.takeCommands()==null,"Commands delivered twice");
        state.commands(translated);state.reset();BridgeTest.check(state.takeCommands()==null,"Commands leaked across reconfiguration");
        var play=ConnectionPipelineTest.rewrite(p,u,Direction.CLIENTBOUND,State.PLAY,ClientboundPackets26_3.COMMANDS.getId(),commands);
        BridgeTest.check(Arrays.equals(translated,play),"Play command handler differs from native-validated output");
        var typed=new PacketWrapperImpl(16,null,u);input=Unpooled.wrappedBuffer(translated);
        CommandTreeTranslator.writeTyped(input,typed);input.release();typed.resetReader();
        var names=new HashMap<Integer,String>();for(var row:ItemMappingData.resource("hash-registries.json").getAsJsonObject("old").getAsJsonArray("minecraft:command_argument_type")){
            var r=row.getAsJsonObject();names.put(r.get("id").getAsInt(),r.get("key").getAsString());
        }
        int nodes=typed.passthrough(Types.VAR_INT);
        for(int i=0;i<nodes;i++){
            byte flags=typed.passthrough(Types.BYTE);typed.passthrough(Types.VAR_INT_ARRAY_PRIMITIVE);
            if((flags&8)!=0)typed.passthrough(Types.VAR_INT);
            if((flags&3)!=0)typed.passthrough(Types.STRING);
            if((flags&3)==2)WorldEntryPackets.properties(typed,names.get(typed.passthrough(Types.VAR_INT)));
            if((flags&16)!=0)typed.passthrough(Types.STRING);
        }
        typed.passthrough(Types.VAR_INT);var out=Unpooled.buffer();typed.writeToBuffer(out);Types.VAR_INT.readPrimitive(out);
        BridgeTest.check(Arrays.equals(translated,bytes(out)),"Downstream typed command read changed payload");out.release();
        for(boolean immediate:new boolean[]{false,true}) {
            var move=Unpooled.buffer();Types.VAR_INT.writePrimitive(move,123);Types.VAR_INT.writePrimitive(move,5);move.writeBoolean(immediate);
            for(int i=0;i<2;i++){Types.VAR_INT.writePrimitive(move,i+1);move.writeShort(20);move.writeShort(-10);move.writeShort(4);}
            var old=Unpooled.wrappedBuffer(ConnectionPipelineTest.rewrite(p,u,Direction.CLIENTBOUND,State.PLAY,ClientboundPackets26_3.MOVE_ENTITY_POS.getId(),bytes(move)));move.release();
            BridgeTest.check(Types.VAR_INT.readPrimitive(old)==123&&Types.VAR_INT.readPrimitive(old)==5,"Movement header changed");
            for(int i=0;i<2;i++)BridgeTest.check(Types.VAR_INT.readPrimitive(old)==i+1&&old.readShort()==20&&old.readShort()==-10&&old.readShort()==4,"Movement delta misaligned");
            BridgeTest.check(!old.isReadable(),"Trailing movement bytes");old.release();
        }
        var report=Map.of("mappedPlayPacketIds",ids.size(),"shiftedPlayPacketIds",shifted,"typedCommandNodes",nodes,"configurationCommandsDeferredAndReset",true,"steppedMovementCases",2,"passed",true,"networkClientTest",false);
        Files.writeString(Path.of(args[1]),new GsonBuilder().setPrettyPrinting().create().toJson(report));System.out.println(new Gson().toJson(report));
    }
}
