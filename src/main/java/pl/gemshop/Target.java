package pl.gemshop;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.RaycastContext;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

/**
 * Kogo gracz ma bezposrednio na muszce - normalny raycast "co na wprost mnie",
 * taki sam jak przy zwyklym atakowaniu: blokowany przez bloki (bez przechodzenia
 * przez sciany) i ograniczony do rozsadnego zasiegu.
 */
public final class Target {
    private static final double RANGE = 24.0;

    private Target() {}

    public static AbstractClientPlayerEntity lookedAt(float pt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return null;
        Vec3d start = mc.player.getCameraPosVec(pt);
        Vec3d dir = mc.player.getRotationVec(pt);
        Vec3d end = start.add(dir.multiply(RANGE));

        // najpierw sprawdz czy na drodze jest blok - jesli tak, nie "widzimy" nic za nim
        HitResult blockHit = mc.world.raycast(new RaycastContext(start, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        double maxDist = blockHit != null && blockHit.getType() != HitResult.Type.MISS
                ? start.distanceTo(blockHit.getPos()) : RANGE;

        AbstractClientPlayerEntity best = null;
        double bd = Double.MAX_VALUE;
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || p.isSpectator()) continue;
            Optional<Vec3d> hit = p.getBoundingBox().expand(0.1).raycast(start, end);
            if (hit.isEmpty()) continue;
            double d = start.distanceTo(hit.get());
            if (d > maxDist) continue; // zasloniety przez sciane
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }
}
