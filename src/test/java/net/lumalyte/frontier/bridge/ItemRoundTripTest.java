package net.lumalyte.frontier.bridge;
import com.google.gson.*;
import com.viaversion.viaversion.api.minecraft.data.*;
import com.viaversion.viaversion.api.minecraft.item.Item;
import io.netty.buffer.Unpooled;
import java.nio.file.*;
import java.util.*;

public final class ItemRoundTripTest {
    static final CodecContext source=new CodecContext(true),target=new CodecContext(false);
    static Item decode(CodecContext context,byte[] bytes) {
        var b=Unpooled.wrappedBuffer(bytes);try{var value=context.types.item.read(b);BridgeTest.check(!b.isReadable(),"Item trailing bytes");return value;}finally{b.release();}
    }
    static byte[] encode(CodecContext context,Item item) {
        var b=Unpooled.buffer();try{context.types.item.write(b,item);byte[] result=new byte[b.readableBytes()];b.readBytes(result);return result;}finally{b.release();}
    }
    static void rejected(Runnable action) {try{action.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("Unsafe item accepted");}
    static void roundtrip(ItemTranslationSession session,Item original) {
        var client=session.toClient(original);var decoded=decode(target,encode(target,client));var restored=session.toServer(decoded);
        BridgeTest.check(original.identifier()==restored.identifier()&&original.amount()==restored.amount(),"Identity lost");
        BridgeTest.check(Arrays.equals(encode(source,original),encode(source,restored)),"Component values lost");
        decode(source,encode(source,restored));
    }
    public static void main(String[] args)throws Exception {
        var fixture=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        var session=new ItemTranslationSession();int items=0,components=0;var failures=new ArrayList<String>();Item sample=null;
        for(var entry:fixture.getAsJsonArray("items")) {
            var row=entry.getAsJsonObject();var item=decode(source,HexFormat.of().parseHex(row.get("hex").getAsString()));
            if(sample==null)sample=item;
            try{roundtrip(session,item);items++;}catch(Exception|AssertionError e){failures.add("item "+row.get("id")+": "+e);}
        }
        for(var entry:fixture.getAsJsonArray("components")) {
            var row=entry.getAsJsonObject();var b=Unpooled.wrappedBuffer(HexFormat.of().parseHex(row.get("hex").getAsString()));
            try {
                var item=sample.copy();var data=source.types.structuredData.read(b);item.dataContainer().data().put(data.key(),data);
                roundtrip(session,item);components++;
            }catch(Exception|AssertionError e){failures.add("component "+row.get("key")+": "+e);}finally{b.release();}
        }
        var first=session.toClient(sample);var second=session.toClient(sample);
        var nested=sample.copy();nested.dataContainer().setIdLookup(source,true);
        nested.dataContainer().set(source.types.structuredDataKeys().container,new Item[]{sample.copy(),sample.copy()});
        roundtrip(session,nested);
        var deep=sample.copy();deep.dataContainer().setIdLookup(source,true);
        deep.dataContainer().set(source.types.structuredDataKeys().bundleContents,new Item[]{nested});roundtrip(session,deep);
        BridgeTest.check(Arrays.equals(encode(target,first),encode(target,second)),"Same stack identity changed on resend");
        second.setAmount(64);BridgeTest.check(session.toServer(second).amount()==64,"Stack merging rejected");
        second.setAmount(100);rejected(()->session.toServer(second));
        var modified=first.copy();modified.setIdentifier(first.identifier()+1);rejected(()->session.toServer(modified));
        rejected(()->new ItemTranslationSession().toServer(first));
        session.clear();rejected(()->session.toServer(first));
        var invalid=sample.copy();invalid.setIdentifier(999999);rejected(()->session.toServer(invalid));
        var bounded=new ItemTranslationSession(1);var evicted=bounded.toClient(sample);var other=sample.copy();other.setIdentifier(sample.identifier()+1);bounded.toClient(other);rejected(()->bounded.toServer(evicted));
        BridgeTest.check(bounded.cachedItems()==1,"Cache not bounded");
        var mappings=new ItemMappingData();int canonical=0;
        for(var entry:ItemMappingData.resource("codec-registries.json").getAsJsonObject("old").getAsJsonArray("items")) {
            var row=entry.getAsJsonObject();var item=sample.copy();item.dataContainer().data().clear();item.setIdentifier(row.get("id").getAsInt());
            if(item.identifier()==0)continue;
            BridgeTest.check(mappings.getFullItemMappings().identifier(session.toServer(item).identifier()).equals(row.get("key").getAsString()),"Canonical reverse ID lost");canonical++;
        }
        var report=Map.of("nativeItemsRestored",items,"nativeComponentValuesRestored",components,"canonicalOldItemIds",canonical,"provenanceChecks",true,"failures",failures,"passed",failures.isEmpty());
        Files.writeString(Path.of(args[1]),new GsonBuilder().setPrettyPrinting().create().toJson(report));System.out.println(new Gson().toJson(report));
        if(!failures.isEmpty())throw new AssertionError("Item roundtrip failures: "+failures.size());
    }
}
