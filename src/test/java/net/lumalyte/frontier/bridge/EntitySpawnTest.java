package net.lumalyte.frontier.bridge;
import com.google.gson.JsonParser;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v26_2to26_3.packet.ClientboundPackets26_3;
import io.netty.buffer.Unpooled;
import java.nio.file.*;
import java.util.*;
public final class EntitySpawnTest {
    public static void main(String[] args) throws Exception {
        var m=new BridgeMappings();var p=new ProtocolSnapshot8To26_3(m);p.initialize();
        var fixtures=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject().getAsJsonArray("entities");
        for(var entry:fixtures) {
            var row=entry.getAsJsonObject();var bytes=HexFormat.of().parseHex(row.get("hex").getAsString());
            var in=Unpooled.wrappedBuffer(bytes);var original=Unpooled.wrappedBuffer(bytes);
            try {
                var out=BridgeTest.rewrite(p,ClientboundPackets26_3.ADD_ENTITY,in);
                try {
                    BridgeTest.check(Types.VAR_INT.readPrimitive(original)==Types.VAR_INT.readPrimitive(out),"Entity ID changed");
                    BridgeTest.check(Types.UUID.read(original).equals(Types.UUID.read(out)),"Entity UUID changed");
                    int type=Types.VAR_INT.readPrimitive(original);BridgeTest.check(Types.VAR_INT.readPrimitive(out)==m.entity(type),"Wrong entity type");
                    for(int i=0;i<3;i++) BridgeTest.check(original.readDouble()==out.readDouble(),"Entity position changed");
                    BridgeTest.check(Types.LOW_PRECISION_VECTOR.read(original).equals(Types.LOW_PRECISION_VECTOR.read(out)),"Entity movement changed");
                    for(int i=0;i<3;i++) BridgeTest.check(original.readByte()==out.readByte(),"Entity rotation changed");
                    int data=Types.VAR_INT.readPrimitive(original);BridgeTest.check(Types.VAR_INT.readPrimitive(out)==(m.fallingBlock(type)?m.state(data):data),"Entity spawn data wrong");
                    BridgeTest.check(!original.isReadable()&&!out.isReadable(),"Trailing spawn bytes");
                } finally { out.release(); }
            } finally {in.release();original.release();}
        }
        String result="{\"nativeEntitySpawnFixtures\":"+fixtures.size()+",\"passed\":true,\"metadataValidated\":false}";
        Files.writeString(Path.of(args[1]),result+"\n");System.out.println(result);
    }
}
