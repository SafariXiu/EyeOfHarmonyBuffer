package com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork;

import java.math.BigInteger;
import java.util.UUID;

public interface IWirelessComputeConsumer {

    UUID getOwnerUUID();

    WirelessNodeRef getWirelessNodeRef();

    BigInteger getRequiredCompute();
}
