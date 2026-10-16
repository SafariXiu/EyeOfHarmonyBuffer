package com.EyeOfHarmonyBuffer.sim.export;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

/**
 * 目标 4（对外导出图像与数值）的最小实现：标量场 -> PNG + TSV。
 *
 * <p>约定：场是 **row-major**，row = z 方向（第 0 行是 z 最小），col = x 方向。
 * PNG 里第 0 行画在**底部**（与地理直觉一致：z 增大向上）。
 */
public final class MapWriter {

    private MapWriter() {}

    /** t 在 [0,1]，返回 0xRRGGBB。 */
    public interface ColorMap {
        int rgb(double t);
    }

    /** 蓝 -> 青 -> 绿 -> 黄 -> 红（适合绝对温度）。 */
    public static final ColorMap THERMAL = new ColorMap() {
        @Override public int rgb(double t) {
            double r, g, b;
            if (t < 0.25) { r = 0; g = 4 * t; b = 1; }
            else if (t < 0.5) { r = 0; g = 1; b = 1 - 4 * (t - 0.25); }
            else if (t < 0.75) { r = 4 * (t - 0.5); g = 1; b = 0; }
            else { r = 1; g = 1 - 4 * (t - 0.75); b = 0; }
            return pack(r, g, b);
        }
    };

    /** 蓝 -> 白 -> 红（适合正负异常）。 */
    public static final ColorMap DIVERGING = new ColorMap() {
        @Override public int rgb(double t) {
            double r, g, b;
            if (t < 0.5) { double u = t * 2; r = u; g = u; b = 1; }
            else { double u = (t - 0.5) * 2; r = 1; g = 1 - u; b = 1 - u; }
            return pack(r, g, b);
        }
    };

    static int pack(double r, double g, double b) {
        int ri = (int) Math.round(clamp01(r) * 255);
        int gi = (int) Math.round(clamp01(g) * 255);
        int bi = (int) Math.round(clamp01(b) * 255);
        return (ri << 16) | (gi << 8) | bi;
    }

    public static double clamp01(double t) { return t < 0 ? 0 : (t > 1 ? 1 : t); }

    /**
     * 关流并**检查错误**。
     *
     * <p>⚠⚠ 审计 D32(c)（2026-09-13 修）：{@code PrintStream} 会把写入过程中的
     * {@code IOException} **吞掉**，只在内部置一个标志。不查 {@code checkError()} 的话，
     * 磁盘满 / 写失败会**静默产出一个截断或空的文件**，而调用方以为导出成功了 ——
     * 对一个「对外导出数据」的功能，这是最坏的失败模式（数据错了但没人知道）。
     */
    private static void closeChecked(PrintStream p, File f) throws IOException {
        p.flush();
        boolean err = p.checkError();
        p.close();
        if (err) throw new IOException("写失败（PrintStream 吞掉了 IOException，通常是磁盘满或路径不可写）: " + f);
    }

    /** PNG 编码器缺失时 {@code ImageIO.write} 只返回 false、不抛异常 —— 同样必须查。 */
    private static void writePngChecked(BufferedImage img, File f) throws IOException {
        if (!ImageIO.write(img, "png", f)) throw new IOException("没有可用的 PNG 编码器，或写入失败: " + f);
    }

    public static void writePng(File f, int w, int h, double[] v, double lo, double hi, ColorMap cm)
            throws IOException {
        File dir = f.getParentFile();
        if (dir != null && !dir.exists()) dir.mkdirs();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        double span = (hi - lo) == 0 ? 1.0 : (hi - lo);
        for (int r = 0; r < h; r++) {
            for (int c = 0; c < w; c++) {
                img.setRGB(c, h - 1 - r, cm.rgb(clamp01((v[r * w + c] - lo) / span)));
            }
        }
        writePngChecked(img, f);
    }

    /**
     * 底图 + 矢量叠加：每 stride 格画一根箭头。**各向同性版本**。
     *
     * @deprecated 审计 D22：它只有一个 pxPerMs，而本项目的导出网格常是**各向异性**的
     *     （P261 的 551x101 网格：x 20 km/px、z 200 km/px）⇒ 箭头方向失真 10 倍。
     *     **请改用带 pxPerMsX / pxPerMsZ 的重载。** 本方法保留只为兼容旧探针，内部委托。
     */
    @Deprecated
    public static void writePngWithVectors(File f, int w, int h, double[] v, double lo, double hi, ColorMap cm,
                                           double[] ux, double[] vz, int stride, double pxPerMs) throws IOException {
        writePngWithVectors(f, w, h, v, lo, hi, cm, ux, vz, stride, pxPerMs, pxPerMs);
    }

