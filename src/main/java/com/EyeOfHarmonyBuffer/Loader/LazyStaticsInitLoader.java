package com.EyeOfHarmonyBuffer.Loader;

import com.EyeOfHarmonyBuffer.common.Machine.EOHB_SolarEnergyArray;
import com.EyeOfHarmonyBuffer.common.Machine.MiscHelper;

public class LazyStaticsInitLoader {

    public void initStaticsOnCompleteInit(){
        MiscHelper.initStatics();
        EOHB_SolarEnergyArray.initStatics();
    }
}
