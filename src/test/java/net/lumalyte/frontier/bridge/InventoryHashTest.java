package net.lumalyte.frontier.bridge;
import com.google.gson.*;
import com.viaversion.viaversion.api.minecraft.data.StructuredData;
import com.viaversion.viaversion.api.minecraft.item.*;
import com.viaversion.viaversion.api.type.Types;
import io.netty.buffer.*;
import java.nio.file.*;
import java.util.*;
public final class InventoryHashTest {
    static void checkRoundtrip(InventoryHashTranslator hashes,ItemTranslationSession items,Item original) {
        var translated=items.toClient(original);hashes.track(original,translated);
        var predicted=hashes.hash(translated,true);var expected=hashes.hash(original,false);
        for(int amount:new int[]{1,2,32,64}) {predicted.setAmount(amount);expected.setAmount(amount);BridgeTest.check(hashes.restore(predicted).equals(expected),"Original component hashes lost");}
    }
    static ByteBuf click(HashedItem changed,HashedItem carried) {
        var b=Unpooled.buffer();Types.VAR_INT.writePrimitive(b,2);Types.VAR_INT.writePrimitive(b,71);b.writeShort(4).writeByte(0);Types.VAR_INT.writePrimitive(b,0);Types.VAR_INT.writePrimitive(b,1);b.writeShort(4);Types.HASHED_ITEM.write(b,changed);Types.HASHED_ITEM.write(b,carried);return b;
    }
    public static void main(String[] args)throws Exception {
        var fixture=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();var state=new BridgeConnectionState(new BridgeMappings());
        for(var value:fixture.getAsJsonArray("registries")) {
            var b=Unpooled.wrappedBuffer(HexFormat.of().parseHex(value.getAsJsonObject().get("hex").getAsString()));var out=Unpooled.buffer();
            try{state.registries.rewrite(b,out);}finally{b.release();out.release();}
        }
        var source=new CodecContext(true);var hashes=state.hashes();int itemCount=0,componentCount=0;Item sample=null;
        for(var entry:fixture.getAsJsonArray("items")) {
            var row=entry.getAsJsonObject();var original=ItemRoundTripTest.decode(source,HexFormat.of().parseHex(row.get("hex").getAsString()));if(sample==null)sample=original;
            checkRoundtrip(hashes,state.items,original);itemCount++;
        }
        var translatedComponents=new JsonArray();var target=new CodecContext(false);
        for(var entry:fixture.getAsJsonArray("components")) {
            var b=Unpooled.wrappedBuffer(HexFormat.of().parseHex(entry.getAsJsonObject().get("hex").getAsString()));
            try{
                var data=source.types.structuredData.read(b);var original=sample.copy();original.dataContainer().data().put(data.key(),data);checkRoundtrip(hashes,state.items,original);componentCount++;
                var translated=state.items.toClient(original);
                for(var translatedData:translated.dataContainer().data().values()) {
                var out=Unpooled.buffer();try {
                    target.types.structuredData.write(out,translatedData);byte[] bytes=new byte[out.readableBytes()];out.readBytes(bytes);
                    var row=new JsonObject();row.addProperty("hex",HexFormat.of().formatHex(bytes));row.addProperty("hash",hashes.hash(translated,true).dataHashesById().get(translatedData.id()));translatedComponents.add(row);
                }finally{out.release();}
                }
            }finally{b.release();}
        }
        var removed=sample.copy();var custom=source.types.structuredData.key(0);removed.dataContainer().data().put(custom,StructuredData.empty(custom,0));checkRoundtrip(hashes,state.items,removed);
        var nested=sample.copy();nested.dataContainer().setIdLookup(source,true);nested.dataContainer().set(source.types.structuredDataKeys().container,new Item[]{sample.copy(),removed.copy()});checkRoundtrip(hashes,state.items,nested);
        var bundled=sample.copy();bundled.dataContainer().setIdLookup(source,true);bundled.dataContainer().set(source.types.structuredDataKeys().bundleContents,new Item[]{nested});checkRoundtrip(hashes,state.items,bundled);
        var client=state.inventoryItem(sample);var prediction=hashes.hash(client,true);var expected=hashes.hash(sample,false);
        var input=click(prediction,prediction);var output=Unpooled.buffer();
        try {
            BridgeTest.check(hashes.rewriteClick(input,output),"Known click rejected");
            BridgeTest.check(Types.VAR_INT.readPrimitive(output)==2&&Types.VAR_INT.readPrimitive(output)==71&&output.readShort()==4&&output.readByte()==0&&Types.VAR_INT.readPrimitive(output)==0&&Types.VAR_INT.readPrimitive(output)==1&&output.readShort()==4,"Click header changed");
            BridgeTest.check(Types.HASHED_ITEM.read(output).equals(expected)&&Types.HASHED_ITEM.read(output).equals(expected)&&!output.isReadable(),"Click hashes not restored");
        }finally{input.release();output.release();}
        var altered=prediction.copy();altered.dataHashesById().put(0,altered.dataHashesById().get(0)^1);
        for(int invalidAmount:new int[]{-1,0,100}){var invalid=prediction.copy();invalid.setAmount(invalidAmount);ItemRoundTripTest.rejected(()->hashes.restore(invalid));}
        input=click(prediction,altered);output=Unpooled.buffer();
        try{BridgeTest.check(!hashes.rewriteClick(input,output)&&!output.isReadable(),"Partial or unknown click forwarded");}finally{input.release();output.release();}
        // Exercise multi-slot predictions used by quick move, number-key swaps,
        // drag distribution, dropping, and double-click collection. This checks
        // the wire transformation, not the client or server's click simulation.
        for(int mode:new int[]{0,1,2,4,5,6}) {
            input=Unpooled.buffer();output=Unpooled.buffer();
            try {
                Types.VAR_INT.writePrimitive(input,3);Types.VAR_INT.writePrimitive(input,19);
                input.writeShort(7).writeByte(0);Types.VAR_INT.writePrimitive(input,mode);
                Types.VAR_INT.writePrimitive(input,2);input.writeShort(7);Types.HASHED_ITEM.write(input,HashedStructuredItem.empty());
                input.writeShort(31);Types.HASHED_ITEM.write(input,prediction);Types.HASHED_ITEM.write(input,HashedStructuredItem.empty());
                BridgeTest.check(hashes.rewriteClick(input,output),"Click mode rejected: "+mode);
                BridgeTest.check(Types.VAR_INT.readPrimitive(output)==3&&Types.VAR_INT.readPrimitive(output)==19&&output.readShort()==7&&output.readByte()==0&&Types.VAR_INT.readPrimitive(output)==mode&&Types.VAR_INT.readPrimitive(output)==2,"Click mode header changed: "+mode);
                BridgeTest.check(output.readShort()==7&&Types.HASHED_ITEM.read(output).equals(HashedStructuredItem.empty())&&output.readShort()==31&&Types.HASHED_ITEM.read(output).equals(expected)&&Types.HASHED_ITEM.read(output).equals(HashedStructuredItem.empty())&&!output.isReadable(),"Click mode slots changed: "+mode);
            } finally {input.release();output.release();}
        }
        var isolated=new InventoryHashTranslator(state.registries.hashContext(false),state.registries.hashContext(true),1);
        ItemRoundTripTest.rejected(()->isolated.restore(prediction));isolated.track(sample,client);
        var other=sample.copy();other.setIdentifier(sample.identifier()+1);isolated.track(other,client);ItemRoundTripTest.rejected(()->isolated.restore(prediction));
        isolated.clear();isolated.track(sample,client);isolated.track(other,state.items.toClient(other));ItemRoundTripTest.rejected(()->isolated.restore(prediction));
        state.reset();ItemRoundTripTest.rejected(()->hashes.restore(prediction));
        var report=Map.of("itemHashRoundTrips",itemCount,"componentHashRoundTrips",componentCount,"amountsPerCase",4,"clickPacketRestored",true,"cacheMissDropsWholeClick",true,"collisionIsolationEvictionResetChecks",true,"passed",true);
        Files.writeString(Path.of(args[1]),new GsonBuilder().setPrettyPrinting().create().toJson(report));System.out.println(new Gson().toJson(report));
        Files.writeString(Path.of(args[2]),new Gson().toJson(translatedComponents));
    }
}
