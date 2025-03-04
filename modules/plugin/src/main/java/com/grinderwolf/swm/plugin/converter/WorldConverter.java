package com.grinderwolf.swm.plugin.converter;

import com.grinderwolf.swm.nms.CraftSlimeWorld;

public interface WorldConverter {

    void upgrade(CraftSlimeWorld world);

    void downgrade(CraftSlimeWorld world);

}
