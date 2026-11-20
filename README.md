# FrontierBridge

FrontierBridge translates Minecraft 26.4 Snapshot 8 packets for older Java clients through ViaVersion. This repository contains the bridge source and protocol mappings. The initial import uses the available source checkout and reconstructs the earlier advancement-only command translation as the review baseline. The checkout has additional development work beyond the deployed 0.24.8 JAR.

## Build

Use JDK 25 and Python 3. Supply the Frontier-compatible ViaVersion JAR and the library directory from the matching Paper Snapshot 8 runtime. The build uses those existing dependencies.

    python build.py /path/to/ViaVersion-frontier-chat-codec.jar /path/to/server/libraries

The output is `build/FrontierBridge.jar`. Server installation also requires ViaBackwards. The plugin checks the protocol, authentication, whitelist and deployment settings at startup.
