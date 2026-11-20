package net.lumalyte.frontier.bridge;

import com.google.gson.JsonParser;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v26_2to26_3.packet.ClientboundPackets26_3;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

public final class SoundTranslatorTest {
    public static void main(String[] args) throws Exception {
        var mappings = new BridgeMappings();
        var protocol = new ProtocolSnapshot8To26_3(mappings); protocol.initialize();
        var fixtures = JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray();
        for (var value : fixtures) {
            var fixture = value.getAsJsonObject();
            var input = Unpooled.wrappedBuffer(HexFormat.of().parseHex(fixture.get("hex").getAsString()));
            try {
                var output = BridgeTest.rewrite(protocol,ClientboundPackets26_3.SOUND,input);
                try {
                    BridgeTest.check(Types.VAR_INT.readPrimitive(output)==mappings.sound(fixture.get("id").getAsInt())+1,"Sound holder mismatch");
                    BridgeTest.check(Types.VAR_INT.readPrimitive(output)==4,"Sound category changed");
                    BridgeTest.check(output.readInt()==-100 && output.readInt()==560 && output.readInt()==362,"Sound position changed");
                    BridgeTest.check(output.readFloat()==0.75f && output.readFloat()==1.25f && output.readLong()==123456789L,"Sound parameters changed");
                    BridgeTest.check(!output.isReadable(),"Trailing sound bytes");
                } finally { output.release(); }
            } finally { input.release(); }
        }
        for (int id=0; id<mappings.soundCount(); id++) {
            var input=Unpooled.buffer();
            Types.VAR_INT.writePrimitive(input,id+1); Types.VAR_INT.writePrimitive(input,7); Types.VAR_INT.writePrimitive(input,12345);
            input.writeFloat(1.5f).writeFloat(0.5f).writeLong(-123L);
            try {
                var output=BridgeTest.rewrite(protocol,ClientboundPackets26_3.SOUND_ENTITY,input);
                try {
                    BridgeTest.check(Types.VAR_INT.readPrimitive(output)==mappings.sound(id)+1,"Entity sound mismatch");
                    BridgeTest.check(Types.VAR_INT.readPrimitive(output)==7 && Types.VAR_INT.readPrimitive(output)==12345,"Entity sound target changed");
                    BridgeTest.check(output.readFloat()==1.5f && output.readFloat()==0.5f && output.readLong()==-123L && !output.isReadable(),"Entity sound parameters changed");
                } finally { output.release(); }
            } finally { input.release(); }
        }
        // Inline sound holders retain their optional fixed range and packet tail.
        for (String name : new String[]{"minecraft:entity.ice_ball.throw","custom:untouched"}) {
            var input=Unpooled.buffer(); Types.VAR_INT.writePrimitive(input,0); Types.STRING.write(input,name);
            input.writeBoolean(true).writeFloat(16f); Types.VAR_INT.writePrimitive(input,7); Types.VAR_INT.writePrimitive(input,123);
            input.writeFloat(1f).writeFloat(1f).writeLong(0L);
            try {
                var output=BridgeTest.rewrite(protocol,ClientboundPackets26_3.SOUND_ENTITY,input);
                try {
                    BridgeTest.check(Types.VAR_INT.readPrimitive(output)==0 && Types.STRING.read(output).equals(mappings.soundName(name)),"Inline sound name mismatch");
                    BridgeTest.check(output.readBoolean() && output.readFloat()==16f,"Inline sound range changed");
                    BridgeTest.check(Types.VAR_INT.readPrimitive(output)==7 && Types.VAR_INT.readPrimitive(output)==123,"Inline sound body changed");
                    BridgeTest.check(output.readFloat()==1f && output.readFloat()==1f && output.readLong()==0L && !output.isReadable(),"Inline sound tail changed");
                } finally { output.release(); }
            } finally { input.release(); }
        }
        for (int flags=0; flags<4; flags++) {
            var input=Unpooled.buffer().writeByte(flags);
            if ((flags&1)!=0) Types.VAR_INT.writePrimitive(input,4);
            if ((flags&2)!=0) Types.STRING.write(input,"minecraft:entity.ice_ball.throw");
            try {
                var output=BridgeTest.rewrite(protocol,ClientboundPackets26_3.STOP_SOUND,input);
                try {
                    BridgeTest.check(output.readByte()==flags,"Stop sound flags changed");
                    if ((flags&1)!=0) BridgeTest.check(Types.VAR_INT.readPrimitive(output)==4,"Stop sound category changed");
                    if ((flags&2)!=0) BridgeTest.check(Types.STRING.read(output).equals("minecraft:entity.snowball.throw"),"Stop sound fallback mismatch");
                    BridgeTest.check(!output.isReadable(),"Trailing stop sound bytes");
                } finally { output.release(); }
            } finally { input.release(); }
        }
        String result="{\"realSoundFixtures\":"+fixtures.size()+",\"entitySoundCases\":"+mappings.soundCount()+",\"inlineCases\":2,\"stopSoundCases\":4,\"passed\":true}";
        Files.writeString(Path.of(args[1]),result+"\n"); System.out.println(result);
    }
}
