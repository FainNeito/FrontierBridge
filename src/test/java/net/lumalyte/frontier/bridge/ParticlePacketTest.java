package net.lumalyte.frontier.bridge;
import com.google.gson.*;
import com.viaversion.viaversion.api.protocol.packet.*;
import com.viaversion.viaversion.protocols.v26_2to26_3.packet.ClientboundPackets26_3;
import com.viaversion.viaversion.api.minecraft.item.Item;
import io.netty.buffer.Unpooled;
import java.nio.file.*;
import java.util.*;
public final class ParticlePacketTest {
    public static void main(String[] args)throws Exception {
        var source=new CodecContext(true);var target=new CodecContext(false);var mappings=new BridgeMappings();
        var p=new ProtocolSnapshot8To26_3(mappings);p.initialize();var u=ConnectionPipelineTest.user();p.init(u);var tables=ItemMappingData.resource("bridge-mappings.json");
        var fixtures=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray();var output=new JsonArray();int count=0;
        for(var value:fixtures) {
            var row=value.getAsJsonObject();byte[] original=HexFormat.of().parseHex(row.get("hex").getAsString());
            var translated=ConnectionPipelineTest.rewrite(p,u,Direction.CLIENTBOUND,State.PLAY,ClientboundPackets26_3.LEVEL_PARTICLES.getId(),original);
            var in=Unpooled.wrappedBuffer(original);var out=Unpooled.wrappedBuffer(translated);
            try {
                var a=source.types.particle.read(in);var b=target.types.particle.read(out);
                BridgeTest.check(b.id()==tables.getAsJsonArray("particles").get(a.id()).getAsInt(),"Particle id mismatch");
                String key=row.get("key").getAsString();
                if(key.equals("minecraft:item")){var item=(Item)b.getArgument(0).getValue();BridgeTest.check(u.get(BridgeConnectionState.class).items.toServer(item).identifier()==((Item)a.getArgument(0).getValue()).identifier(),"Particle item restoration failed");}
                else if(!a.getArguments().isEmpty())BridgeTest.check(((Integer)b.getArgument(0).getValue())==mappings.state((Integer)a.getArgument(0).getValue()),"Particle block state mismatch");
                byte[] tail=new byte[in.readableBytes()],newTail=new byte[out.readableBytes()];in.readBytes(tail);out.readBytes(newTail);BridgeTest.check(Arrays.equals(tail,newTail),"Particle motion/randomization changed");
            }finally{in.release();out.release();}
            var changed=row.deepCopy();changed.addProperty("hex",HexFormat.of().formatHex(translated));output.add(changed);count++;
        }
        Files.writeString(Path.of(args[1]),new Gson().toJson(output));var report=Map.of("nativeParticlePackets",count,"motionAndRandomizationPreserved",true,"itemTemplateAndBlockStatesMapped",true,"passed",true);Files.writeString(Path.of(args[2]),new GsonBuilder().setPrettyPrinting().create().toJson(report));System.out.println(new Gson().toJson(report));
    }
}
