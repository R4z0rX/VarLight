package me.shawlaf.varlight.nms;

import net.minecraft.server.v1_14_R1.*;

public class WrappedIBlockAccess implements IBlockAccess {

    private final NmsAdapter nmsAdapter;
    private final WorldServer wrappedWorld;
    private final IBlockAccess wrappedBlockAccess;

    public WrappedIBlockAccess(NmsAdapter nmsAdapter, WorldServer wrappedWorld, IBlockAccess wrappedBlockAccess) {
        this.nmsAdapter = nmsAdapter;
        this.wrappedWorld = wrappedWorld;
        this.wrappedBlockAccess = wrappedBlockAccess;
    }

    @Override
    public int h(BlockPosition position) {
        return nmsAdapter.getCustomLuminance(wrappedWorld, position, () -> IBlockAccess.super.h(position));
    }

    @Override
    public TileEntity getTileEntity(BlockPosition blockPosition) {
        return wrappedBlockAccess.getTileEntity(blockPosition);
    }

    @Override
    public IBlockData getType(BlockPosition blockPosition) {
        return wrappedBlockAccess.getType(blockPosition);
    }

    @Override
    public Fluid getFluid(BlockPosition blockPosition) {
        return wrappedBlockAccess.getFluid(blockPosition);
    }
}
