package net.lumalyte.frontier.bridge;
import com.google.gson.*;
import com.viaversion.viaversion.codec.hash.*;
import io.netty.buffer.Unpooled;
import java.nio.file.*;
import java.util.*;
public final class ComponentHashTest {
    public static void main(String[] args)throws Exception {
        var codecs=new CodecContext(true);
        var registries=JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonObject();
        var context=new RegistryHashContext(registries,codecs,false);
        var hasher=new HashOps(context,HashFunction.crc32c());int passed=0;
        var failures=new ArrayList<String>();
        for(var entry:JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject().getAsJsonArray("components")) {
            var row=entry.getAsJsonObject();var b=Unpooled.wrappedBuffer(HexFormat.of().parseHex(row.get("hex").getAsString()));
            try {
                var data=codecs.types.structuredData.read(b);hasher.reset();codecs.types.structuredData.write(hasher,data);
                if(hasher.hash()!=row.get("hash").getAsInt())throw new IllegalStateException("hash mismatch: expected="+row.get("hash")+" actual="+hasher.hash());passed++;
            }catch(Exception e){failures.add(row.get("key").getAsString()+": "+e);}finally{b.release();}
        }
        var report=Map.of("nativeComponentHashesMatched",passed,"failures",failures,"passed",failures.isEmpty());
        Files.writeString(Path.of(args[2]),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        System.out.println("Native component hashes matched="+passed+", failures="+failures.size());
        if(!failures.isEmpty())throw new AssertionError("Native component hash mismatch; see report");
    }
}