    /**
     * 底图 + 矢量叠加（**各向异性**）：pxPerMsX / pxPerMsZ 分别是 x / z 方向的「像素 / (m/s)」。
     *
     * <p>正确用法（审计 D22）：它们应当由网格步长决定（同一个箭头长度基准除以 dx 与 dz），
     * 这样 (u,v) 画出来的**方向**才等于真实的 atan2(v, u)。
     */
    public static void writePngWithVectors(File f, int w, int h, double[] v, double lo, double hi, ColorMap cm,
                                           double[] ux, double[] vz, int stride,
                                           double pxPerMsX, double pxPerMsZ) throws IOException {
        if (stride <= 0) throw new IllegalArgumentException("stride must be > 0 (audit D32: used to hang), got " + stride);
        File dir = f.getParentFile();
        if (dir != null && !dir.exists()) dir.mkdirs();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        double span = (hi - lo) == 0 ? 1.0 : (hi - lo);
        for (int r = 0; r < h; r++) {
            for (int c = 0; c < w; c++) {
                img.setRGB(c, h - 1 - r, cm.rgb(clamp01((v[r * w + c] - lo) / span)));
            }
        }
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(java.awt.Color.BLACK);
        for (int r = stride / 2; r < h; r += stride) {
            for (int c = stride / 2; c < w; c += stride) {
                int i = r * w + c;
                int y0 = h - 1 - r;
                int x1 = (int) Math.round(c + ux[i] * pxPerMsX);
                int y1 = (int) Math.round(y0 - vz[i] * pxPerMsZ);
                g.drawLine(c, y0, x1, y1);
            }
        }
        g.dispose();
        writePngChecked(img, f);
    }

    /**
     * 写导出清单（manifest.tsv）。目标 4 的验收条款是「图 + 数值，**schema 明确**」，
     * 清单就是 schema 的机器可读部分：每个文件是什么场、什么单位、覆盖哪个范围、什么色标。
     */
    public static void writeManifest(File f, String title, String[] header, java.util.List<String[]> rows)
            throws IOException {
        File dir = f.getParentFile();
        if (dir != null && !dir.exists()) dir.mkdirs();
        PrintStream p = new PrintStream(f, "UTF-8");
        p.println("# " + title);
        // ⚠⚠ 审计 D32(b)（2026-09-13 修）：原来这里**无条件**印一行硬编码 schema，
        // 紧接着又印调用方传进来的 header ⇒ **manifest 有两行表头**。
        // 而 P261 传的 header 与那行硬编码**逐字相同**（file field unit x0 z0 dx dz grid range colormap），
        // 所以它印出来是两行一模一样的东西。
        // 正确语义：**表头只有一处来源** —— 调用方给了就用它的，没给才回退到内置 schema。
        if (header != null && header.length > 0) {
            p.print("#");
            for (String h : header) p.print("\t" + h);
            p.println();
        } else {
            // ⚠ E38（P475 抓到）：内置回退原来印成 "# file\t..."（井号后是**空格**），
            // 而调用方那条印成 "#\tfile\t..."（井号后是**TAB**）⇒ manifest 有**两种表头形状**。
            // 机器可读的东西不该有两种形状 ⇒ 统一成与调用方完全一致的 TAB 形式。
            p.println("#\tfile\tfield\tunit\tx0\tz0\tdx\tdz\tgrid\trange\tcolormap");
        }
        for (String[] r : rows) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < r.length; i++) {
                if (i > 0) sb.append('\t');
                sb.append(r[i]);
            }
            p.println(sb.toString());
        }
        closeChecked(p, f);
    }

    /**
     * 导出制表符分隔的数值（每行一个 z，每列一个 x），带表头注释。**各向同性版本**。
     *
     * @deprecated 审计 D21：它只有**一个** step，却把它同时写进 x 与 z 的自述里。
     *     P261 的网格 dx=20 km / dz=200 km，传进来的却是 DX ⇒ **自述的 dz 错 10 倍**，
     *     与 manifest 里正确的 dz 互相矛盾。**请改用带 dx / dz 的重载。** 本方法保留只为兼容旧探针。
     */
    @Deprecated
    public static void writeTsv(File f, int w, int h, double[] v, int x0, int z0, int step, String title)
            throws IOException {
        writeTsv(f, w, h, v, x0, z0, step, step, title);
    }

    /** 导出制表符分隔的数值（**各向异性**：dx / dz 分开给）。 */
    public static void writeTsv(File f, int w, int h, double[] v, int x0, int z0, int dx, int dz, String title)
            throws IOException {
        if (dx <= 0 || dz <= 0) throw new IllegalArgumentException("dx/dz must be > 0, got " + dx + "/" + dz);
        File dir = f.getParentFile();
        if (dir != null && !dir.exists()) dir.mkdirs();
        PrintStream p = new PrintStream(f, "UTF-8");
        p.println("# " + title);
        p.println("# rows = z from " + z0 + " step " + dz + " (" + h + "); cols = x from " + x0 + " step " + dx + " (" + w + ")");
        for (int r = 0; r < h; r++) {
            StringBuilder sb = new StringBuilder();
            for (int c = 0; c < w; c++) {
                if (c > 0) sb.append('\t');
                sb.append(String.format(java.util.Locale.ROOT, "%.3f", v[r * w + c]));
            }
            p.println(sb.toString());
        }
        closeChecked(p, f);
    }
}
