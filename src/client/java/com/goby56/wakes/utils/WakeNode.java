package com.goby56.wakes.utils;

import com.goby56.wakes.WakesClient;
import com.goby56.wakes.config.WakesConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;

public class WakeNode implements Position<WakeNode>, Age<WakeNode> {
    private final WakeHandler wakeHandler = WakeHandler.getInstance();

    public final int res;

    private static float alpha;
    public float[][][] u;
    public float[][] initialValues;

    public final int x;
    public final int z;
    public float height;

    public WakeNode NORTH = null;
    public WakeNode EAST = null;
    public WakeNode SOUTH = null;
    public WakeNode WEST = null;

    // TODO MAKE DISAPPEARANCE DEPENDENT ON WAVE VALUES INSTEAD OF AGE/TIME (MAYBE)
    public final int maxAge = 30;
    public int age = 0;
    private boolean dead = false;

    public float t = 0;
    public int floodLevel;

    public WakeNode(Vec3d position, int initialStrength) {
        this.res = wakeHandler.resolution.res;
        this.initValues();
        this.x = (int) Math.floor(position.x);
        this.z = (int) Math.floor(position.z);
        this.height = (float) position.getY();
        int sx = (int) Math.floor(res * (position.x - this.x));
        int sz = (int) Math.floor(res * (position.z - this.z));
        for (int z = -1; z < 2; z++) {
            for (int x = -1; x < 2; x++) {
                this.u[0][sz+1+z][sx+1+x] = initialStrength;
            }
        }
        this.floodLevel = WakesClient.CONFIG_INSTANCE.floodFillDistance;
    }

    private WakeNode(int x, int z, float height, int floodLevel) {
        this.res = wakeHandler.resolution.res;
        this.initValues();
        this.x = x;
        this.z = z;
        this.height = height;
        this.floodLevel = floodLevel;
    }

    private WakeNode(long pos, float height) {
        this.res = wakeHandler.resolution.res;
        this.initValues();
        int[] xz = WakesUtils.longAsPos(pos);
        this.x = xz[0];
        this.z = xz[1];
        this.height = height;
        this.floodLevel = WakesClient.CONFIG_INSTANCE.floodFillDistance;
    }

    private void initValues() {
         this.u = new float[3][res+2][res+2];
         this.initialValues = new float[res+2][res+2];
    }

    public void setInitialValue(long pos, int val) {
        float resFactor = this.res / 16f;
        int[] xz = WakesUtils.longAsPos(pos);
        if (xz[0] < 0) xz[0] += res;
        if (xz[1] < 0) xz[1] += res;
        for (int i = -1; i < 2; i++) {
            for (int j = -1; j < 2; j++) {
                this.initialValues[xz[1]+i+1][xz[0]+j+1] = val * resFactor;
            }
        }
    }

    public static void calculateAlpha() {
        float time = 20f; // ticks
        WakeNode.alpha = (float) Math.pow(WakesClient.CONFIG_INSTANCE.waveSpeed * 16f / time, 2);
    }

    @Override
    public void tick() {
        if (this.isDead()) return;
        if (this.age++ >= this.maxAge || this.res != wakeHandler.resolution.res) {
            this.markDead();
            return;
        }
        this.t = this.age / (float) this.maxAge;

        for (int i = 2; i >= 1; i--) {
            if (this.NORTH != null) this.u[i][0] = this.NORTH.u[i][res];
            if (this.SOUTH != null) this.u[i][res+1] = this.SOUTH.u[i][1];
            for (int z = 0; z < res+2; z++) {
                if (this.EAST == null && this.WEST == null) break;
                if (this.EAST != null) this.u[i][z][res+1] = this.EAST.u[i][z][1];
                if (this.WEST != null) this.u[i][z][0] = this.WEST.u[i][z][res];
            }
        }

        for (int z = 1; z < res+1; z++) {
            for (int x = 1; x < res+1; x++) {
                this.u[0][z][x] += this.initialValues[z][x];
                this.initialValues[z][x] = 0;

                this.u[2][z][x] = this.u[1][z][x];
                this.u[1][z][x] = this.u[0][z][x];
            }
        }

        for (int z = 1; z < res+1; z++) {
            for (int x = 1; x < res+1; x++) {
                if (WakesClient.CONFIG_INSTANCE.use9PointStencil) {
                    this.u[0][z][x] = (float) (alpha * (0.5*u[1][z-1][x] + 0.25*u[1][z-1][x+1] + 0.5*u[1][z][x+1]
                            + 0.25*u[1][z+1][x+1] + 0.5*u[1][z+1][x] + 0.25*u[1][z+1][x-1]
                            + 0.5*u[1][z][x-1] + 0.25*u[1][z-1][x-1] - 3*u[1][z][x])
                            + 2*u[1][z][x] - u[2][z][x]);
                } else {
                    this.u[0][z][x] = alpha * (u[1][z-1][x] + u[1][z+1][x] + u[1][z][x-1] + u[1][z][x+1] - 4*u[1][z][x]) + 2*u[1][z][x] - u[2][z][x];
                }
                this.u[0][z][x] *= WakesClient.CONFIG_INSTANCE.waveDecay;
            }
        }

        if (this.floodLevel > 0 && this.age > WakesClient.CONFIG_INSTANCE.ticksBeforeFill) {
            if (this.NORTH == null) {
                wakeHandler.insert(new WakeNode(this.x, this.z - 1, this.height, this.floodLevel - 1));
            } else {
                this.NORTH.updateFloodLevel(this.floodLevel - 1);
            }
            if (this.EAST == null) {
                wakeHandler.insert(new WakeNode(this.x + 1, this.z, this.height, this.floodLevel - 1));
            } else {
                this.EAST.updateFloodLevel(this.floodLevel - 1);
            }
            if (this.SOUTH == null) {
                wakeHandler.insert(new WakeNode(this.x, this.z + 1, this.height, this.floodLevel - 1));
            } else {
                this.SOUTH.updateFloodLevel(this.floodLevel - 1);
            }
            if (this.WEST == null) {
                wakeHandler.insert(new WakeNode(this.x - 1, this.z, this.height, this.floodLevel - 1));
            } else {
                this.WEST.updateFloodLevel(this.floodLevel - 1);
            }
            this.floodLevel = 0;
            // TODO IF BLOCK IS BROKEN (AND WATER APPEARS IN ITS STEAD) RETRY FLOOD FILL
        }
    }

