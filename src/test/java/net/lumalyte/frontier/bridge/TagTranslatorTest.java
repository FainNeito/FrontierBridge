package net.lumalyte.frontier.bridge;
import com.google.gson.*;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.api.protocol.packet.*;
import com.viaversion.viaversion.protocols.v26_2to26_3.packet.*;
import io.netty.buffer.*;
import java.nio.file.*;
import java.util.*;
public final class TagTranslatorTest {
    static Map<String,Map<String,Set<String>>> read(byte[] bytes,JsonObject registries) {
        var b=Unpooled.wrappedBuffer(bytes);var result=new LinkedHashMap<String,Map<String,Set<String>>>();
        try {
            int count=Types.VAR_INT.readPrimitive(b);
            for(int r=0;r<count;r++) {
                var registry=Types.STRING.read(b);var rows=registries.getAsJsonArray(registry);var tags=new LinkedHashMap<String,Set<String>>();
                int n=Types.VAR_INT.readPrimitive(b);
                for(int t=0;t<n;t++){String name=Types.STRING.read(b);var members=new LinkedHashSet<String>();for(int id:Types.VAR_INT_ARRAY_PRIMITIVE.read(b))members.add(rows.get(id).getAsJsonObject().get("key").getAsString());tags.put(name,members);}
                result.put(registry,tags);
            }
            BridgeTest.check(!b.isReadable(),"Trailing tags");return result;
        }finally{b.release();}
    }
    public static void main(String[] args)throws Exception {
        var p=new ProtocolSnapshot8To26_3(new BridgeMappings());p.initialize();var u=ConnectionPipelineTest.user();p.init(u);
        var state=u.get(BridgeConnectionState.class);
        for(var row:JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject().getAsJsonArray("registries")) {
            var b=Unpooled.wrappedBuffer(HexFormat.of().parseHex(row.getAsJsonObject().get("hex").getAsString()));var out=Unpooled.buffer();try{state.registries.rewrite(b,out);}finally{b.release();out.release();}
        }
        byte[] original=HexFormat.of().parseHex(JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonObject().get("hex").getAsString());
        byte[] result=ConnectionPipelineTest.rewrite(p,u,Direction.CLIENTBOUND,State.CONFIGURATION,ClientboundConfigurationPackets26_3.UPDATE_TAGS.getId(),original);
        byte[] play=ConnectionPipelineTest.rewrite(p,u,Direction.CLIENTBOUND,State.PLAY,ClientboundPackets26_3.UPDATE_TAGS.getId(),original);
        BridgeTest.check(Arrays.equals(result,play),"Configuration/play tag handlers differ");
        var source=read(original,state.registries.keyRegistries(false));var actual=read(result,state.registries.keyRegistries(true));int tags=0,members=0,removed=0;
        var target=state.registries.keyRegistries(true);
        for(var registry:source.entrySet()) {
            if(registry.getKey().equals("minecraft:block_sound_set")){BridgeTest.check(!actual.containsKey(registry.getKey()),"Unsupported registry survived");continue;}
            var known=new HashSet<String>();for(var row:target.getAsJsonArray(registry.getKey()))known.add(row.getAsJsonObject().get("key").getAsString());
            var expected=new LinkedHashMap<String,Set<String>>();
            for(var tag:registry.getValue().entrySet()){var values=new LinkedHashSet<>(tag.getValue());values.retainAll(known);removed+=tag.getValue().size()-values.size();members+=values.size();tags++;expected.put(tag.getKey(),values);}
            BridgeTest.check(expected.equals(actual.get(registry.getKey())),"Tag membership changed identity: "+registry.getKey());
        }
        var in=Unpooled.wrappedBuffer(Arrays.copyOf(original,original.length+1));var out=Unpooled.buffer();
        try{ItemRoundTripTest.rejected(()->new TagTranslator().rewrite(in,out,state.registries));BridgeTest.check(!out.isReadable(),"Invalid packet partly emitted");}finally{in.release();out.release();}
        Files.writeString(Path.of(args[2]),new Gson().toJson(Map.of("hex",HexFormat.of().formatHex(result))));
        var report=Map.of("registries",actual.size(),"tags",tags,"members",members,"newOnlyMembershipsOmitted",removed,"configAndPlayVerified",true,"passed",true);
        Files.writeString(Path.of(args[3]),new GsonBuilder().setPrettyPrinting().create().toJson(report));System.out.println(new Gson().toJson(report));
    }
}
