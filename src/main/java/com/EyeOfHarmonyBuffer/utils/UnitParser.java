package com.EyeOfHarmonyBuffer.utils;

public class UnitParser {

    /**
     * 解析带有单位的数字字符串，将其转换为 long 类型的数值。
     * 支持的单位包括：K, M, B, G, T, P, E。
     *
     * @param input 玩家输入的字符串（例如 "5K", "10M", "2T"）
     * @return 转换后的 long 类型数值
     * @throws NumberFormatException 当输入格式不正确时抛出异常
     */
    public static long parseQuantityWithUnits(String input) throws NumberFormatException {
        input = input.trim()
            .toUpperCase(); // 去掉空格并转换为大写

        // 获取最后一个字符，判断是否是单位
        char unit = input.charAt(input.length() - 1);
        long multiplier = 1;
        String numberPart = input;

        switch (unit) {
            case 'K':
                multiplier = 1000L; // 千
                numberPart = input.substring(0, input.length() - 1);
                break;
            case 'M':
                multiplier = 1000000L; // 百万
                numberPart = input.substring(0, input.length() - 1);
                break;
            case 'B':
            case 'G': // 十亿
                multiplier = 1000000000L; // 十亿
                numberPart = input.substring(0, input.length() - 1);
                break;
            case 'T':
                multiplier = 1000000000000L; // 万亿
                numberPart = input.substring(0, input.length() - 1);
                break;
            case 'P':
                multiplier = 1000000000000000L; // 千万亿
                numberPart = input.substring(0, input.length() - 1);
                break;
            case 'E':
                multiplier = 1000000000000000000L; // 百亿亿
                numberPart = input.substring(0, input.length() - 1);
                break;
            default:
                // 没有单位，直接解析为普通数字
                break;
        }

        // 解析数字部分并乘以相应的单位倍数
        return Long.parseLong(numberPart) * multiplier;
    }
}
