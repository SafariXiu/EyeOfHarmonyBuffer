package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 2026-11-11: on-disk cache for the PRODUCTION ocean solution (handoff doc section 5-2).
 *
 * WHY: the user's rule A0-2 says the final deliverable is ONE converged steady state,
 * computed once and then cached. Today the only cache is the in-memory slot in
 * StaticOceanProduction, so every fresh JVM pays the full spin-up again (11 min at
 * SEASONAL_YEARS=1, ~80 min at 12). This class writes the solution to a file keyed by
 * (worldSeed, flagsHash, SOLVER_VERSION) and reads it back bit-identically.
 *
 * DISCIPLINE (rule A3-5 / A6 'cache key missed an input'): the file name carries the
 * seed, the full solver flags hash and SOLVER_VERSION; the header repeats them, so a
 * stale or foreign file is REJECTED rather than silently used.
 *
 * Pure Java (no Minecraft import): the caller decides the directory.
 */
public final class OceanSolutionIO {
    private OceanSolutionIO() {}

    /** Bump when the on-disk layout or the meaning of any field changes. */
    public static final int FORMAT_VERSION = 1;
    public static final String MAGIC = "EOHOCN";

    public static synchronized String fileName(int worldSeed, long flagsHash, int solverVersion) {
        return String.format(java.util.Locale.ROOT, "ocean_v%d_seed%d_%016x.ocn.gz",
                solverVersion, worldSeed, flagsHash);
    }

    // ------------------------------------------------------------------ write

