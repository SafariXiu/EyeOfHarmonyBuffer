package com.EyeOfHarmonyBuffer.common.Block.Arknights.botany;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBush;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

import java.util.Random;

public class BlockResourceCluster extends BlockBush {

    private final ResourceClusterDef def;
    private final Item dropItem;

    public BlockResourceCluster(String modid, ResourceClusterDef def, Item dropItem) {
        super(Material.plants);
        this.def = def;
        this.dropItem = dropItem;

        setBlockName(def.blockName);
        setBlockTextureName(modid + ":Arknights/" + def.blockName);
        setCreativeTab(CreativeTabs.tabDecorations);
        setHardness(0.2F);
        setStepSound(soundTypeGrass);
    }

    @Override
    protected boolean canPlaceBlockOn(Block ground) {
        return def.validGround.contains(ground);
    }

    @Override
    public Item getItemDropped(int meta, Random rand, int fortune) {
        return dropItem;
    }

    @Override
    public int quantityDropped(Random rand) {
        int amount = 1;
        if (rand.nextFloat() < def.extraDropChance) amount++;
        return amount;
    }
}
