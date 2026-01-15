package com.EyeOfHarmonyBuffer.command;

/**
 * TalosClimateSample
 *
 * 职责：
 * - 调试/诊断用的数据快照对象
 * - 将某个坐标点的 MacroBiome、气候参数（温度/湿度/粗糙度）与高度参数等信息打包
 * - formatForChat() 用于以可读文本输出到日志/聊天/调试工具
 *
 * 说明：
 * - 此类不参与实际地形生成逻辑，只用于观测与排查问题
 * - 可在 message 中扩展输出更多字段（例如 absoluteMin/absoluteMax/baseHeightOffset）
 *   以验证 MacroBiome Height Profile 参数是否生效、是否在边界处平滑
 */
public final class TalosClimateSample {

    private final int x;
    private final int z;
    private final String macroBiomeName;
    private final int macroBiomeId;
    private final float temperature;
    private final float humidity;
    private final float roughness;
    private final double baseHeight;
    private final double macroVariance;
    private final double microVariance;
    private final boolean hardEdge;
    private final float plateauAnchorWeight;
    private final boolean oceanicCandidate;
    private final Double hydroLevel;
    private final Double distanceToCoast;
    private final String message;
    private final boolean success;

    private TalosClimateSample(Builder builder) {
        this.x = builder.x;
        this.z = builder.z;
        this.macroBiomeName = builder.macroBiomeName;
        this.macroBiomeId = builder.macroBiomeId;
        this.temperature = builder.temperature;
        this.humidity = builder.humidity;
        this.roughness = builder.roughness;
        this.baseHeight = builder.baseHeight;
        this.macroVariance = builder.macroVariance;
        this.microVariance = builder.microVariance;
        this.hardEdge = builder.hardEdge;
        this.plateauAnchorWeight = builder.plateauAnchorWeight;
        this.oceanicCandidate = builder.oceanicCandidate;
        this.hydroLevel = builder.hydroLevel;
        this.distanceToCoast = builder.distanceToCoast;
        this.message = builder.message;
        this.success = builder.success;
    }

    public static TalosClimateSample error(int x, int z, String reason) {
        return new Builder(x, z)
            .macroBiome("unknown", -1)
            .message(reason)
            .success(false)
            .build();
    }

    public String formatForChat() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Talos Climate @ (%d, %d)%n", x, z));
        if (message != null && !message.isEmpty()) {
            sb.append(" - ").append(message).append('\n');
        }
        sb.append(String.format(" - MacroBiome: %s (id=%d)%n", macroBiomeName, macroBiomeId));
        sb.append(String.format(" - Temp/Humidity/Roughness: %.3f / %.3f / %.3f%n",
            temperature, humidity, roughness));
        sb.append(String.format(" - BaseHeight=%.2f, MacroVar=%.2f, MicroVar=%.2f%n",
            baseHeight, macroVariance, microVariance));
        sb.append(String.format(" - HardEdge=%s, PlateauWeight=%.2f, OceanicCandidate=%s%n",
            hardEdge, plateauAnchorWeight, oceanicCandidate));
        if (hydroLevel != null) {
            sb.append(String.format(" - HydroLevel=%.2f%n", hydroLevel));
        }
        if (distanceToCoast != null) {
            sb.append(String.format(" - DistanceToCoast=%.2f%n", distanceToCoast));
        }
        sb.append(String.format(" - Result=%s", success ? "OK" : "ERROR"));
        return sb.toString();
    }

    public static final class Builder {
        private final int x;
        private final int z;
        private String macroBiomeName = "unknown";
        private int macroBiomeId = -1;
        private float temperature = Float.NaN;
        private float humidity = Float.NaN;
        private float roughness = Float.NaN;
        private double baseHeight = Double.NaN;
        private double macroVariance = Double.NaN;
        private double microVariance = Double.NaN;
        private boolean hardEdge;
        private float plateauAnchorWeight;
        private boolean oceanicCandidate;
        private Double hydroLevel;
        private Double distanceToCoast;
        private String message = "";
        private boolean success = true;

        public Builder(int x, int z) {
            this.x = x;
            this.z = z;
        }

        public Builder macroBiome(String name, int id) {
            this.macroBiomeName = name;
            this.macroBiomeId = id;
            return this;
        }

        public Builder climate(float temperature, float humidity, float roughness) {
            this.temperature = temperature;
            this.humidity = humidity;
            this.roughness = roughness;
            return this;
        }

        public Builder heights(double baseHeight, double macroVariance, double microVariance) {
            this.baseHeight = baseHeight;
            this.macroVariance = macroVariance;
            this.microVariance = microVariance;
            return this;
        }

        public Builder hardEdge(boolean hardEdge) {
            this.hardEdge = hardEdge;
            return this;
        }

        public Builder plateauAnchorWeight(float weight) {
            this.plateauAnchorWeight = weight;
            return this;
        }

        public Builder oceanicCandidate(boolean flag) {
            this.oceanicCandidate = flag;
            return this;
        }

        public Builder hydroLevel(Double level) {
            this.hydroLevel = level;
            return this;
        }

        public Builder distanceToCoast(Double distance) {
            this.distanceToCoast = distance;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public TalosClimateSample build() {
            return new TalosClimateSample(this);
        }
    }
}
