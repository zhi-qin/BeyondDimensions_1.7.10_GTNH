package com.wintercogs.beyonddimensions.common.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.block.entity.NetedBlockEntity;

public class NetedBlock extends Block implements net.minecraft.block.ITileEntityProvider {

    public NetedBlock() {
        super(Material.rock);
    }

    public NetedBlock(Material material) {
        super(material);
    }

    public static int getNetId(World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof NetedBlockEntity) {
            return ((NetedBlockEntity) te).getNetId();
        }
        return -1;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        if (placer instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) placer;
            if (!world.isRemote) {
                TileEntity te = world.getTileEntity(x, y, z);
                if (te instanceof NetedBlockEntity) {
                    NetedBlockEntity blockEntity = (NetedBlockEntity) te;
                    if (blockEntity.getNetId() == -1) {
                        DimensionsNet net = DimensionsNet.getNetFromPlayer(player);
                        if (net != null) {
                            if (net.isManager(player)) {
                                blockEntity.setNetId(net.getId());
                                player.addChatMessage(
                                    new ChatComponentTranslation("msg.beyonddimensions.block_net_bound", net.getId()));
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return null;
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (player.getHeldItem() != null || !player.isSneaking()) {
            return false;
        }
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof NetedBlockEntity) {
                NetedBlockEntity blockEntity = (NetedBlockEntity) te;
                if (blockEntity.getNetId() < 0) {
                    DimensionsNet net = DimensionsNet.getNetFromPlayer(player);
                    if (net != null) {
                        if (net.isManager(player)) {
                            blockEntity.setNetId(net.getId());
                            world.playSoundEffect(player.posX, player.posY, player.posZ, "random.click", 0.5F, 1.0F);
                            player.addChatMessage(
                                new ChatComponentTranslation("msg.beyonddimensions.block_net_bound", net.getId()));
                        } else {
                            player.addChatMessage(
                                new ChatComponentTranslation("msg.beyonddimensions.no_right_to_bound_block"));
                        }
                    }
                } else {
                    int currentNetId = blockEntity.getNetId();
                    DimensionsNet currentNet = DimensionsNet.getNetFromId(currentNetId);
                    if (currentNet == null) {
                        player.addChatMessage(
                            new ChatComponentTranslation("msg.beyonddimensions.block_net_unbound", currentNetId));
                        blockEntity.setNetId(-1);
                        world.playSoundEffect(player.posX, player.posY, player.posZ, "random.click", 0.5F, 1.0F);
                    } else if (currentNet.isManager(player)) {
                        player.addChatMessage(
                            new ChatComponentTranslation("msg.beyonddimensions.block_net_unbound", currentNetId));
                        blockEntity.setNetId(-1);
                        world.playSoundEffect(player.posX, player.posY, player.posZ, "random.click", 0.5F, 1.0F);
                    } else {
                        player.addChatMessage(
                            new ChatComponentTranslation("msg.beyonddimensions.no_right_to_bound_block"));
                    }
                }
            }
        }
        return true;
    }
}
