package net.lumalyte.frontier.bridge;
import com.google.gson.*;
import com.viaversion.viaversion.api.type.Types;
import io.netty.buffer.Unpooled;
import java.nio.file.*;
import java.util.*;
public final class RegistryTranslatorTest {
    public static void main(String[] args)throws Exception {
        var fixtures=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        var translator=new RegistryTranslator(new BridgeMappings());var translated=new JsonArray();int suppressed=0,entryCount=0;
        for(var value:fixtures.getAsJsonArray("registries")) {
            var row=value.getAsJsonObject();var bytes=HexFormat.of().parseHex(row.get("hex").getAsString());
            var input=Unpooled.wrappedBuffer(bytes);var output=Unpooled.buffer();var original=Unpooled.wrappedBuffer(bytes);
            try {
                String key=Types.STRING.read(original);var before=Types.REGISTRY_ENTRY_ARRAY.read(original);
                if(!translator.rewrite(input,output)) {BridgeTest.check(key.equals("minecraft:block_sound_set"),"Wrong registry suppressed");suppressed++;continue;}
                var result=output.copy();
                try {
                    BridgeTest.check(Types.STRING.read(result).equals(key),"Registry renamed");var after=Types.REGISTRY_ENTRY_ARRAY.read(result);
                    BridgeTest.check(before.length==after.length&&!result.isReadable(),"Registry IDs changed");
                    for(int i=0;i<before.length;i++) BridgeTest.check(before[i].key().equals(after[i].key()),"Registry order changed");
                } finally{result.release();}
                byte[] encoded=new byte[output.readableBytes()];output.readBytes(encoded);
                var record=row.deepCopy();record.addProperty("hex",HexFormat.of().formatHex(encoded));translated.add(record);entryCount+=before.length;
            }finally{input.release();output.release();original.release();}
        }
        BridgeTest.check(translator.biomeCount()==68,"Biome count wrong");
        boolean overworld=false,nether=false,end=false;
        for(int i=0;i<4;i++) {
            var d=translator.dimension(i);
            if(d.key().equals("minecraft:overworld")){BridgeTest.check(d.height()==384&&d.minY()==-64,"Overworld shape wrong");overworld=true;}
            if(d.key().equals("minecraft:the_nether")){BridgeTest.check(d.sections()==16,"Nether shape wrong");nether=true;}
            if(d.key().equals("minecraft:the_end")){BridgeTest.check(d.sections()==16,"End shape wrong");end=true;}
        }
        BridgeTest.check(overworld&&nether&&end,"Missing dimensions");translator.reset();
        boolean rejected=false;try{translator.biomeCount();}catch(IllegalStateException expected){rejected=true;}BridgeTest.check(rejected,"Configuration reset leaked biome context");
        fixtures.add("registries",translated);
        Files.writeString(Path.of(args[1]),new GsonBuilder().setPrettyPrinting().create().toJson(fixtures));
        var report=Map.of("registryPackets",translated.size(),"entries",entryCount,"suppressedNewRegistry",suppressed,"idsAndOrderingPreserved",true,"dimensionTrackingPassed",true);
        Files.writeString(Path.of(args[2]),new Gson().toJson(report));System.out.println(new Gson().toJson(report));
    }
}