    @Override
    public void updateAdjacency(WakeNode node) {
        if (node.x == this.x && node.z == this.z - 1) {
            this.NORTH = node;
            node.SOUTH = this;
            return;
        }
        if (node.x == this.x + 1 && node.z == this.z) {
            this.EAST = node;
            node.WEST = this;
            return;
        }
        if (node.x == this.x && node.z == this.z + 1) {
            this.SOUTH = node;
            node.NORTH = this;
            return;
        }
        if (node.x == this.x - 1 && node.z == this.z) {
            this.WEST = node;
            node.EAST = this;
        }
    }

    public void updateFloodLevel(int newLevel) {
        this.age = 0;
        if (newLevel > this.floodLevel) {
            this.floodLevel = newLevel;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WakeNode wakeNode = (WakeNode) o;
        return x == wakeNode.x && z == wakeNode.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, z);
    }

    @Override
    public String toString() {
        return "WakeNode{" +
                "x=" + x +
                ", z=" + z +
                ", height=" + height +
                '}';
    }

    @Override
    public Box toBox() {
        return new Box(this.x, Math.floor(this.height), this.z, this.x + 1, Math.ceil(this.height), this.z + 1);
    }

    @Override
    public int x() {
        return this.x;
    }

    @Override
    public int z() {
        return this.z;
    }

    @Override
    public void revive(WakeNode node) {
        this.age = 0;
        this.floodLevel = WakesClient.CONFIG_INSTANCE.floodFillDistance;
        this.initialValues = node.initialValues;
    }

    @Override
    public void markDead() {
        this.dead = true;
    }

    @Override
    public boolean isDead() {
        return this.dead;
    }

    @Override
    public boolean inValidPos() {
        FluidState fluidState = MinecraftClient.getInstance().world.getFluidState(this.blockPos());
        FluidState fluidStateAbove = MinecraftClient.getInstance().world.getFluidState(this.blockPos().up());
        if (fluidState.isIn(FluidTags.WATER) && !fluidStateAbove.isIn(FluidTags.WATER)) {
            return fluidState.isStill() || WakesClient.CONFIG_INSTANCE.wakesInRunningWater;
        }
        return false;
    }

    public Vec3d getPos() {
        return new Vec3d(this.x, this.height, this.z);
    }

    public BlockPos blockPos() {
        return new BlockPos(this.x, (int) Math.floor(this.height), this.z);
    }

