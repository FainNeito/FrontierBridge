package net.lumalyte.frontier.bridge;
import com.viaversion.viaversion.api.protocol.packet.State;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.api.minecraft.*;
import com.viaversion.viaversion.api.type.types.version.VersionedTypesHolder;
import com.viaversion.viaversion.protocols.v26_2to26_3.packet.ClientboundPackets26_3;

/** Snapshot-8 additive motion must not be mistaken for absolute SET_ENTITY_MOTION. */
final class ImpulsePackets {
 static void register(ProtocolSnapshot8To26_3 p,VersionedTypesHolder client) {
  int nativeId=-1,blockParticle=-1;
  for(var row:ItemMappingData.resource("snapshot-packets.json").getAsJsonObject("play").getAsJsonArray("CLIENTBOUND")){
   var r=row.getAsJsonObject();if(r.get("key").getAsString().equals("minecraft:add_entity_motion"))nativeId=r.get("id").getAsInt();
  }
  for(var row:ItemMappingData.resource("codec-registries.json").getAsJsonObject("old").getAsJsonArray("particles")){
   var r=row.getAsJsonObject();if(r.get("key").getAsString().equals("minecraft:block"))blockParticle=r.get("id").getAsInt();
  }
  if(nativeId<0||blockParticle<0)throw new IllegalStateException("Missing impulse registry entry");
  final int particleId=blockParticle;
  p.registerNativeClientbound(State.PLAY,nativeId,ClientboundPackets26_3.EXPLODE.getId(),w->{
   int entity=w.read(Types.VAR_INT);var impulse=w.read(Types.LOW_PRECISION_VECTOR);
   if(!Double.isFinite(impulse.x())||!Double.isFinite(impulse.y())||!Double.isFinite(impulse.z()))throw new IllegalArgumentException("Non-finite impulse");
   var state=w.user().get(BridgeConnectionState.class);
   if(!state.isPlayer(entity)) {
    if(!state.vehicleImpulseTesting)throw new IllegalArgumentException("Controlled-vehicle additive motion requires explicit lab testing opt-in; entity="+entity);
    VehicleImpulseCandidate.write(w,entity,impulse.x(),impulse.y(),impulse.z(),state.vehicles.requireGround(entity));return;
   }
   // Old clients add explosion knockback to their current velocity. No guessed velocity baseline.
   for(int i=0;i<3;i++)w.write(Types.DOUBLE,0D);
   w.write(Types.FLOAT,0F);w.write(Types.INT,0);w.write(Types.BOOLEAN,true);
   w.write(Types.DOUBLE,impulse.x());w.write(Types.DOUBLE,impulse.y());w.write(Types.DOUBLE,impulse.z());
   var invisible=new Particle(particleId);invisible.add(Types.VAR_INT,0);w.write(client.particle(),invisible);
   w.write(Types.SOUND_EVENT,Holder.of(new SoundEvent("minecraft:intentionally_empty",null)));
   w.write(Types.VAR_INT,0);w.write(Types.BOOLEAN,false);
  });
 }
}
