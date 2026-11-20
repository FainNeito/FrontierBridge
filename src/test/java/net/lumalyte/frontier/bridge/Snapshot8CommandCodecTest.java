package net.lumalyte.frontier.bridge;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import io.netty.buffer.Unpooled;
public final class Snapshot8CommandCodecTest {
    public static void main(String[] args)throws Exception {
        var fixture=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        var bytes=HexFormat.of().parseHex(fixture.get("hex").getAsString());
        var input=Unpooled.wrappedBuffer(bytes);var output=Unpooled.buffer();
        try {
            int nodes=CommandTreeTranslator.rewrite(input,output);
            byte[] translated=new byte[output.readableBytes()];output.readBytes(translated);
            Files.writeString(Path.of(args[1]),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("nodes",nodes,"hex",HexFormat.of().formatHex(translated),"sourceBytes",bytes.length,"targetBytes",translated.length,"nativeValidationRequired",true)));
            System.out.println("Translated "+nodes+" native command nodes");
        }finally{input.release();output.release();}
    }
}
