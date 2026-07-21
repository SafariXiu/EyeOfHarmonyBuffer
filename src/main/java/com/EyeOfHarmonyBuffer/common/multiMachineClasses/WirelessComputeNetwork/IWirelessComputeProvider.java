package com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork;

import java.math.BigInteger;
import java.util.UUID;

public interface IWirelessComputeProvider {

    UUID getOwnerUUID();

    WirelessNodeRef getWirelessNodeRef();

    BigInteger getProvidedCompute();
}
