package net.lumalyte.frontier.bridge;
import java.util.*;
/** Connection-local state. Never reuse a removed entity's mount or ground information. */
final class VehicleMotionState {
 private final Set<Integer> spawned=new HashSet<>();
 private final Map<Integer,Boolean> ground=new HashMap<>();
 private Integer controlled;
 void spawn(int id){remove(id);spawned.add(id);}
 void remove(int id){spawned.remove(id);ground.remove(id);if(Objects.equals(controlled,id))controlled=null;}
 void passengers(int id,boolean playerFirst){
  if(playerFirst&&spawned.contains(id))controlled=id;
  else if(Objects.equals(controlled,id))controlled=null;
 }
 void ground(int id,boolean value){if(spawned.contains(id))ground.put(id,value);}
 void clientGround(boolean value){if(controlled!=null)ground(controlled,value);}
 boolean requireGround(int id){
  if(!Objects.equals(controlled,id)||!spawned.contains(id)||!ground.containsKey(id))
   throw new IllegalStateException("Vehicle impulse requires a spawned controlling mount and observed ground state: "+id);
  return ground.get(id);
 }
 void clear(){spawned.clear();ground.clear();controlled=null;}
}
