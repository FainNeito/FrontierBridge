package net.lumalyte.frontier.bridge;
import com.viaversion.viaversion.connection.UserConnectionImpl;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.packet.*;
import com.viaversion.viaversion.api.minecraft.item.*;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.exception.CancelException;
import com.viaversion.viaversion.protocol.packet.PacketWrapperImpl;
import com.viaversion.viaversion.protocols.v26_2to26_3.packet.*;
import io.netty.buffer.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** In-process lab diagnostic. Does not simulate a network login or a real player. */
final class ViaInventoryProbe {
    static void run(Consumer<UserConnection> resend) {
        var requests=new AtomicInteger();var user=new UserConnectionImpl(null);
        var protocol=new ProtocolSnapshot8To26_3(new BridgeMappings(),u->{requests.incrementAndGet();resend.accept(u);});protocol.initialize();protocol.init(user);
        var source=new CodecContext(true);var target=new CodecContext(false);
        var original=new StructuredItem(new ItemMappingData().getFullItemMappings().id("ice_ball"),3);
        var input=Unpooled.buffer();ByteBuf output=null;
        try {
            source.types.item.write(input,original);
            output=transform(protocol,user,Direction.CLIENTBOUND,ClientboundPackets26_3.SET_CURSOR_ITEM.getId(),input);
            var translated=target.types.item.read(output);if(output.isReadable())throw new IllegalStateException("Cursor trailing bytes");
            var hashes=user.get(BridgeConnectionState.class).hashes();var predicted=hashes.hash(translated,true);var expected=hashes.hash(original,false);
            output.release();output=null;input.clear();writeClick(input,predicted);
            output=transform(protocol,user,Direction.SERVERBOUND,ServerboundPackets26_3.CONTAINER_CLICK.getId(),input);
            if(Types.VAR_INT.readPrimitive(output)!=0||Types.VAR_INT.readPrimitive(output)!=7||output.readShort()!=4||output.readByte()!=0||Types.VAR_INT.readPrimitive(output)!=0||Types.VAR_INT.readPrimitive(output)!=0||!Types.HASHED_ITEM.read(output).equals(expected)||output.isReadable())throw new IllegalStateException("Hashed click restore failed");
            output.release();output=null;input.clear();predicted.dataHashesById().put(0,predicted.dataHashesById().get(0)^1);writeClick(input,predicted);
            boolean cancelled=false;
            try{output=transform(protocol,user,Direction.SERVERBOUND,ServerboundPackets26_3.CONTAINER_CLICK.getId(),input);}catch(CancelException expectedCancel){cancelled=true;}
            if(!cancelled||requests.get()!=1)throw new IllegalStateException("Unknown click was not cancelled with one resync request");
        }catch(Exception e){throw new IllegalStateException("Via inventory integration probe failed",e);}
        finally{input.release();if(output!=null)output.release();user.get(BridgeConnectionState.class).reset();user.remove(BridgeConnectionState.class);}
    }
    private static void writeClick(ByteBuf b,HashedItem item){Types.VAR_INT.writePrimitive(b,0);Types.VAR_INT.writePrimitive(b,7);b.writeShort(4).writeByte(0);Types.VAR_INT.writePrimitive(b,0);Types.VAR_INT.writePrimitive(b,0);Types.HASHED_ITEM.write(b,item);}
    private static ByteBuf transform(ProtocolSnapshot8To26_3 p,UserConnection u,Direction d,int id,ByteBuf input)throws Exception {
        int inputId=d==Direction.CLIENTBOUND?ProtocolSnapshot8To26_3.nativeClientboundId(State.PLAY,id):id;
        var wrapper=new PacketWrapperImpl(inputId,input,u);p.transform(d,State.PLAY,wrapper);var output=Unpooled.buffer();
        try{wrapper.writeToBuffer(output);if(Types.VAR_INT.readPrimitive(output)!=id)throw new IllegalStateException("Packet id changed");return output;}catch(Exception e){output.release();throw e;}
    }
}
