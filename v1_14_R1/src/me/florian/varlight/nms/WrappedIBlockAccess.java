package me.florian.varlight.nms;

import net.minecraft.server.v1_14_R1.*;

import javax.annotation.Nullable;
import java.util.logging.Logger;

public class WrappedIBlockAccess implements IBlockAccess {

    private IBlockAccess wrapped;
    private WorldServer worldServer;

    public WrappedIBlockAccess(WorldServer worldServer, IBlockAccess wrapped) {
        this.wrapped = wrapped;
        this.worldServer = worldServer;
    }

    @Nullable
    @Override
    public TileEntity getTileEntity(BlockPosition blockPosition) {
        return wrapped.getTileEntity(blockPosition);
    }

    @Override
    public IBlockData getType(BlockPosition blockPosition) {
        return wrapped.getType(blockPosition);
    }

    @Override
    public Fluid getFluid(BlockPosition blockPosition) {
        return wrapped.getFluid(blockPosition);
    }

    @Override
    public int h(BlockPosition position) {
        int b = NmsAdapter_1_14_R1.getBrightness(worldServer, position);
        Logger.getLogger(getClass().getName()).info(String.valueOf(b));

        return b;
    }
}
