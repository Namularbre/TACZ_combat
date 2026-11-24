package fr.namularbre.taczcombat.arena;

import net.minecraft.core.BlockPos;

/** Position de bloc serialisable en JSON. */
public class Pos {
    public int x;
    public int y;
    public int z;

    public Pos() {}

    public Pos(BlockPos pos) {
        this.x = pos.getX();
        this.y = pos.getY();
        this.z = pos.getZ();
    }

    public BlockPos toBlockPos() {
        return new BlockPos(x, y, z);
    }

    public boolean is(BlockPos pos) {
        return pos.getX() == x && pos.getY() == y && pos.getZ() == z;
    }

    @Override
    public String toString() {
        return x + " " + y + " " + z;
    }
}
