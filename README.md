# FrontierBridge

FrontierBridge translates Minecraft 26.4 Snapshot 8 packets for older Java clients through ViaVersion. This repository contains the bridge source and protocol mappings. The initial import uses the available source checkout and reconstructs the earlier advancement-only command translation as the review baseline. The checkout has additional development work beyond the deployed 0.24.8 JAR.

## Build

Use JDK 25 and Python 3. Supply the Frontier-compatible ViaVersion JAR and the library directory from the matching Paper Snapshot 8 runtime. The build uses those existing dependencies.

    python build.py /path/to/ViaVersion-frontier-chat-codec.jar /path/to/server/libraries

The output is `build/FrontierBridge.jar`. Server installation also requires ViaBackwards. The plugin checks the protocol, authentication, whitelist and deployment settings at startup.

## Dawn 26.3 command compatibility

Run the regression with the extracted vanilla 26.3 server JAR and its matching libraries. The test rebuilds the bridge, translates snapshot commands, then instantiates the command tree with the native 26.3 decoder and client-visible registries.

    python -B test_commands.py VIA_JAR SNAPSHOT_LIBRARIES VANILLA_26_3_SERVER_JAR VANILLA_26_3_LIBRARIES

An optional final argument accepts a captured command-tree JSON fixture with a hex-encoded packet. The bundled fixtures cover the template-pool login failure, four server-only registries with both resource argument forms, and the ordinary item registry.
