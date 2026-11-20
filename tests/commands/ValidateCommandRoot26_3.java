import java.nio.file.*;
import java.util.*;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import com.mojang.brigadier.builder.*;
import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.resources.Identifier;

/** Unlike codec-only checks, instantiate each argument as the real client does. */
public final class ValidateCommandRoot26_3 {
 public static void main(String[] args) throws Exception {
  SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
  var fixture=com.google.gson.JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
  var buffer=new FriendlyByteBuf(Unpooled.wrappedBuffer(HexFormat.of().parseHex(fixture.get("hex").getAsString())));
  try {
   var packet=ClientboundCommandsPacket.STREAM_CODEC.decode(buffer);
   var registries = new ArrayList<net.minecraft.core.Registry<?>>();
   BuiltInRegistries.REGISTRY.forEach(registries::add);
   for (var synced : net.minecraft.resources.RegistryDataLoader.SYNCHRONIZED_REGISTRIES) {
    registries.add(new net.minecraft.core.MappedRegistry<>(synced.key(), com.mojang.serialization.Lifecycle.stable()).freeze());
   }
   var context=CommandBuildContext.simple(new RegistryAccess.ImmutableRegistryAccess(registries),FeatureFlags.DEFAULT_FLAGS);
   var root=packet.getRoot(context,new ClientboundCommandsPacket.NodeBuilder<Object>() {
    public ArgumentBuilder<Object,?> createLiteral(String n){return LiteralArgumentBuilder.literal(n);}
    public ArgumentBuilder<Object,?> createArgument(String n,ArgumentType<?> type,Identifier suggestion){return RequiredArgumentBuilder.argument(n,type);}
    public ArgumentBuilder<Object,?> configure(ArgumentBuilder<Object,?> b,boolean executable,boolean restricted){return executable?b.executes(c->1):b;}
   });
   if(root.getChildren().isEmpty() || buffer.isReadable())throw new AssertionError("Invalid tree");
   System.out.println("COMMAND_ROOT_26_3_PASS roots="+root.getChildren().size()+"");
  }finally{buffer.release();}
 }
}
