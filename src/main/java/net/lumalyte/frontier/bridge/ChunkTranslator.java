package net.lumalyte.frontier.bridge;

import com.viaversion.viaversion.api.minecraft.chunks.PaletteType;
import com.viaversion.viaversion.api.type.types.chunk.ChunkType26_1;
import io.netty.buffer.ByteBuf;

/** Terrain palette translation. Dynamic biomes/block-entity schemas require separate handling. */
public final class ChunkTranslator {
    private final BridgeMappings mappings;
    public ChunkTranslator(BridgeMappings mappings) { this.mappings = mappings; }

    public void rewrite(ByteBuf input, ByteBuf output, int sections, int biomeCount) {
        if (sections < 1 || sections > 256 || biomeCount < 1 || biomeCount > 65536)
            throw new IllegalArgumentException("Invalid dimension context");
        var source = new SnapshotChunkType(sections, bits(mappings.stateCount()), biomeCount);
        var target = new ChunkType26_1(sections, bits(mappings.oldStateCount()), bits(biomeCount));
        var chunk = source.read(input);
        for (var section : chunk.getSections()) section.palette(PaletteType.BLOCKS).replaceIds(mappings::state);
        target.write(output, chunk);
        // Light masks and arrays contain no registry IDs.
        output.writeBytes(input);
    }

    public static int bits(int size) { return size <= 1 ? 0 : 32 - Integer.numberOfLeadingZeros(size - 1); }
}
