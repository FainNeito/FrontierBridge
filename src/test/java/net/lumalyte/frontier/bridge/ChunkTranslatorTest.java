package net.lumalyte.frontier.bridge;

import com.google.gson.JsonParser;
import com.viaversion.viaversion.api.minecraft.chunks.PaletteType;
import com.viaversion.viaversion.api.type.types.chunk.ChunkType26_1;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;

public final class ChunkTranslatorTest {
    public static void main(String[] args) throws Exception {
        var mappings = new BridgeMappings();
        var translator = new ChunkTranslator(mappings);
        var fixtures = JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray();
        long blocks = 0;
        for (var value : fixtures) {
            var fixture = value.getAsJsonObject();
            int sections = fixture.get("sections").getAsInt(), biomes = fixture.get("biomeCount").getAsInt();
            var bytes = HexFormat.of().parseHex(fixture.get("hex").getAsString());
            var input = Unpooled.wrappedBuffer(bytes);
            var reference = Unpooled.wrappedBuffer(bytes);
            var output = Unpooled.buffer();
            try {
                var sourceCodec = new SnapshotChunkType(sections,ChunkTranslator.bits(mappings.stateCount()),biomes);
                var oldCodec = new ChunkType26_1(sections,ChunkTranslator.bits(mappings.oldStateCount()),ChunkTranslator.bits(biomes));
                var original = sourceCodec.read(reference);
                translator.rewrite(input, output, sections, biomes);
                var translated = oldCodec.read(output);
                BridgeTest.check(original.getX()==translated.getX() && original.getZ()==translated.getZ(), "Chunk coordinates changed");
                BridgeTest.check(original.blockEntities().equals(translated.blockEntities()), "Block entity payload changed");
                for (int section=0; section<sections; section++) {
                    var before=original.getSections()[section]; var after=translated.getSections()[section];
                    BridgeTest.check(before.getNonAirBlocksCount()==after.getNonAirBlocksCount() && before.getFluidCount()==after.getFluidCount(), "Section counts changed");
                    for (int block=0; block<4096; block++) {
                        BridgeTest.check(mappings.state(before.palette(PaletteType.BLOCKS).idAt(block))==after.palette(PaletteType.BLOCKS).idAt(block), "Terrain state mismatch");
                        blocks++;
                    }
                    for (int biome=0; biome<64; biome++) BridgeTest.check(before.palette(PaletteType.BIOMES).idAt(biome)==after.palette(PaletteType.BIOMES).idAt(biome), "Biome ID changed");
                }
                var expectedLight = new byte[reference.readableBytes()]; reference.readBytes(expectedLight);
                var actualLight = new byte[output.readableBytes()]; output.readBytes(actualLight);
                BridgeTest.check(Arrays.equals(expectedLight,actualLight), "Lighting bytes changed");
                BridgeTest.check(!input.isReadable(), "Chunk bytes left unread");
            } finally { input.release(); reference.release(); output.release(); }
        }
        String result="{\"realDimensionChunks\":"+fixtures.size()+",\"blockPositionsChecked\":"+blocks+",\"passed\":true,\"dynamicRegistryTranslationValidated\":false}";
        Files.writeString(Path.of(args[1]),result+"\n"); System.out.println(result);
    }
}
