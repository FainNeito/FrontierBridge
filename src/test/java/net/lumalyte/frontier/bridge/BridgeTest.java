package net.lumalyte.frontier.bridge;

import com.google.gson.JsonParser;
import com.viaversion.viaversion.api.protocol.packet.*;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocol.packet.PacketWrapperImpl;
import com.viaversion.viaversion.protocols.v26_2to26_3.packet.ClientboundPackets26_3;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

public final class BridgeTest {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static ByteBuf rewrite(ProtocolSnapshot8To26_3 protocol, ClientboundPackets26_3 packet, ByteBuf input) throws Exception {
        var output = Unpooled.buffer();
        try {
            var wrapper = new PacketWrapperImpl(packet.getId(), input, null);
            protocol.transform(Direction.CLIENTBOUND, State.PLAY, wrapper);
            wrapper.writeToBuffer(output);
            check(Types.VAR_INT.readPrimitive(output) == packet.getId(), "Packet ID changed");
            return output;
        } catch (Exception e) { output.release(); throw e; }
    }
    public static void main(String[] args) throws Exception {
        var mappings = new BridgeMappings();
        var protocol = new ProtocolSnapshot8To26_3(mappings);
        protocol.initialize();
        var fixtures = JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray();
        int verified = 0;
        for (var value : fixtures) {
            var fixture = value.getAsJsonObject();
            var input = Unpooled.wrappedBuffer(HexFormat.of().parseHex(fixture.get("hex").getAsString()));
            long originalPosition = input.getLong(0);
            try {
                var output = rewrite(protocol, ClientboundPackets26_3.BLOCK_UPDATE, input);
                try {
                    check(output.readLong() == originalPosition, "Block position changed");
                    check(Types.VAR_INT.readPrimitive(output) == mappings.state(fixture.get("state").getAsInt()), "Wrong state");
                    check(!output.isReadable(), "Trailing bytes in real block fixture");
                    verified++;
                } finally { output.release(); }
            } finally { input.release(); }
        }
        // Exhaustive mixed section batches: preserve packed local positions and state IDs.
        int sectionStates = 0;
        for (int start = 0; start < mappings.stateCount(); start += 4096) {
            int count = Math.min(4096, mappings.stateCount() - start);
            var input = Unpooled.buffer();
            long position = -9223372036854775000L;
            input.writeLong(position); Types.VAR_INT.writePrimitive(input, count);
            for (int i = 0; i < count; i++) Types.VAR_LONG.writePrimitive(input, ((long)(start + i) << 12) | i);
            try {
                var output = rewrite(protocol, ClientboundPackets26_3.SECTION_BLOCKS_UPDATE, input);
                try {
                    check(output.readLong() == position, "Section position changed");
                    check(Types.VAR_INT.readPrimitive(output) == count, "Wrong section count");
                    for (int i = 0; i < count; i++) {
                        long packed = Types.VAR_LONG.readPrimitive(output);
                        check((packed & 4095) == i, "Local block position changed");
                        check((packed >>> 12) == mappings.state(start+i), "Wrong section state");
                        sectionStates++;
                    }
                    check(!output.isReadable(), "Trailing section bytes");
                } finally { output.release(); }
            } finally { input.release(); }
        }
        for (int state = 0; state < mappings.stateCount(); state++) {
            var input = Unpooled.buffer().writeInt(2001).writeLong(1234567L).writeInt(state).writeBoolean(true);
            try {
                var output = rewrite(protocol, ClientboundPackets26_3.LEVEL_EVENT, input);
                try {
                    check(output.readInt()==2001 && output.readLong()==1234567L, "Level event header changed");
                    check(output.readInt()==mappings.state(state) && output.readBoolean() && !output.isReadable(), "Level event data wrong");
                } finally { output.release(); }
            } finally { input.release(); }
        }
        int blockEvents = 0;
        for (var value : fixtures) {
            // Exported fixtures are in real registry order.
            var input = Unpooled.buffer().writeLong(-100L).writeByte(255).writeByte(128);
            Types.VAR_INT.writePrimitive(input, blockEvents);
            try {
                var output = rewrite(protocol, ClientboundPackets26_3.BLOCK_EVENT, input);
                try {
                    check(output.readLong()==-100L && output.readUnsignedByte()==255 && output.readUnsignedByte()==128, "Block event header changed");
                    check(Types.VAR_INT.readPrimitive(output)==mappings.block(blockEvents) && !output.isReadable(), "Block event ID wrong");
                } finally { output.release(); }
            } finally { input.release(); }
            blockEvents++;
        }
        boolean refused = false;
        try { protocol.transform(Direction.SERVERBOUND, State.PLAY, new PacketWrapperImpl(0, null, null)); }
        catch (IllegalArgumentException expected) { refused = true; }
        check(refused, "Unimplemented direction was allowed through");
        refused = false;
        try { protocol.transform(Direction.CLIENTBOUND, State.CONFIGURATION, new PacketWrapperImpl(0, null, null)); }
        catch (IllegalArgumentException expected) { refused = true; }
        check(refused, "Unimplemented configuration was allowed through");
        refused = false;
        try { mappings.state(mappings.stateCount()); } catch (IllegalArgumentException expected) { refused = true; }
        check(refused, "Unknown ID was accepted");
        String result = "{\"realServerBlockFixtures\":"+verified+",\"sectionStates\":"+sectionStates+
            ",\"breakEvents\":"+mappings.stateCount()+",\"blockEvents\":"+blockEvents+",\"liveProtocolRegistered\":false,\"passed\":true}";
        Files.writeString(Path.of(args[1]), result + "\n");
        System.out.println(result);
    }
}
