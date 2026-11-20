package net.lumalyte.frontier.bridge;
import com.google.gson.*;
import com.viaversion.viaversion.api.protocol.packet.*;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v26_2to26_3.packet.*;
import io.netty.buffer.*;
import java.nio.file.*;
import java.util.*;
public final class InventoryPacketTest {
    static byte[] bytes(ByteBuf b){var result=new byte[b.readableBytes()];b.readBytes(result);return result;}
    public static void main(String[] args)throws Exception {
        var fixture=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        var p=new ProtocolSnapshot8To26_3(new BridgeMappings());p.initialize();var u=ConnectionPipelineTest.user();p.init(u);
        int count=0;
        for(var entry:fixture.getAsJsonArray("items")) {
            byte[] nativeItem=HexFormat.of().parseHex(entry.getAsJsonObject().get("hex").getAsString());
            for(var packet:List.of(ClientboundPackets26_3.CONTAINER_SET_SLOT,ClientboundPackets26_3.SET_PLAYER_INVENTORY,ClientboundPackets26_3.SET_CURSOR_ITEM,ClientboundPackets26_3.SET_EQUIPMENT)) {
                var b=Unpooled.buffer();byte[] payload;
                try {
                    if(packet==ClientboundPackets26_3.CONTAINER_SET_SLOT){Types.VAR_INT.writePrimitive(b,2);Types.VAR_INT.writePrimitive(b,91);b.writeShort(7);}
                    else if(packet==ClientboundPackets26_3.SET_PLAYER_INVENTORY)Types.VAR_INT.writePrimitive(b,7);
                    else if(packet==ClientboundPackets26_3.SET_EQUIPMENT){Types.VAR_INT.writePrimitive(b,123);b.writeByte(0);}
                    b.writeBytes(nativeItem);payload=bytes(b);
                }finally{b.release();}
                var translated=Unpooled.wrappedBuffer(ConnectionPipelineTest.rewrite(p,u,Direction.CLIENTBOUND,State.PLAY,packet.getId(),payload));
                var creative=Unpooled.buffer();
                try {
                    if(packet==ClientboundPackets26_3.CONTAINER_SET_SLOT){BridgeTest.check(Types.VAR_INT.readPrimitive(translated)==2&&Types.VAR_INT.readPrimitive(translated)==91&&translated.readShort()==7,"Slot header changed");}
                    else if(packet==ClientboundPackets26_3.SET_PLAYER_INVENTORY)BridgeTest.check(Types.VAR_INT.readPrimitive(translated)==7,"Inventory slot changed");
                    else if(packet==ClientboundPackets26_3.SET_EQUIPMENT){BridgeTest.check(Types.VAR_INT.readPrimitive(translated)==123&&translated.readByte()==0,"Equipment header changed");}
                    creative.writeShort(7);ItemRoundTripTest.target.types.lengthPrefixedItem.write(creative,ItemRoundTripTest.target.types.item.read(translated));
                    var restored=Unpooled.wrappedBuffer(ConnectionPipelineTest.rewrite(p,u,Direction.SERVERBOUND,State.PLAY,ServerboundPackets26_3.SET_CREATIVE_MODE_SLOT.getId(),bytes(creative)));
                    try{BridgeTest.check(restored.readShort()==7&&Arrays.equals(ItemRoundTripTest.encode(ItemRoundTripTest.source,ItemRoundTripTest.source.types.lengthPrefixedItem.read(restored)),nativeItem)&&!restored.isReadable(),"Packet item restoration mismatch");}finally{restored.release();}
                }finally{translated.release();creative.release();}
                count++;
            }
        }
        var source=new CodecContext(true);var target=new CodecContext(false);
        var sample=ItemRoundTripTest.decode(source,HexFormat.of().parseHex(fixture.getAsJsonArray("items").get(0).getAsJsonObject().get("hex").getAsString()));
        var content=Unpooled.buffer();byte[] full;
        try {
            Types.VAR_INT.writePrimitive(content,5);Types.VAR_INT.writePrimitive(content,94);
            source.types.itemArray.write(content,new com.viaversion.viaversion.api.minecraft.item.Item[]{sample,com.viaversion.viaversion.api.minecraft.item.StructuredItem.empty(),sample.copy()});
            source.types.item.write(content,com.viaversion.viaversion.api.minecraft.item.StructuredItem.empty());full=bytes(content);
        }finally{content.release();}
        var result=Unpooled.wrappedBuffer(ConnectionPipelineTest.rewrite(p,u,Direction.CLIENTBOUND,State.PLAY,ClientboundPackets26_3.CONTAINER_SET_CONTENT.getId(),full));
        try {
            BridgeTest.check(Types.VAR_INT.readPrimitive(result)==5&&Types.VAR_INT.readPrimitive(result)==94,"Content header changed");
            var slots=target.types.itemArray.read(result);var carried=target.types.item.read(result);
            BridgeTest.check(slots.length==3&&com.viaversion.viaversion.api.minecraft.item.Item.isEmpty(slots[1])&&com.viaversion.viaversion.api.minecraft.item.Item.isEmpty(carried)&&!result.isReadable(),"Content/cursor decode failed");
            BridgeTest.check(u.get(BridgeConnectionState.class).items.toServer(slots[2]).identifier()==sample.identifier(),"Content original ID lost");
        }finally{result.release();}
        var report=Map.of("packetRoundTrips",count,"fullContentWithEmptySlotsAndCursor",true,"includesHashedClickCases",false,"passed",true);
        Files.writeString(Path.of(args[1]),new Gson().toJson(report));System.out.println(new Gson().toJson(report));
    }
}