    public static class Factory {
        public static Set<WakeNode> rowingNodes(BoatEntity boat, float height) {
            Set<WakeNode> nodesAffected = new HashSet<>();
            double velocity = Math.max(WakesClient.CONFIG_INSTANCE.minimumProducerVelocity, boat.getVelocity().horizontalLength());
            for (int i = 0; i < 2; i++) {
                if (boat.isPaddleMoving(i)) {
                    double phase = boat.paddlePhases[i] % (2*Math.PI);
                    if (BoatEntity.NEXT_PADDLE_PHASE / 2 <= phase && phase <= BoatEntity.EMIT_SOUND_EVENT_PADDLE_ROTATION + BoatEntity.NEXT_PADDLE_PHASE) {
                        Vec3d rot = boat.getRotationVec(1.0f);
                        double x = boat.getX() + (i == 1 ? -rot.z : rot.z);
                        double z = boat.getZ() + (i == 1 ? rot.x : -rot.x);
                        Vec3d paddlePos = new Vec3d(x, height, z);
                        Vec3d dir = Vec3d.fromPolar(0, boat.getYaw()).multiply(velocity);
                        Vec3d from = paddlePos;
                        Vec3d to = paddlePos.add(dir.multiply(2));
                        nodesAffected.addAll(nodeTrail(from.x, from.z, to.x, to.z, height, WakesClient.CONFIG_INSTANCE.paddleStrength, velocity));
                    }
                }
            }
            return nodesAffected;
        }

        public static Set<WakeNode> nodeTrail(double fromX, double fromZ, double toX, double toZ, float height, float waveStrength, double velocity) {
            int res = WakeHandler.getInstance().resolution.res;
            int x1 = (int) (fromX * res);
            int z1 = (int) (fromZ * res);
            int x2 = (int) (toX * res);
            int z2 = (int) (toZ * res);

            ArrayList<Long> pixelsAffected = new ArrayList<>();
            WakesUtils.bresenhamLine(x1, z1, x2, z2, pixelsAffected);
            return pixelsToNodes(pixelsAffected, height, waveStrength, velocity);
        }

        public static Set<WakeNode> thickNodeTrail(double fromX, double fromZ, double toX, double toZ, float height, float waveStrength, double velocity, float width) {
            int res = WakeHandler.getInstance().resolution.res;
            int x1 = (int) (fromX * res);
            int z1 = (int) (fromZ * res);
            int x2 = (int) (toX * res);
            int z2 = (int) (toZ * res);
            int w = (int) (0.8 * width * res / 2);

            // TODO MAKE MORE EFFICIENT THICK LINE DRAWER
            float len = (float) Math.sqrt(Math.pow(z1 - z2, 2) + Math.pow(x2 - x1, 2));
            float nx = (z1 - z2) / len;
            float nz = (x2 - x1) / len;
            ArrayList<Long> pixelsAffected = new ArrayList<>();
            for (int i = -w; i < w; i++) {
                WakesUtils.bresenhamLine((int) (x1 + nx * i), (int) (z1 + nz * i), (int) (x2 + nx * i), (int) (z2 + nz * i), pixelsAffected);
            }
            return pixelsToNodes(pixelsAffected, height, waveStrength, velocity);
        }

        public static Set<WakeNode> nodeLine(double x, double z, float height, float waveStrength, Vec3d velocity, float width) {
            int res = WakeHandler.getInstance().resolution.res;
            Vec3d dir = velocity.normalize();
            double nx = -dir.z;
            double nz = dir.x;
            int w = (int) (0.8 * width * res / 2);

            int x1 = (int) (x * res - nx * w);
            int z1 = (int) (z * res - nz * w);
            int x2 = (int) (x * res + nx * w);
            int z2 = (int) (z * res + nz * w);

            ArrayList<Long> pixelsAffected = new ArrayList<>();
            WakesUtils.bresenhamLine(x1, z1, x2, z2, pixelsAffected);
            return pixelsToNodes(pixelsAffected, height, waveStrength, velocity.horizontalLength());
        }

        private static Set<WakeNode> pixelsToNodes(ArrayList<Long> pixelsAffected, float height, float waveStrength, double velocity) {
            WakesConfig.Resolution res = WakeHandler.getInstance().resolution;
            HashMap<Long, HashSet<Long>> pixelsInNodes = new HashMap<>();
            for (Long pixel : pixelsAffected) {
                int[] pos = WakesUtils.longAsPos(pixel);
                long k = WakesUtils.posAsLong(pos[0] >> res.power, pos[1] >> res.power);
                pos[0] %= res.res;
                pos[1] %= res.res;
                long v = WakesUtils.posAsLong(pos[0], pos[1]);
                if (pixelsInNodes.containsKey(k)) {
                    pixelsInNodes.get(k).add(v);
                } else {
                    HashSet<Long> set = new HashSet<>();
                    set.add(v);
                    pixelsInNodes.put(k, set);
                }
            }
            Set<WakeNode> nodesAffected = new HashSet<>();
            for (Long nodePos : pixelsInNodes.keySet()) {
                WakeNode node = new WakeNode(nodePos, height);
                for (Long subPos : pixelsInNodes.get(nodePos)) {
                    node.setInitialValue(subPos, (int) (waveStrength * velocity));
                }
                nodesAffected.add(node);
            }
            return nodesAffected;
        }
    }
}
