package net.lumalyte.frontier.bridge;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.minecraft.Particle;
import com.viaversion.viaversion.api.minecraft.Holder;
import com.viaversion.viaversion.api.minecraft.SoundEvent;
import com.viaversion.viaversion.api.type.types.version.VersionedTypesHolder;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import io.netty.buffer.Unpooled;
import static com.viaversion.viaversion.api.protocol.packet.Direction.*;
import static net.lumalyte.frontier.bridge.RemainingControlPackets.*;

final class RemainingItemPackets {
 final CodecContext source,target;final VersionedTypesHolder client;final BridgeMappings mappings=new BridgeMappings();
 RemainingItemPackets(CodecContext source,CodecContext target,VersionedTypesHolder client){this.source=source;this.target=target;this.client=client;}
 void item(PacketWrapper w,Type<Item> from,Type<Item> to,Type<Item> wire){
  Item original=w.read(from);if(original==null){w.write(wire,null);return;}int amount=original.amount();boolean cost=from==source.types.itemCost||from==source.types.optionalItemCost;
  if(cost&&amount<=0)original.setAmount(1); // Cost predicates can legally have a zero/negative count; they are not empty stacks.
  Item value=w.user().get(BridgeConnectionState.class).items.toClientDisplay(original);if(cost)value.setAmount(amount);
  var buf=Unpooled.buffer();try{to.write(buf,value);w.write(wire,wire.read(buf));}finally{buf.release();}
 }
 void particle(PacketWrapper w){var state=w.user().get(BridgeConnectionState.class);var value=state.entities.particle(w.read(source.types.particle),state.items);var buf=Unpooled.buffer();try{target.types.particle.write(buf,value);w.write(client.particle(),client.particle().read(buf));}finally{buf.release();}}
 void register(ProtocolSnapshot8To26_3 p){
  handler(p,CLIENTBOUND,"MERCHANT_OFFERS",w->{w.passthrough(Types.VAR_INT);int n=count(w,65536);for(int i=0;i<n;i++){item(w,source.types.itemCost,target.types.itemCost,client.itemCost());item(w,source.types.item,target.types.item,client.item());item(w,source.types.optionalItemCost,target.types.optionalItemCost,client.optionalItemCost());w.passthrough(Types.BOOLEAN);for(int j=0;j<4;j++)w.passthrough(Types.INT);w.passthrough(Types.FLOAT);w.passthrough(Types.INT);}w.passthrough(Types.VAR_INT);w.passthrough(Types.VAR_INT);w.passthrough(Types.BOOLEAN);w.passthrough(Types.BOOLEAN);});
  handler(p,CLIENTBOUND,"UPDATE_ADVANCEMENTS",w->{w.passthrough(Types.BOOLEAN);int n=count(w,1048576);for(int i=0;i<n;i++){w.passthrough(Types.STRING);w.passthrough(Types.OPTIONAL_STRING);if(w.passthrough(Types.BOOLEAN)){w.passthrough(Types.TRUSTED_TAG);w.passthrough(Types.TRUSTED_TAG);item(w,source.types.itemTemplate,target.types.itemTemplate,client.itemTemplate());w.passthrough(Types.VAR_INT);int flags=w.passthrough(Types.INT);if((flags&1)!=0)w.passthrough(Types.STRING);}int requirements=count(w,1048576);for(int j=0;j<requirements;j++)w.passthrough(Types.STRING_ARRAY);w.passthrough(Types.BOOLEAN);w.passthrough(Types.FLOAT);w.passthrough(Types.FLOAT);}w.passthrough(Types.STRING_ARRAY);n=count(w,1048576);for(int i=0;i<n;i++){w.passthrough(Types.STRING);int criteria=count(w,1048576);for(int j=0;j<criteria;j++){w.passthrough(Types.STRING);if(w.passthrough(Types.BOOLEAN))w.passthrough(Types.LONG);}}w.passthrough(Types.BOOLEAN);});
  handler(p,CLIENTBOUND,"EXPLODE",w->{for(int i=0;i<3;i++)w.passthrough(Types.DOUBLE);w.passthrough(Types.FLOAT);w.passthrough(Types.INT);w.write(Types.BOOLEAN,false); // Knockback is now a separate additive-motion packet.
   if(w.read(Types.BOOLEAN))particle(w);else {
    // Old explosion packets require a particle. A block particle using air has no visible sprite.
    var rows=ItemMappingData.resource("codec-registries.json").getAsJsonObject("old").getAsJsonArray("particles");int id=-1;for(var row:rows)if(row.getAsJsonObject().get("key").getAsString().equals("minecraft:block"))id=row.getAsJsonObject().get("id").getAsInt();if(id<0)throw new IllegalStateException("Missing block particle");var invisible=new Particle(id);invisible.add(Types.VAR_INT,0);w.write(client.particle(),invisible);
   }
   boolean sound=w.read(Types.BOOLEAN);if(sound){var value=w.read(Types.SOUND_EVENT);w.write(Types.SOUND_EVENT,value.hasId()?Holder.of(mappings.sound(value.id())):Holder.of(value.value().withIdentifier(mappings.soundName(value.value().identifier()))));}else w.write(Types.SOUND_EVENT,Holder.of(new SoundEvent("minecraft:intentionally_empty",null)));
   int n=count(w,65536);for(int i=0;i<n;i++){particle(w);w.passthrough(Types.FLOAT);w.passthrough(Types.FLOAT);w.passthrough(Types.VAR_INT);}w.write(Types.BOOLEAN,sound);
  });
 }
}
