package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Rvr2Loader {

    private static final float  FLOAT_EPSILON = 1.0e-5f;
    private static final double BBOX_EPSILON  = 1.0e-3;

    private Rvr2Loader() {}

    private static final class MutableEdge {
        int id;
        int parentId;
        int parentSegment;

        RiverType type;
        RiverRelation relation;

        boolean hasSource;
        boolean hasMouth;

        float visualWidthScale;
        float widthStart;
        float widthEnd;
        float influenceRadius;
        float parentT;

        List<RiverPoint> points = new ArrayList<RiverPoint>();
    }

    public static RiverNetwork load(InputStream rawInput) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(rawInput));

        byte[] magic = new byte[4];
        in.readFully(magic);

        if (magic[0] != 'R' || magic[1] != 'V' || magic[2] != 'R' || magic[3] != '2') {
            throw new IOException("Invalid RVR2 magic.");
        }

        int version = in.readUnsignedShort();
        if (version != 2) {
            throw new IOException("Unsupported RVR2 version: " + version);
        }

        int coordinateScale = in.readUnsignedShort();
        if (coordinateScale <= 0) {
            throw new IOException("Invalid coordinate scale: " + coordinateScale);
        }
        if (coordinateScale != 16) {
            throw new IOException("Unsupported coordinateScale (expected 16): " + coordinateScale);
        }

        int minXFixed = in.readInt();
        int minZFixed = in.readInt();
        int maxXFixed = in.readInt();
        int maxZFixed = in.readInt();

        long seed = in.readLong();

        long edgeCountUnsigned = Integer.toUnsignedLong(in.readInt());
        if (edgeCountUnsigned == 0L || edgeCountUnsigned > 1_000_000L) {
            throw new IOException("Invalid edge count: " + edgeCountUnsigned);
        }
        int edgeCount = (int) edgeCountUnsigned;

        double scaleD = (double) coordinateScale;
        double minX = minXFixed / scaleD;
        double minZ = minZFixed / scaleD;
        double maxX = maxXFixed / scaleD;
        double maxZ = maxZFixed / scaleD;

        List<MutableEdge> mutableEdges = new ArrayList<MutableEdge>(edgeCount);

        int expectedEdgeId = 0;

        double actualMinX = Double.POSITIVE_INFINITY;
        double actualMinZ = Double.POSITIVE_INFINITY;
        double actualMaxX = Double.NEGATIVE_INFINITY;
        double actualMaxZ = Double.NEGATIVE_INFINITY;

        for (int e = 0; e < edgeCount; e++) {
            int edgeId = in.readInt();
            int parentId = in.readInt();
            int parentSegment = in.readInt();

            int typeCode = in.readUnsignedByte();
            int relationCode = in.readUnsignedByte();
            int flags = in.readUnsignedByte();
            /* reserved */ in.readUnsignedByte();

            float visualWidthScale = in.readFloat();
            float widthStart = in.readFloat();
            float widthEnd = in.readFloat();
            float influenceRadius = in.readFloat();
            float parentT = in.readFloat();

            long pointCountUnsigned = Integer.toUnsignedLong(in.readInt());

            if (edgeId != expectedEdgeId) {
                throw new IOException("Non-sequential edge ID: " + edgeId + " (expected " + expectedEdgeId + ")");
            }
            expectedEdgeId++;

            if (pointCountUnsigned < 2L || pointCountUnsigned > 10_000_000L) {
                throw new IOException("Invalid point count: " + pointCountUnsigned + " for edge " + edgeId);
            }
            int pointCount = (int) pointCountUnsigned;

            if (!Float.isFinite(visualWidthScale)
                || !Float.isFinite(widthStart)
                || !Float.isFinite(widthEnd)
                || !Float.isFinite(influenceRadius)) {
                throw new IOException("Non-finite river property for edge " + edgeId);
            }

            if (visualWidthScale <= 0.0f
                || widthStart <= 0.0f
                || widthEnd <= 0.0f
                || influenceRadius <= 0.0f) {
                throw new IOException("Invalid river physical property (<=0) for edge " + edgeId);
            }

            if (widthStart > 10_000.0f
                || widthEnd > 10_000.0f
                || influenceRadius > 100_000.0f) {
                throw new IOException("Unreasonably large river property for edge " + edgeId);
            }

            RiverType type;
            RiverRelation relation;
            try {
                type = RiverType.fromCode(typeCode);
            } catch (IllegalArgumentException ex) {
                throw new IOException("Invalid river type code " + typeCode + " for edge " + edgeId, ex);
            }

            try {
                relation = RiverRelation.fromCode(relationCode);
            } catch (IllegalArgumentException ex) {
                throw new IOException("Invalid river relation code " + relationCode + " for edge " + edgeId, ex);
            }

            boolean hasSource = (flags & 1) != 0;
            boolean hasMouth  = (flags & 2) != 0;

            MutableEdge me = new MutableEdge();
            me.id = edgeId;
            me.parentId = parentId;
            me.parentSegment = parentSegment;
            me.type = type;
            me.relation = relation;
            me.hasSource = hasSource;
            me.hasMouth = hasMouth;
            me.visualWidthScale = visualWidthScale;
            me.widthStart = widthStart;
            me.widthEnd = widthEnd;
            me.influenceRadius = influenceRadius;
            me.parentT = parentT;

            for (int p = 0; p < pointCount; p++) {
                int fixedX = in.readInt();
                int fixedZ = in.readInt();

                double x = fixedX / scaleD;
                double z = fixedZ / scaleD;

                if (x < actualMinX) actualMinX = x;
                if (x > actualMaxX) actualMaxX = x;
                if (z < actualMinZ) actualMinZ = z;
                if (z > actualMaxZ) actualMaxZ = z;

                me.points.add(new RiverPoint(x, z));
            }

            mutableEdges.add(me);
        }

        if (!withinEpsilon(actualMinX, minX, BBOX_EPSILON)
            || !withinEpsilon(actualMinZ, minZ, BBOX_EPSILON)
            || !withinEpsilon(actualMaxX, maxX, BBOX_EPSILON)
            || !withinEpsilon(actualMaxZ, maxZ, BBOX_EPSILON)) {
            throw new IOException(
                "River network AABB mismatch header. "
                    + "Header=[" + minX + "," + minZ + " → " + maxX + "," + maxZ + "], "
                    + "Actual=[" + actualMinX + "," + actualMinZ + " → " + actualMaxX + "," + actualMaxZ + "]"
            );
        }

        MutableEdge mainRiver = null;

        for (int i = 0; i < edgeCount; i++) {
            MutableEdge child = mutableEdges.get(i);

            int edgeId = child.id;
            int parentId = child.parentId;
            int parentSegment = child.parentSegment;
            RiverType type = child.type;
            RiverRelation relation = child.relation;
            boolean hasSource = child.hasSource;
            boolean hasMouth = child.hasMouth;

            if (relation == RiverRelation.ROOT) {
                if (parentId != -1 || parentSegment != -1) {
                    throw new IOException("ROOT edge must have parentId=-1 and parentSegment=-1, edgeId=" + edgeId);
                }

                if (type != RiverType.MAIN) {
                    throw new IOException("ROOT edge must be MAIN river, edgeId=" + edgeId);
                }

                if (!hasSource || !hasMouth) {
                    throw new IOException("MAIN ROOT river must have both source and mouth, edgeId=" + edgeId);
                }

                if (!Float.isFinite(child.parentT)) {
                    throw new IOException("Invalid parentT for ROOT edge " + edgeId + ": " + child.parentT);
                }

                if (mainRiver != null) {
                    throw new IOException("Multiple ROOT MAIN rivers found (edgeId=" + edgeId + ")");
                }

                mainRiver = child;
                continue;
            }

            if (parentId < 0 || parentId >= edgeId) {
                throw new IOException("Invalid parentId " + parentId + " for edge " + edgeId + " (must be in [0, " + (edgeId - 1) + "])");
            }

            if (!Float.isFinite(child.parentT)
                || child.parentT < -FLOAT_EPSILON
                || child.parentT > 1.0f + FLOAT_EPSILON) {
                throw new IOException("Invalid parentT for edge " + edgeId + ": " + child.parentT);
            }

            MutableEdge parent = mutableEdges.get(parentId);

            int parentPointsCount = parent.points.size();
            if (parentSegment < 0 || parentSegment >= parentPointsCount - 1) {
                throw new IOException(
                    "Invalid parentSegment " + parentSegment
                        + " for edge " + edgeId
                        + " (parent edge " + parentId + " has " + parentPointsCount + " points)"
                );
            }

            switch (type) {
                case MAIN:
                    throw new IOException("Non-ROOT MAIN river is not allowed (edgeId=" + edgeId + ")");

                case BRANCH1:
                    if (relation == RiverRelation.INTO_PARENT) {
                        if (parent.type != RiverType.MAIN) {
                            throw new IOException("BRANCH1 INTO_PARENT must have MAIN parent, edgeId=" + edgeId);
                        }
                        if (!hasSource || hasMouth) {
                            throw new IOException("BRANCH1 INTO_PARENT must have hasSource=true, hasMouth=false, edgeId=" + edgeId);
                        }
                    } else if (relation == RiverRelation.FROM_PARENT) {
                        if (parent.type != RiverType.MAIN) {
                            throw new IOException("BRANCH1 FROM_PARENT must have MAIN parent, edgeId=" + edgeId);
                        }
                        if (hasSource || !hasMouth) {
                            throw new IOException("BRANCH1 FROM_PARENT must have hasSource=false, hasMouth=true, edgeId=" + edgeId);
                        }
                    } else {
                        throw new IOException("BRANCH1 river cannot have relation=" + relation + " (edgeId=" + edgeId + ")");
                    }
                    break;

                case BRANCH2:
                    if (relation != RiverRelation.INTO_PARENT) {
                        throw new IOException("BRANCH2 river must be INTO_PARENT relation, edgeId=" + edgeId + ")");
                    }
                    if (parent.type != RiverType.BRANCH1) {
                        throw new IOException("BRANCH2 parent must be BRANCH1, edgeId=" + edgeId + ")");
                    }
                    if (!hasSource || hasMouth) {
                        throw new IOException("BRANCH2 INTO_PARENT must have hasSource=true, hasMouth=false, edgeId=" + edgeId);
                    }
                    break;

                default:
                    throw new IOException("Unknown river type enum for edge " + edgeId);
            }

            RiverPoint a = parent.points.get(parentSegment);
            RiverPoint b = parent.points.get(parentSegment + 1);

            double t = Math.max(0.0, Math.min(1.0, child.parentT));
            double connectionX = a.getX() + (b.getX() - a.getX()) * t;
            double connectionZ = a.getZ() + (b.getZ() - a.getZ()) * t;
            RiverPoint connectionPoint = new RiverPoint(connectionX, connectionZ);

            if (relation == RiverRelation.INTO_PARENT) {
                int lastIndex = child.points.size() - 1;
                child.points.set(lastIndex, connectionPoint);
            } else if (relation == RiverRelation.FROM_PARENT) {
                child.points.set(0, connectionPoint);
            }
        }

        if (mainRiver == null) {
            throw new IOException("No ROOT MAIN river found in network.");
        }

        try {
            int extra = in.read();
            if (extra != -1) {
                throw new IOException("Trailing data detected after RVR2 content.");
            }
        } catch (EOFException ignored) {
        }

        List<RiverEdgeData> edges = new ArrayList<RiverEdgeData>(edgeCount);
        for (int i = 0; i < edgeCount; i++) {
            MutableEdge me = mutableEdges.get(i);
            List<RiverPoint> immutablePoints = Collections.unmodifiableList(new ArrayList<RiverPoint>(me.points));

            RiverEdgeData edgeData = new RiverEdgeData(
                me.id,
                me.parentId,
                me.parentSegment,
                me.type,
                me.relation,
                me.hasSource,
                me.hasMouth,
                me.visualWidthScale,
                me.widthStart,
                me.widthEnd,
                me.influenceRadius,
                me.parentT,
                immutablePoints
            );
            edges.add(edgeData);
        }

        List<RiverEdgeData> immutableEdges = Collections.unmodifiableList(edges);

        return new RiverNetwork(
            version,
            coordinateScale,
            minX,
            minZ,
            maxX,
            maxZ,
            seed,
            immutableEdges
        );
    }

    private static boolean withinEpsilon(double value, double target, double eps) {
        return Math.abs(value - target) <= eps;
    }
}
