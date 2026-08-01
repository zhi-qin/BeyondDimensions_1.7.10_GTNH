package com.wintercogs.beyonddimensions.common.item;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;

public class MatterCompressionBall extends Item {

    public MatterCompressionBall() {
        super();
    }

    public static boolean hasIStackList(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.hasKey("stack_list", 9)
            && tag.getTagList("stack_list", 10)
                .tagCount() > 0;
    }

    public static List<KeyAmount> getIStackList(ItemStack stack) {
        List<KeyAmount> result = new ArrayList<>();
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey("stack_list", 9)) {
            return result;
        }

        NBTTagList listTag = tag.getTagList("stack_list", 10);
        for (int i = 0; i < listTag.tagCount(); i++) {
            NBTTagCompound elementTag = listTag.getCompoundTagAt(i);
            KeyAmount stackType = KeyAmount.deserializeNBT(elementTag);
            result.add(stackType);
        }
        return result;
    }

    public static void setIStackList(ItemStack stack, List<KeyAmount> stackList) {
        NBTTagList listTag = new NBTTagList();
        for (KeyAmount stackType : stackList) {
            NBTTagCompound elementTag = KeyAmount.serializeNBT(stackType);
            listTag.appendTag(elementTag);
        }

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setTag("stack_list", listTag);
    }
}
