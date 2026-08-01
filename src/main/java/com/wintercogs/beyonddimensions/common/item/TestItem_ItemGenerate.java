package com.wintercogs.beyonddimensions.common.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;

public class TestItem_ItemGenerate extends Item {

    public TestItem_ItemGenerate() {
        super();
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote) {
            DimensionsNet net = DimensionsNet.getNetFromPlayer(player);
            if (net != null) {
                UnifiedStorage storage = net.getUnifiedStorage();
                // 从注册表获取所有非空物品（1.7.10 中无 Items.AIR，仅过滤 null）
                List<Item> allItems = new ArrayList<>();
                for (Object obj : Item.itemRegistry) {
                    Item item = (Item) obj;
                    if (item != null) {
                        allItems.add(item);
                    }
                }

                // 创建随机数生成器
                Random random = new Random();

                // 打乱物品列表保证随机性
                Collections.shuffle(allItems, random);

                // 生成100种随机物品
                int count = Math.min(100, allItems.size());

                for (int i = 0; i < count; i++) {
                    Item item = allItems.get(i);
                    int amount = 100 + random.nextInt(201); // 生成100-300之间的随机数量

                    ItemStackKey stackKey = new ItemStackKey(new ItemStack(item, 1));

                    storage.insert(stackKey, amount, false);
                }
            }
        }

        return stack;
    }
}
