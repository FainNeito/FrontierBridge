package net.lumalyte.frontier.bridge;
import com.viaversion.viaversion.api.type.Types;
import static com.viaversion.viaversion.api.protocol.packet.Direction.*;
import static net.lumalyte.frontier.bridge.RemainingControlPackets.*;

final class RemainingWorldPackets {
 static void register(ProtocolSnapshot8To26_3 p) {
  var source=ItemMappingData.resource("hash-registries.json").getAsJsonObject("snapshot");
  var old=ItemMappingData.resource("remaining-registries-26.3.json");
  for(String key:new String[]{"stat_type","custom_stat","debug_subscription","map_decoration_type","block_entity_type","number_format_type"})
   if(!source.get("minecraft:"+key).equals(old.get("minecraft:"+key)))throw new IllegalStateException("Unaudited registry "+key);
  var blocks=new BridgeMappings();var items=new ItemMappingData();
  handler(p,CLIENTBOUND,"AWARD_STATS",w->{int n=w.read(Types.VAR_INT);if(n<0||n>1048576)throw new IllegalArgumentException("Invalid statistics count");var values=new java.util.LinkedHashMap<Long,Integer>();for(int i=0;i<n;i++){int category=w.read(Types.VAR_INT),id=w.read(Types.VAR_INT);int mapped=switch(category){case 0->blocks.block(id);case 1,2,3,4,5->items.getItemMappings().getNewId(id);case 6,7->blocks.entity(id);case 8->id;default->throw new IllegalArgumentException("Unknown statistic category");};if(mapped<0)throw new IllegalArgumentException("Unknown statistic ID");int amount=w.read(Types.VAR_INT);long key=((long)category<<32)|(mapped&0xffffffffL);values.merge(key,amount,(a,b)->(int)Math.clamp((long)a+b,Integer.MIN_VALUE,Integer.MAX_VALUE));}w.write(Types.VAR_INT,values.size());values.forEach((key,amount)->{w.write(Types.VAR_INT,(int)(key>>>32));w.write(Types.VAR_INT,(int)(long)key);w.write(Types.VAR_INT,amount);});});
  handler(p,CLIENTBOUND,"BLOCK_ENTITY_DATA",w->{w.passthrough(Types.BLOCK_POSITION1_14);w.passthrough(Types.VAR_INT);w.passthrough(Types.TRUSTED_COMPOUND_TAG);});
  // Dynamic biome IDs and their order are preserved by RegistryTranslator.
  handler(p,CLIENTBOUND,"CHUNKS_BIOMES",w->{int n=count(w,65536);for(int i=0;i<n;i++){
   w.passthrough(Types.LONG);var input=io.netty.buffer.Unpooled.wrappedBuffer(w.read(Types.BYTE_ARRAY_PRIMITIVE));var output=io.netty.buffer.Unpooled.buffer();
   try{var state=w.user().get(BridgeConnectionState.class);int biomes=state.registries.biomeCount();var codec=new com.viaversion.viaversion.api.type.types.chunk.PaletteType1_21_5(com.viaversion.viaversion.api.minecraft.chunks.PaletteType.BIOMES,ChunkTranslator.bits(biomes));
    // The packet is a length-delimited sequence of section biome palettes.
    int sections=0;while(input.isReadable()){if(++sections>256)throw new IllegalArgumentException("Too many biome sections");codec.write(output,SnapshotChunkType.readBiomes(input,biomes));}
    byte[] bytes=new byte[output.readableBytes()];output.readBytes(bytes);w.write(Types.BYTE_ARRAY_PRIMITIVE,bytes);
   }finally{input.release();output.release();}
  }});
  handler(p,CLIENTBOUND,"ADD_TRANSIENT_BLOCK",w->{w.passthrough(Types.BLOCK_POSITION1_14);w.write(Types.VAR_INT,blocks.state(w.read(Types.VAR_INT)));});
  handler(p,CLIENTBOUND,"DAMAGE_EVENT",w->{for(int i=0;i<4;i++)w.passthrough(Types.VAR_INT);if(w.passthrough(Types.BOOLEAN))for(int i=0;i<3;i++)w.passthrough(Types.DOUBLE);});
  handler(p,CLIENTBOUND,"MAP_ITEM_DATA",w->{w.passthrough(Types.VAR_INT);w.passthrough(Types.BYTE);w.passthrough(Types.BOOLEAN);if(w.passthrough(Types.BOOLEAN)){int n=count(w,65536);for(int i=0;i<n;i++){w.passthrough(Types.VAR_INT);for(int j=0;j<3;j++)w.passthrough(Types.BYTE);w.passthrough(Types.OPTIONAL_TAG);}}if(w.passthrough(Types.UNSIGNED_BYTE)>0){for(int i=0;i<3;i++)w.passthrough(Types.UNSIGNED_BYTE);w.passthrough(Types.BYTE_ARRAY_PRIMITIVE);}});
  handler(p,CLIENTBOUND,"SET_TIME",w->{w.passthrough(Types.LONG);int n=count(w,65536);for(int i=0;i<n;i++){w.passthrough(Types.VAR_INT);w.passthrough(Types.VAR_LONG);w.passthrough(Types.FLOAT);w.passthrough(Types.FLOAT);}});
  handler(p,CLIENTBOUND,"TRACKED_WAYPOINT",w->{w.passthrough(Types.VAR_INT);if(w.passthrough(Types.BOOLEAN))w.passthrough(Types.UUID);else w.passthrough(Types.STRING);w.passthrough(Types.STRING);if(w.passthrough(Types.BOOLEAN))for(int i=0;i<3;i++)w.passthrough(Types.BYTE);int kind=w.passthrough(Types.VAR_INT);switch(kind){case 0->{}case 1->{for(int i=0;i<3;i++)w.passthrough(Types.VAR_INT);}case 2->{w.passthrough(Types.VAR_INT);w.passthrough(Types.VAR_INT);}case 3->w.passthrough(Types.FLOAT);default->throw new IllegalArgumentException("Unknown waypoint kind");}});
 }
}
