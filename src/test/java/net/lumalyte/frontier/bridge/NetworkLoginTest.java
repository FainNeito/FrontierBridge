package net.lumalyte.frontier.bridge;
import com.viaversion.viaversion.api.protocol.packet.*;
import com.viaversion.viaversion.api.type.Types;
import io.netty.buffer.*;
import java.nio.file.*;
import java.util.*;
public final class NetworkLoginTest {
 static int cases;
 static ProtocolSnapshot8To26_3 protocol;
 static void check(Direction direction,State state,int id,ByteBuf input,byte[] expected)throws Exception{
  try{byte[] bytes=new byte[input.readableBytes()];input.readBytes(bytes);var result=ConnectionPipelineTest.rewrite(protocol,null,direction,state,id,bytes);BridgeTest.check(Arrays.equals(result,expected==null?bytes:expected),"Login wire mismatch "+direction+"/"+state+"/"+id);cases++;}finally{input.release();}
 }
 static ByteBuf string(String value){var b=Unpooled.buffer();Types.STRING.write(b,value);return b;}
 public static void main(String[] args)throws Exception{
  protocol=new ProtocolSnapshot8To26_3(new BridgeMappings());protocol.initialize();
  var b=Unpooled.buffer();Types.VAR_INT.write(b,BridgeMappings.SOURCE_PROTOCOL);Types.STRING.write(b,"localhost");b.writeShort(25567);Types.VAR_INT.write(b,2);check(Direction.SERVERBOUND,State.HANDSHAKE,0,b,null);
  check(Direction.SERVERBOUND,State.STATUS,0,Unpooled.buffer(),null);check(Direction.SERVERBOUND,State.STATUS,1,Unpooled.buffer().writeLong(42),null);
  check(Direction.CLIENTBOUND,State.STATUS,0,string("{}"),null);check(Direction.CLIENTBOUND,State.STATUS,1,Unpooled.buffer().writeLong(42),null);
  check(Direction.CLIENTBOUND,State.LOGIN,0,string("{\"text\":\"Rejected\"}"),null);
  b=string("");Types.BYTE_ARRAY_PRIMITIVE.write(b,new byte[162]);Types.BYTE_ARRAY_PRIMITIVE.write(b,new byte[4]);b.writeBoolean(true);check(Direction.CLIENTBOUND,State.LOGIN,1,b,null);
  for(int flags:new int[]{0,2}){b=Unpooled.buffer();Types.UUID.write(b,new UUID(1,2));Types.STRING.write(b,"Test");Types.VAR_INT.write(b,0);Types.UUID.write(b,new UUID(3,4));byte[] expected=new byte[b.readableBytes()];b.getBytes(0,expected);b.writeByte(flags);check(Direction.CLIENTBOUND,State.LOGIN,2,b,expected);}
  b=Unpooled.buffer();Types.VAR_INT.write(b,256);check(Direction.CLIENTBOUND,State.LOGIN,3,b,null);
  b=Unpooled.buffer();Types.VAR_INT.write(b,3);Types.STRING.write(b,"test:query");b.writeBytes(new byte[]{1,2,3});check(Direction.CLIENTBOUND,State.LOGIN,4,b,null);
  check(Direction.CLIENTBOUND,State.LOGIN,5,string("test:cookie"),null);
  b=string("Test");Types.UUID.write(b,new UUID(1,2));check(Direction.SERVERBOUND,State.LOGIN,0,b,null);
  b=Unpooled.buffer();Types.BYTE_ARRAY_PRIMITIVE.write(b,new byte[128]);Types.BYTE_ARRAY_PRIMITIVE.write(b,new byte[128]);check(Direction.SERVERBOUND,State.LOGIN,1,b,null);
  for(boolean present:new boolean[]{false,true}){b=Unpooled.buffer();Types.VAR_INT.write(b,3);b.writeBoolean(present);if(present)b.writeBytes(new byte[]{4,5});check(Direction.SERVERBOUND,State.LOGIN,2,b,null);}
  check(Direction.SERVERBOUND,State.LOGIN,3,Unpooled.buffer(),null);
  for(boolean present:new boolean[]{false,true}){b=string("test:cookie");Types.OPTIONAL_BYTE_ARRAY_PRIMITIVE.write(b,present?new byte[]{6,7}:null);check(Direction.SERVERBOUND,State.LOGIN,4,b,null);}
  var user=ConnectionPipelineTest.user();protocol.init(user);var info=user.getProtocolInfo();
  info.setClientState(State.CONFIGURATION);info.setServerState(State.CONFIGURATION);
  ConnectionPipelineTest.rewrite(protocol,user,Direction.CLIENTBOUND,State.CONFIGURATION,3,new byte[0]);
  BridgeTest.check(info.getServerState()==State.PLAY&&info.getClientState()==State.CONFIGURATION,"Server finish must not advance client before its acknowledgement");
  ConnectionPipelineTest.rewrite(protocol,user,Direction.SERVERBOUND,State.CONFIGURATION,3,new byte[0]);
  BridgeTest.check(info.getClientState()==State.PLAY,"Client finish did not advance state");
  ConnectionPipelineTest.rewrite(protocol,user,Direction.CLIENTBOUND,State.PLAY,SessionPackets.type(State.PLAY,Direction.CLIENTBOUND,"START_CONFIGURATION").getId(),new byte[0]);
  BridgeTest.check(info.getServerState()==State.CONFIGURATION&&info.getClientState()==State.PLAY,"Reconfiguration switched client early");
  ConnectionPipelineTest.rewrite(protocol,user,Direction.SERVERBOUND,State.PLAY,SessionPackets.type(State.PLAY,Direction.SERVERBOUND,"CONFIGURATION_ACKNOWLEDGED").getId(),new byte[0]);
  BridgeTest.check(info.getClientState()==State.CONFIGURATION,"Reconfiguration acknowledgement did not advance client");
  String result="{\"passed\":true,\"handshakeStatusLoginCases\":"+cases+",\"independentStateTransitions\":4}";Files.writeString(Path.of(args[0]),result);System.out.println(result);
 }
}
