package fr.namularbre.taczcombat.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Une map de melee : un volume 3D defini par deux bornes posees a deux coins opposes,
 * et une liste de points d'apparition.
 */
public class ArenaMap {
    public static final int MAX_CORNERS = 2;

    public String name;
    /** Dimension, ex. "minecraft:overworld". Fixee par la premiere borne ou le premier spawn. */
    public String dimension;
    public List<Pos> corners = new ArrayList<>();
    public List<SpawnPoint> spawns = new ArrayList<>();

    public ArenaMap() {}

    public ArenaMap(String name) {
        this.name = name;
    }

    public boolean hasBounds() {
        return corners.size() == MAX_CORNERS;
    }

    public boolean isPlayable() {
        return hasBounds() && !spawns.isEmpty() && dimension != null;
    }

    /** Volume de la zone, bornes incluses. Null tant que les deux bornes ne sont pas posees. */
    public AABB bounds() {
        if (!hasBounds()) return null;
        BlockPos a = corners.get(0).toBlockPos();
        BlockPos b = corners.get(1).toBlockPos();
        return new AABB(
                Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1);
    }

    public boolean contains(Vec3 position) {
        AABB box = bounds();
        return box != null && box.contains(position);
    }

    /** Retire la borne ou le spawn a cette position. Renvoie true si quelque chose a ete retire. */
    public boolean removeAt(BlockPos pos) {
        boolean removed = corners.removeIf(c -> c.is(pos));
        removed |= spawns.removeIf(s -> s.pos.is(pos));
        return removed;
    }
}
