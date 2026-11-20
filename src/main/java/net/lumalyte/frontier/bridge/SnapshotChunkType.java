package net.lumalyte.frontier.bridge;

import com.viaversion.viaversion.api.minecraft.chunks.*;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.api.type.types.chunk.*;
import io.netty.buffer.ByteBuf;

/** Snapshot 8 transmits block-resolution biomes; legacy clients use quart cells. */
final class SnapshotChunkType extends ChunkType1_21_5 {
 SnapshotChunkType(int sections,int blockBits,int biomeCount){super(new Section(blockBits,biomeCount),sections);}
 private static final class Section extends ChunkSectionType26_1 {
  private final int biomeCount;
  Section(int blockBits,int biomeCount){super(blockBits,ChunkTranslator.bits(biomeCount));this.biomeCount=biomeCount;}
  @Override public ChunkSection read(ByteBuf buffer){
   var section=new ChunkSectionImpl();section.setNonAirBlocksCount(buffer.readShort());section.setFluidCount(buffer.readShort());
   section.addPalette(PaletteType.BLOCKS,blockPaletteType.read(buffer));
   section.addPalette(PaletteType.BIOMES,readBiomes(buffer,biomeCount));return section;
  }
 }
 static DataPalette readBiomes(ByteBuf buffer,int biomeCount){
  int bits=buffer.readUnsignedByte();if(bits>16)throw new IllegalArgumentException("Invalid biome palette width");
  int[] palette=null;
  if(bits==0){int id=Types.VAR_INT.readPrimitive(buffer);checkBiome(id,biomeCount);var result=new DataPaletteImpl(64,1);result.addId(id);return result;}
  if(bits<=8){int count=Types.VAR_INT.readPrimitive(buffer);if(count<1||count>(1<<bits))throw new IllegalArgumentException("Invalid biome palette size");palette=new int[count];for(int i=0;i<count;i++){palette[i]=Types.VAR_INT.readPrimitive(buffer);checkBiome(palette[i],biomeCount);}}
  int perLong=64/bits;long mask=(1L<<bits)-1;int[] values=new int[4096];
  for(int start=0;start<4096;start+=perLong){long packed=buffer.readLong();for(int j=0;j<perLong&&start+j<4096;j++){int id=(int)((packed>>>(j*bits))&mask);if(palette!=null){if(id>=palette.length)throw new IllegalArgumentException("Invalid biome palette index");id=palette[id];}checkBiome(id,biomeCount);values[start+j]=id;}}
  var result=new DataPaletteImpl(64);
  for(int y=0;y<4;y++)for(int z=0;z<4;z++)for(int x=0;x<4;x++)result.setIdAt((y<<4)|(z<<2)|x,values[((y*4+2)<<8)|((z*4+2)<<4)|(x*4+2)]);
  return result;
 }
 private static void checkBiome(int id,int count){if(id<0||id>=count)throw new IllegalArgumentException("Invalid biome registry ID: "+id);}
}
