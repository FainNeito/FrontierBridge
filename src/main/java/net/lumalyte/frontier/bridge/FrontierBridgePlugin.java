package net.lumalyte.frontier.bridge;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/** Exact-snapshot bootstrap with explicit lab/Bloom test deployment selection. */
public final class FrontierBridgePlugin extends JavaPlugin {
    private boolean verified;
    @Override public void onLoad() {
        // This development bootstrap must never accidentally change another server's protocol registry.
        if (!DeploymentPolicy.permits(getServer().getIp(),getServer().getPort(),getServer().getUnsafe().getProtocolVersion(),
                getServer().getMinecraftVersion(),System.getProperty("frontierbridge.deployment",""),getServer().getOnlineMode(),getServer().hasWhitelist())) {
            getLogger().severe("FrontierBridge requires the exact snapshot-8 lab or explicit authenticated, whitelisted Bloom test deployment; actual="
                + getServer().getIp()+":"+getServer().getPort()+" version="+getServer().getMinecraftVersion()+" protocol="+getServer().getUnsafe().getProtocolVersion());
            return;
        }
        if (!ProtocolVersion.isRegistered(BridgeMappings.SOURCE_PROTOCOL)) {
            ProtocolVersion.register(BridgeMappings.SOURCE_PROTOCOL, "26.4-snapshot-8");
        }
        verified = true;
        // Via's onEnable checks the graph before dependent plugins are enabled.
        // Register in onLoad when initialized, or at Via's initialization callback
        // for late-bind servers. Never suppress its unsupported-server diagnostic.
        if (Via.getManager().isInitialized()) registerBridge();
        else Via.getManager().addEnableListener(this::registerBridge);
    }
    @Override public void onEnable() {
        if (!verified) { getServer().getPluginManager().disablePlugin(this); return; }
    }
    private void registerBridge() {
        // Validate the bundled tables before reporting the bootstrap ready.
        var mappings = new BridgeMappings();
        new CodecContext(true);
        new CodecContext(false);
        getLogger().info("BRIDGE_CODECS_PASS independent_snapshot_and_26_3_types");
        getLogger().info("BRIDGE_BOOTSTRAP_PASS protocol=" + BridgeMappings.SOURCE_PROTOCOL + " states=" + mappings.stateCount());
        if(Boolean.getBoolean("frontierbridge.network")) {
            Via.getManager().getProtocolManager().registerProtocol(new ProtocolSnapshot8To26_3(mappings,this::resendInventory,com.viaversion.viaversion.api.type.types.version.VersionedTypes.V26_3),ProtocolVersion.v26_3,ProtocolVersion.getProtocol(BridgeMappings.SOURCE_PROTOCOL));
            getLogger().info("BRIDGE_NETWORK_EDGE_ENABLED deployment="+System.getProperty("frontierbridge.deployment","loopback-lab"));
        } else getLogger().warning("Network testing opt-in is disabled; no snapshot translation edge is registered.");
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender)) return true;
        if(args.length>0&&args[0].equals("chatstatus")) {
            for(var player:getServer().getOnlinePlayers()) {
                var user=Via.getManager().getConnectionManager().getServerConnection(player.getUniqueId());
                var state=user==null?null:user.get(BridgeConnectionState.class);
                String nativeSession="unknown";
                try {
                    Object handle=player.getClass().getMethod("getHandle").invoke(player);
                    Object connection=handle.getClass().getField("connection").get(handle);
                    var field=connection.getClass().getDeclaredField("chatSession");field.setAccessible(true);
                    nativeSession=Boolean.toString(field.get(connection)!=null);
                } catch(ReflectiveOperationException failure) { nativeSession="unavailable:"+failure.getClass().getSimpleName(); }
                sender.sendMessage("BRIDGE_CHAT_STATUS player="+player.getName()+" nativeSession="+nativeSession+
                    (state==null?" translated=false":" translated=true sessions="+state.chatSessionsReceived+" signed="+state.signedChatsReceived+" unsigned="+state.unsignedChatsReceived));
            }
            return true;
        }
        if(args.length>0&&args[0].equals("sessions")) {
            var sessions=new java.util.ArrayList<java.util.Map<String,Object>>();
            for(var player:getServer().getOnlinePlayers()) {
                var user=Via.getManager().getConnectionManager().getServerConnection(player.getUniqueId());if(user==null)continue;
                var info=user.getProtocolInfo();var pipeline=user.getChannel().pipeline();
                var row=new java.util.LinkedHashMap<String,Object>();row.put("player",player.getName());row.put("client",info.protocolVersion().getName());row.put("protocol",info.protocolVersion().getVersion());
                row.put("onlineMode",getServer().getOnlineMode());row.put("whitelisted",player.isWhitelisted());row.put("whitelistEnabled",getServer().hasWhitelist());
                row.put("encryptedInbound",pipeline.get("decrypt")!=null);row.put("encryptedOutbound",pipeline.get("encrypt")!=null);
                row.put("clientState",info.getClientState().name());row.put("serverState",info.getServerState().name());row.put("world",player.getWorld().getKey().toString());
                row.put("time",java.time.Instant.now().toString());row.put("bridgeVersion",getPluginMeta().getVersion());
                row.put("inventory",java.util.stream.IntStream.range(0,player.getInventory().getSize()).filter(i->player.getInventory().getItem(i)!=null).mapToObj(i->{var item=player.getInventory().getItem(i);return java.util.Map.of("slot",i,"item",item.getType().name(),"amount",item.getAmount());}).toList());
                sessions.add(row);
                try{java.nio.file.Files.writeString(getDataFolder().toPath().resolve("authenticated-session-"+info.protocolVersion().getVersion()+".json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(row));}catch(java.io.IOException e){throw new IllegalStateException(e);}
            }
            sender.sendMessage("BRIDGE_SESSIONS "+new com.google.gson.Gson().toJson(sessions));return true;
        }
        if(args.length>0&&args[0].equals("chain8")) {
            try {boolean passed=Snapshot8ChainProbe.run(getDataFolder().toPath().resolve("chain-fixtures.json"),getDataFolder().toPath().resolve("chain8-probe.json"));sender.sendMessage(passed?"BRIDGE_CHAIN8_PASS":"BRIDGE_CHAIN8_FAIL see chain8-probe.json");}
            catch(Exception e){throw new IllegalStateException("Snapshot-8 chain diagnostic failed",e);}return true;
        }
        if(args.length>0&&args[0].equals("chain")) {
            try {boolean passed=ViaChainProbe.run(getDataFolder().toPath().resolve("chain-fixtures.json"),getDataFolder().toPath().resolve("chain-probe.json"));sender.sendMessage(passed?"BRIDGE_CHAIN_PASS":"BRIDGE_CHAIN_FAIL see chain-probe.json");}
            catch(Exception e){throw new IllegalStateException("Chain diagnostic failed",e);}return true;
        }
        var manager = Via.getManager().getProtocolManager();
        var server = manager.getServerProtocolVersion();
        boolean known = server.isKnown() && server.highestSupportedProtocolVersion().getVersion() == BridgeMappings.SOURCE_PROTOCOL;
        var target = ProtocolVersion.getProtocol(BridgeMappings.SOURCE_PROTOCOL);
        boolean missingEdge = manager.getProtocolPath(ProtocolVersion.v26_3, target) == null;
        if (!known || missingEdge==Boolean.getBoolean("frontierbridge.network")) throw new IllegalStateException("Unexpected lab protocol graph");
        var paths=new java.util.LinkedHashMap<String,Object>();
        for(var client:ProtocolVersion.getProtocols()) {
            if(!client.getName().startsWith("1.21")||client.olderThan(ProtocolVersion.v1_21_6))continue;
            var path=manager.getProtocolPath(client,ProtocolVersion.v26_3);
            if(path==null||path.isEmpty())throw new IllegalStateException("Missing existing ViaBackwards path: "+client);
            paths.put(client.getName(),path.stream().map(entry->entry.protocol().getClass().getSimpleName()).toList());
        }
        if(paths.size()!=4)throw new IllegalStateException("Unexpected supported 1.21 protocol family count: "+paths.size());
        var blocked=Via.getConfig().blockedProtocolVersions();
        for(var client:ProtocolVersion.getProtocols()) {
            if(!client.getName().startsWith("1.21"))continue;
            if(blocked.contains(client)!=client.olderThan(ProtocolVersion.v1_21_6))throw new IllegalStateException("Incorrect client version policy: "+client);
        }
        sender.sendMessage("BRIDGE_CLIENT_POLICY_PASS minimum=1.21.6");
        // Exercise the handler against Via's actual connection implementation, without a network player.
        var user=new com.viaversion.viaversion.connection.UserConnectionImpl(null);
        var kernel=new ProtocolSnapshot8To26_3(new BridgeMappings(),this::resendInventory);kernel.initialize();kernel.init(user);
        var buffer=io.netty.buffer.Unpooled.wrappedBuffer(new byte[]{0});var output=io.netty.buffer.Unpooled.buffer();
        try {
            int id=com.viaversion.viaversion.protocols.v26_2to26_3.packet.ClientboundPackets26_3.SET_CURSOR_ITEM.getId();
            int nativeId=ProtocolSnapshot8To26_3.nativeClientboundId(com.viaversion.viaversion.api.protocol.packet.State.PLAY,id);
            var wrapper=new com.viaversion.viaversion.protocol.packet.PacketWrapperImpl(nativeId,buffer,user);
            kernel.transform(com.viaversion.viaversion.api.protocol.packet.Direction.CLIENTBOUND,com.viaversion.viaversion.api.protocol.packet.State.PLAY,wrapper);
            wrapper.writeToBuffer(output);
            if(com.viaversion.viaversion.api.type.Types.VAR_INT.readPrimitive(output)!=id||output.readByte()!=0||output.isReadable())throw new IllegalStateException("Actual Via connection cursor translation failed");
            user.get(BridgeConnectionState.class).reset();user.remove(BridgeConnectionState.class);
        }catch(Exception e){throw new IllegalStateException("Via connection smoke failed",e);}
        finally{buffer.release();output.release();}
        ViaInventoryProbe.run(this::resendInventory);
        try {
            getDataFolder().mkdirs();
            java.nio.file.Files.writeString(getDataFolder().toPath().resolve("via-path-audit.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(java.util.Map.of("pathsTo26_3",paths,"snapshotEdgeRegistered",!missingEdge,"authenticatedClientJoinValidated",false)));
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
        sender.sendMessage("BRIDGE_VIA_PATHS_PASS families="+paths.size()+" destination=26.3 snapshot_edge_registered="+!missingEdge);
        sender.sendMessage("BRIDGE_VIA_CONNECTION_PASS actual_user_connection=true network_player=false");
        sender.sendMessage("BRIDGE_HASHED_CLICK_PASS restore=true miss_cancelled=true resync_callback=true network_player=false");
        sender.sendMessage("BRIDGE_STATUS_PASS exact_server_known=true network_opt_in="+Boolean.getBoolean("frontierbridge.network"));
        return true;
    }
    private void resendInventory(com.viaversion.viaversion.api.connection.UserConnection connection) {
        var uuid=connection.getProtocolInfo().getUuid();if(uuid==null)return;
        var state=connection.get(BridgeConnectionState.class);if(state==null||!state.resyncPending.compareAndSet(false,true))return;
        getServer().getScheduler().runTask(this,()->{
            try{var player=getServer().getPlayer(uuid);if(player!=null&&player.isOnline())player.updateInventory();}
            finally{state.resyncPending.set(false);}
        });
    }
}
