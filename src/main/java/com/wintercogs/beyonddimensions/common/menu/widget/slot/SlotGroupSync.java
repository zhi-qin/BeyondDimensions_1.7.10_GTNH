package com.wintercogs.beyonddimensions.common.menu.widget.slot;

import java.util.List;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;

/**
 * 同步无序槽位时使用的同步器
 */
public interface SlotGroupSync {

    int getGroupId();

    void updateChange();

    void loadChange(List<IStackKey<?>> keys, List<Long> newCounts, List<Long> newModifiedTime,
        List<Long> newInsertedTime);

    void afterLoadChange();
}
