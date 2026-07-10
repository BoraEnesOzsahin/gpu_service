package com.ayrotek.inventory;

import com.ayrotek.dto.GpuInventory;
import java.util.List;

public interface GpuInventoryProvider {
    boolean isAvailable();
    List<GpuInventory> detectGpus();
}
