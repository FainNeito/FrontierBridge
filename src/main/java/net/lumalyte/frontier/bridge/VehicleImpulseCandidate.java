package net.lumalyte.frontier.bridge;

import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v26_2to26_3.packet.ClientboundPackets26_3;

/** Experimental relative-motion writer; live use requires explicit loopback-lab opt-in. */
final class VehicleImpulseCandidate {
    static void write(PacketWrapper packet, int entity, double x, double y, double z, boolean onGround) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
            throw new IllegalArgumentException("Non-finite vehicle impulse");
        packet.setPacketType(ClientboundPackets26_3.TELEPORT_ENTITY);
        packet.write(Types.VAR_INT, entity);
        // Zero position/rotation offsets preserve the client's own predicted state.
        for (int i=0;i<3;i++) packet.write(Types.DOUBLE,0D);
        packet.write(Types.DOUBLE,x);packet.write(Types.DOUBLE,y);packet.write(Types.DOUBLE,z);
        packet.write(Types.FLOAT,0F);packet.write(Types.FLOAT,0F);
        // Relative XYZ, rotations and velocity XYZ; deliberately no ROTATE_DELTA bit.
        packet.write(Types.INT,255);
        packet.write(Types.BOOLEAN,onGround);
    }
}
