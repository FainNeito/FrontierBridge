package net.lumalyte.frontier.bridge;
import com.google.gson.*;
import io.netty.buffer.Unpooled;
import java.nio.file.*;
import java.util.*;

public final class ComponentCodecTest {
    public static void main(String[] args) throws Exception {
        var context=new CodecContext(true);
        var fixtures=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        var failures=new ArrayList<String>(); int components=0,items=0;
        for(var entry:fixtures.getAsJsonArray("components")) {
            var row=entry.getAsJsonObject(); var bytes=HexFormat.of().parseHex(row.get("hex").getAsString());
            var in=Unpooled.wrappedBuffer(bytes); var out=Unpooled.buffer();
            try {
                var value=context.types.structuredData.read(in);
                if(in.isReadable()) throw new IllegalStateException("Unread bytes: "+in.readableBytes());
                context.types.structuredData.write(out,value);
                var actual=new byte[out.readableBytes()];out.readBytes(actual);
                if(!Arrays.equals(bytes,actual)) throw new IllegalStateException("Roundtrip bytes differ");
                components++;
            } catch(Exception e) { failures.add(row.get("key").getAsString()+": "+e); }
            finally { in.release();out.release(); }
        }
        for(var entry:fixtures.getAsJsonArray("items")) {
            var row=entry.getAsJsonObject();var bytes=HexFormat.of().parseHex(row.get("hex").getAsString());
            var in=Unpooled.wrappedBuffer(bytes);var out=Unpooled.buffer();
            try {
                var item=context.types.item.read(in);
                BridgeTest.check(item.identifier()==row.get("id").getAsInt()&&!in.isReadable(),"Item decode mismatch");
                context.types.item.write(out,item);
                var actual=new byte[out.readableBytes()];out.readBytes(actual);
                BridgeTest.check(Arrays.equals(bytes,actual),"Item encode mismatch");items++;
            } finally { in.release();out.release(); }
        }
        var report=Map.of("nativeDefaultItems",items,"nativeComponentValues",components,"failures",failures,"passed",failures.isEmpty());
        Files.writeString(Path.of(args[1]),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        System.out.println(new Gson().toJson(report));
        if(!failures.isEmpty()) throw new IllegalStateException("Native component codec failures");
    }
}