    public static synchronized void write(Path file, OceanSolution sol, long flagsHash) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
        OutputStream os = new BufferedOutputStream(Files.newOutputStream(tmp), 1 << 16);
        DataOutputStream o = new DataOutputStream(new GZIPOutputStream(os, 1 << 16));
        try {
            o.writeUTF(MAGIC);
            o.writeInt(FORMAT_VERSION);
            o.writeLong(flagsHash);
            o.writeInt(StaticOceanSolver.SOLVER_VERSION);
            OceanCacheKey k = sol.key;
            o.writeInt(k.worldSeed); o.writeInt(k.x0); o.writeInt(k.z0);
            o.writeInt(k.landVersion); o.writeInt(k.solverVersion); o.writeInt(k.season);
            o.writeBoolean(k.sphere); o.writeInt(k.nx); o.writeInt(k.ny); o.writeInt(k.nz);
            o.writeBoolean(k.bathyMask);
            OceanGrid g = sol.state.grid;
            o.writeInt(g.nx); o.writeInt(g.ny); o.writeInt(g.nz);
            o.writeDouble(g.dx); o.writeDouble(g.dy);
            for (int i = 0; i < g.nz; i++) o.writeDouble(g.dz[i]);
            o.writeBoolean(g.sphere); o.writeInt(g.x0); o.writeInt(g.z0);
            writeBools(o, g.wet);
            for (int v : g.wetLayers) o.writeInt(v);
            OceanState st = sol.state;
            writeDoubles(o, st.t); writeDoubles(o, st.s);
            writeDoubles(o, st.u); writeDoubles(o, st.v); writeDoubles(o, st.w);
            writeDoubles(o, st.vBaroclinic); writeDoubles(o, st.uBaroclinic);
            writeDoubles(o, st.eta); writeDoubles(o, st.surfaceLayer);
            writeDoubles(o, st.mixedLayerDepth); writeDoubles(o, st.hTOld);
            writeDoubles(o, st.hTNew); writeDoubles(o, st.iceFraction);
            writeDoubles(o, st.iceThickness); writeDoubles(o, st.iceSaltFlux);
            writeDoubles(o, st.psi); writeDoubles(o, st.uF); writeDoubles(o, st.vF); writeDoubles(o, st.wF);
            writeDoubles(o, sol.surfaceT);
            o.writeInt(sol.baroIterations); o.writeInt(sol.advIterations);
            o.writeDouble(sol.baroResidual); o.writeDouble(sol.advResidual);
            o.writeDouble(sol.psiMin); o.writeDouble(sol.psiMax); o.writeDouble(sol.mocSv);
            o.writeDouble(sol.salinityBudgetMax); o.writeDouble(sol.salinityBudgetMean);
            double[][] seas = sol.surfaceTBySeason;
            o.writeInt(seas == null ? -1 : seas.length);
            if (seas != null) for (double[] arr : seas) writeDoubles(o, arr);
        } finally {
            o.close();
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    // ------------------------------------------------------------------- read

    /** Returns null (never throws) when the file is missing, foreign or stale. */
    public static synchronized OceanSolution read(Path file, int worldSeed, long flagsHash) {
        if (!Files.isRegularFile(file)) return null;
        try (InputStream is = new BufferedInputStream(Files.newInputStream(file), 1 << 16);
             DataInputStream i = new DataInputStream(new GZIPInputStream(is, 1 << 16))) {
            if (!MAGIC.equals(i.readUTF())) return null;
            if (i.readInt() != FORMAT_VERSION) return null;
            if (i.readLong() != flagsHash) return null;
            if (i.readInt() != StaticOceanSolver.SOLVER_VERSION) return null;
            int seed = i.readInt(), x0 = i.readInt(), z0 = i.readInt();
            int landV = i.readInt(), solverV = i.readInt(), season = i.readInt();
            boolean sphere = i.readBoolean();
            int knx = i.readInt(), kny = i.readInt(), knz = i.readInt();
            boolean bathyMask = i.readBoolean();
            if (seed != worldSeed) return null;
            int nx = i.readInt(), ny = i.readInt(), nz = i.readInt();
            double dx = i.readDouble(), dy = i.readDouble();
            double[] dz = new double[nz];
            for (int k = 0; k < nz; k++) dz[k] = i.readDouble();
            boolean gsphere = i.readBoolean();
            int gx0 = i.readInt(), gz0 = i.readInt();
            boolean[] wet = readBools(i);
            int[] wl = new int[nx * ny];
            for (int c = 0; c < wl.length; c++) wl[c] = i.readInt();
            OceanGrid g = OceanGrid.restore(nx, ny, nz, dx, dy, dz, wet, wl, gx0, gz0, gsphere);
            OceanState st = new OceanState(g);
            readDoubles(i, st.t); readDoubles(i, st.s);
            readDoubles(i, st.u); readDoubles(i, st.v); readDoubles(i, st.w);
            readDoubles(i, st.vBaroclinic); readDoubles(i, st.uBaroclinic);
            readDoubles(i, st.eta); readDoubles(i, st.surfaceLayer);
            readDoubles(i, st.mixedLayerDepth); readDoubles(i, st.hTOld);
            readDoubles(i, st.hTNew); readDoubles(i, st.iceFraction);
            readDoubles(i, st.iceThickness); readDoubles(i, st.iceSaltFlux);
            double[] psi = new double[st.psi.length]; readDoubles(i, psi); st.psi = psi;
            readDoubles(i, st.uF); readDoubles(i, st.vF); readDoubles(i, st.wF);
            double[] surf = new double[nx * ny]; readDoubles(i, surf);
            int bi = i.readInt(), ai = i.readInt();
            double br = i.readDouble(), ar = i.readDouble();
            double pmin = i.readDouble(), pmax = i.readDouble(), moc = i.readDouble();
            double sbMax = i.readDouble(), sbMean = i.readDouble();
            int ns = i.readInt();
            double[][] seas = null;
            if (ns >= 0) {
                seas = new double[ns][];
                for (int s2 = 0; s2 < ns; s2++) { seas[s2] = new double[nx * ny]; readDoubles(i, seas[s2]); }
            }
            OceanCacheKey key = new OceanCacheKey(seed, x0, z0, landV, solverV, season,
                    sphere, knx, kny, knz, bathyMask);
            VelocityProjection.FACES_READY = true;
            return new OceanSolution(key, st, surf, bi, ai, br, ar, pmin, pmax, moc,
                    sbMax, sbMean, seas);
        } catch (EOFException e) {
            return null;
        } catch (IOException e) {
            return null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- helpers

    private static void writeBools(DataOutputStream o, boolean[] a) throws IOException {
        o.writeInt(a.length);
        int n = 0;
        for (boolean b : a) if (b) n++;
        o.writeInt(n);
        for (int c = 0; c < a.length; c += 8) {
            int v = 0;
            for (int b = 0; b < 8 && c + b < a.length; b++) if (a[c + b]) v |= (1 << b);
            o.writeByte(v);
        }
    }

    private static boolean[] readBools(DataInputStream i) throws IOException {
        int len = i.readInt();
        boolean[] a = new boolean[len];
        i.readInt();
        for (int c = 0; c < len; c += 8) {
            int v = i.readUnsignedByte();
            for (int b = 0; b < 8 && c + b < len; b++) a[c + b] = (v & (1 << b)) != 0;
        }
        return a;
    }

    private static void writeDoubles(DataOutputStream o, double[] a) throws IOException {
        o.writeInt(a.length);
        for (double v : a) o.writeDouble(v);
    }

    private static void readDoubles(DataInputStream i, double[] a) throws IOException {
        int n = i.readInt();
        if (n != a.length) throw new IOException("length mismatch " + n + " != " + a.length);
        for (int c = 0; c < n; c++) a[c] = i.readDouble();
    }
}
