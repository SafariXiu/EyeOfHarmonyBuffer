package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.rbmk.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * 真实切尔诺贝利四号堆芯栅格数据（读自 assets/…/rbmk/core_map.json）。
 * 图例：0=石墨砌体('.')，1=燃料压力管('F')，2=普通控制棒('R')，3=缩短吸收棒UA('S')，
 *       4=自动控制棒('A')，5=LAR棒('L')。
 * 48×48，间距 250mm。
 */
public class RbmkCoreData {

    public static final int GRID = 48;

    /** 实时通道数据源（模拟或机器注入）。 */
    private static RbmkChannelProvider channelProvider = null;

    private static int[][] cells;
    private static int fuel = 0;
    private static int rods = 0;
    private static int shortRods = 0;
    private static int autoRods = 0;
    private static int larRods = 0;
    private static int graphite = 0;
    private static boolean loaded = false;

    public static void ensureLoaded() {
        if (loaded) {
            return;
        }
        try {
            InputStream in = RbmkCoreData.class
                .getResourceAsStream("/assets/eyeofharmonybuffer/rbmk/core_map.json");
            if (in == null) {
                return;
            }
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, "UTF-8"));
            JsonObject obj = new JsonParser().parse(reader).getAsJsonObject();
            JsonArray map = obj.getAsJsonArray("map");
            int rows = map.size();
            cells = new int[rows][];
            for (int y = 0; y < rows; y++) {
                String line = map.get(y).getAsString();
                cells[y] = new int[line.length()];
                for (int x = 0; x < line.length(); x++) {
                    char c = line.charAt(x);
                    switch (c) {
                        case 'F':
                            cells[y][x] = 1;
                            fuel++;
                            break;
                        case 'R':
                            cells[y][x] = 2;
                            rods++;
                            break;
                        case 'S':
                            cells[y][x] = 3;
                            shortRods++;
                            break;
                        case 'A':
                            cells[y][x] = 4;
                            autoRods++;
                            break;
                        case 'L':
                            cells[y][x] = 5;
                            larRods++;
                            break;
                        default:
                            cells[y][x] = 0;
                            graphite++;
                            break;
                    }
                }
            }
            reader.close();
            loaded = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 设置实时通道数据源（机器接入点）。传 null 恢复模拟数据。 */
    public static void setChannelProvider(RbmkChannelProvider p) {
        channelProvider = p;
    }

    /** 取 (row, col) 通道数据：优先走 provider；无 provider 时用确定性模拟数据。 */
    public static RbmkChannel channelAt(int row, int col) {
        RbmkChannel c = channelProvider != null ? channelProvider.channel(row, col) : null;
        if (c != null) {
            return c;
        }
        return new RbmkChannel(at(row, col), (long) row * 100003L + col);
    }

    /** 通道类型 → 颜色（大屏配色）。0=石墨砌体（调用方自行跳过不画）。 */
    public static int colorFor(int t) {
        switch (t) {
            case 1:  return 0xFF9AA4AE; // 燃料压力管（灰）
            case 2:  return 0xFF44C955; // 普通控制棒（绿）
            case 3:  return 0xFFE6CC3A; // 缩短吸收棒 UA（黄）
            case 4:  return 0xFFE04848; // 自动控制棒（红）
            case 5:  return 0xFF3D6BE0; // LAR 棒（蓝）
            default: return 0xFF343C46;
        }
    }

    /** 通道类型 → 显示名。 */
    public static String typeName(int t) {
        switch (t) {
            case 1:  return "燃料压力管";
            case 2:  return "普通控制棒";
            case 3:  return "缩短吸收棒 UA";
            case 4:  return "自动控制棒";
            case 5:  return "LAR 棒";
            default: return "石墨砌体";
        }
    }

    public static int at(int y, int x) {
        return (cells != null && y >= 0 && y < cells.length && x >= 0 && x < cells[y].length)
            ? cells[y][x] : 0;
    }

    public static int getFuel() { return fuel; }
    /** 全部吸收/控制棒通道：普通 R + 缩短 S + 自动 A + LAR L。 */
    public static int getRods() { return rods + shortRods + autoRods + larRods; }
    public static int getControlRods() { return rods; }
    public static int getShortRods() { return shortRods; }
    public static int getAutoRods() { return autoRods; }
    public static int getLarRods() { return larRods; }
    public static int getGraphite() { return graphite; }
    public static boolean isLoaded() { return loaded; }
}