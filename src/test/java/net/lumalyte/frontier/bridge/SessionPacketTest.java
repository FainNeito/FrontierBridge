package net.lumalyte.frontier.bridge;
import com.google.gson.*;
import com.viaversion.viaversion.api.protocol.packet.*;
import java.nio.file.*;
import java.util.*;
public final class SessionPacketTest {
    public static void main(String[] args)throws Exception {
        var p=new ProtocolSnapshot8To26_3(new BridgeMappings());p.initialize();var user=ConnectionPipelineTest.user();p.init(user);int count=0;
        for(var value:JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray()) {
            var row=value.getAsJsonObject();var direction=Direction.valueOf(row.get("direction").getAsString());var state=State.valueOf(row.get("state").getAsString());
            if(row.get("name").getAsString().equals("CHUNKS_BIOMES")) {
                var registries=JsonParser.parseString(Files.readString(Path.of(args[0]).resolveSibling("bridge-extra-fixtures-snapshot8.json"))).getAsJsonObject().getAsJsonArray("registries");
                for(var registry:registries){var entry=registry.getAsJsonObject();if(entry.get("key").getAsString().equals("minecraft:worldgen/biome"))ConnectionPipelineTest.rewrite(p,user,Direction.CLIENTBOUND,State.CONFIGURATION,SessionPackets.type(State.CONFIGURATION,Direction.CLIENTBOUND,"REGISTRY_DATA").getId(),HexFormat.of().parseHex(entry.get("hex").getAsString()));}
            }
            var bytes=HexFormat.of().parseHex(row.get("hex").getAsString());int id=SessionPackets.type(state,direction,row.get("name").getAsString()).getId();
            var expected=row.has("targetHex")?HexFormat.of().parseHex(row.get("targetHex").getAsString()):bytes;
            if(row.has("targetCancelled")&&row.get("targetCancelled").getAsBoolean()) {
                var field=com.viaversion.viaversion.api.protocol.AbstractProtocol.class.getDeclaredField("clientboundMappings");field.setAccessible(true);
                var mappings=(com.viaversion.viaversion.api.protocol.packet.mapping.PacketMappings)field.get(p);var input=io.netty.buffer.Unpooled.wrappedBuffer(bytes);
                try{var wrapper=new com.viaversion.viaversion.protocol.packet.PacketWrapperImpl(id,input,user);mappings.mappedPacket(state,id).handler().handle(wrapper);BridgeTest.check(wrapper.isCancelled()&&!input.isReadable(),"Unsupported effect not fully consumed and suppressed");}finally{input.release();}
                count++;continue;
            }
            try {BridgeTest.check(Arrays.equals(expected,ConnectionPipelineTest.rewrite(p,user,direction,state,id,bytes)),"Session payload changed: "+row);}catch(Exception error){
                var field=com.viaversion.viaversion.api.protocol.AbstractProtocol.class.getDeclaredField(direction==Direction.CLIENTBOUND?"clientboundMappings":"serverboundMappings");field.setAccessible(true);
                var mappings=(com.viaversion.viaversion.api.protocol.packet.mapping.PacketMappings)field.get(p);var input=io.netty.buffer.Unpooled.wrappedBuffer(bytes);
                try{mappings.mappedPacket(state,id).handler().handle(new com.viaversion.viaversion.protocol.packet.PacketWrapperImpl(id,input,user));}catch(Exception cause){throw new IllegalStateException("Handler failed: "+row,cause);}finally{input.release();}
                throw new IllegalStateException("Protocol failed: "+row,error);
            }count++;
        }
        var report=Map.of("fixtures",count,"passed",true);Files.writeString(Path.of(args[1]),new GsonBuilder().setPrettyPrinting().create().toJson(report));System.out.println(new Gson().toJson(report));
        var coverage=new LinkedHashMap<String,Object>();
        for(State state:new State[]{State.CONFIGURATION,State.PLAY})for(Direction direction:Direction.values()) {
            var registered=new ArrayList<String>();var missing=new ArrayList<String>();
            for(var value:SessionPackets.type(state,direction,"KEEP_ALIVE").getClass().getEnumConstants()) {
                var packet=(PacketType)value;boolean present=direction==Direction.CLIENTBOUND?p.hasRegisteredClientbound(state,packet.getId()):p.hasRegisteredServerbound(state,packet.getId());
                (present?registered:missing).add(packet.getName());
            }
            coverage.put(state+"/"+direction,Map.of("registered",registered,"missing",missing));
        }
        Files.writeString(Path.of(args[1]).resolveSibling("bridge-handler-coverage.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("scope","Explicit kernel registration; not proof of complete schema or network compatibility","packets",coverage)));
    }
}
