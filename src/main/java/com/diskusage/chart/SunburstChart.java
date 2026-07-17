package com.diskusage.chart;

import com.diskusage.model.DirNode;
import com.diskusage.util.FormatUtil;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcTo;
import javafx.scene.shape.Circle;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Shape;
import javafx.scene.layout.Region;
import javafx.util.Duration;

/**
 * A multi-ring "sunburst" chart: the selected root is the center circle, each
 * ring outward represents one more level of subdirectories, and (optionally)
 * a final segment represents unused free space on the containing volume.
 *
 * JavaFX has no built-in sunburst/multi-level pie control, so rings are drawn
 * as annular-sector {@link Path} shapes (outer arc, line, inner arc, close).
 */
public class SunburstChart extends Region {

    private static final double CENTER_RADIUS = 30;
    private static final double RING_GAP = 1.5;

    private static final Color[] PALETTE = {
            Color.web("#4E79A7"), Color.web("#F28E2B"), Color.web("#E15759"), Color.web("#76B7B2"),
            Color.web("#59A14F"), Color.web("#EDC948"), Color.web("#B07AA1"), Color.web("#FF9DA7"),
            Color.web("#9C755F"), Color.web("#BAB0AC")
    };
    private static final Color FREE_SPACE_COLOR = Color.WHITE;
    private static final Color FREE_SPACE_STROKE = Color.web("#b0b0b0");
    private static final Color CENTER_COLOR = Color.web("#37474f");

    private final Group segmentGroup = new Group();
    private final Label placeholderLabel = new Label("Select a folder and click Scan");
    private final Label busyLabel = new Label("Recalculating…");

    private DirNode rootNode;
    private long freeSpace;
    private int maxDepth = 3;
    private boolean busy;

    public SunburstChart() {
        setMinSize(200, 200);
        setPrefSize(520, 520);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        placeholderLabel.setStyle("-fx-opacity: 0.6;");
        busyLabel.setStyle("-fx-background-color: rgba(20,20,20,0.82); -fx-text-fill: white; "
                + "-fx-padding: 8 16; -fx-background-radius: 6; -fx-font-size: 13px;");
        busyLabel.setMouseTransparent(true);
        busyLabel.setVisible(false);
        getChildren().addAll(segmentGroup, placeholderLabel, busyLabel);
    }

    public void setData(DirNode root, long freeSpace, int maxDepth) {
        this.rootNode = root;
        this.freeSpace = Math.max(0, freeSpace);
        this.maxDepth = Math.max(1, maxDepth);
        requestLayout();
    }

    public void clear() {
        this.rootNode = null;
        this.freeSpace = 0;
        requestLayout();
    }

    /**
     * While busy, the previously rendered chart (if any) stays visible but dimmed
     * and non-interactive, with a "Recalculating..." indicator over the center.
     */
    public void setBusy(boolean busy) {
        this.busy = busy;
        requestLayout();
    }

    @Override
    protected void layoutChildren() {
        render();
    }

    private void render() {
        segmentGroup.getChildren().clear();
        double w = getWidth();
        double h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        boolean hasData = rootNode != null && (rootNode.getSize() + freeSpace) > 0;
        placeholderLabel.setText(busy ? "Scanning…" : "Select a folder and click Scan");
        placeholderLabel.setVisible(!hasData);
        placeholderLabel.setAlignment(Pos.CENTER);
        placeholderLabel.resizeRelocate(0, 0, w, h);

        segmentGroup.setMouseTransparent(busy);
        segmentGroup.setOpacity(busy ? 0.35 : 1.0);

        if (!hasData) {
            busyLabel.setVisible(false);
            return;
        }

        double cx = w / 2.0;
        double cy = h / 2.0;

        busyLabel.setVisible(busy);
        if (busy) {
            double lw = busyLabel.prefWidth(-1);
            double lh = busyLabel.prefHeight(lw);
            busyLabel.resizeRelocate(cx - lw / 2, cy - lh / 2, lw, lh);
            busyLabel.toFront();
        }

        double maxOuterR = Math.min(w, h) / 2.0 - 4;
        if (maxOuterR <= CENTER_RADIUS) {
            return;
        }
        double ringThickness = (maxOuterR - CENTER_RADIUS) / maxDepth;
        long totalSize = rootNode.getSize() + freeSpace;

        Circle center = new Circle(cx, cy, CENTER_RADIUS, CENTER_COLOR);
        center.setStroke(Color.WHITE);
        center.setStrokeWidth(1);
        installTooltip(center, tooltipText(rootNode.getName(), rootNode.getPath().toString(), rootNode.getSize(), totalSize));
        segmentGroup.getChildren().add(center);

        double angle = 0;
        int colorIndex = 0;
        for (DirNode child : rootNode.getChildren()) {
            double span = totalSize > 0 ? child.getSize() / (double) totalSize * 360.0 : 0;
            if (span > 0.03) {
                drawNode(child, 1, angle, span, PALETTE[colorIndex % PALETTE.length], cx, cy, ringThickness, totalSize);
            }
            angle += span;
            colorIndex++;
        }

        if (freeSpace > 0) {
            double span = totalSize > 0 ? freeSpace / (double) totalSize * 360.0 : 0;
            if (span > 0.03) {
                Shape seg = ringSegment(cx, cy, CENTER_RADIUS, CENTER_RADIUS + ringThickness - RING_GAP, angle, span);
                seg.setFill(FREE_SPACE_COLOR);
                seg.setStroke(FREE_SPACE_STROKE);
                seg.setStrokeWidth(0.75);
                installTooltip(seg, "Free space\n" + FormatUtil.formatBytes(freeSpace)
                        + " (" + FormatUtil.formatPercent(freeSpace / (double) totalSize) + ")");
                addHoverEffect(seg);
                segmentGroup.getChildren().add(seg);
            }
        }
    }

    private void drawNode(DirNode node, int depth, double startAngle, double extentAngle, Color baseColor,
                           double cx, double cy, double ringThickness, long totalSize) {
        double innerR = CENTER_RADIUS + (depth - 1) * ringThickness;
        double outerR = innerR + ringThickness - RING_GAP;
        Shape seg = ringSegment(cx, cy, innerR, outerR, startAngle, extentAngle);
        seg.setFill(shade(baseColor, depth));
        seg.setStroke(Color.WHITE);
        seg.setStrokeWidth(0.75);
        installTooltip(seg, tooltipText(node.getName(), node.getPath().toString(), node.getSize(), totalSize));
        addHoverEffect(seg);
        segmentGroup.getChildren().add(seg);

        if (depth < maxDepth && !node.getChildren().isEmpty()) {
            long childTotal = node.getSize();
            double angle = startAngle;
            for (DirNode child : node.getChildren()) {
                double span = childTotal > 0 ? child.getSize() / (double) childTotal * extentAngle : 0;
                if (span > 0.03) {
                    drawNode(child, depth + 1, angle, span, baseColor, cx, cy, ringThickness, totalSize);
                }
                angle += span;
            }
        }
    }

    private static void addHoverEffect(Shape seg) {
        seg.setOnMouseEntered(e -> {
            seg.setOpacity(0.75);
            seg.setCursor(Cursor.HAND);
        });
        seg.setOnMouseExited(e -> seg.setOpacity(1.0));
    }

    private static void installTooltip(Shape shape, String text) {
        Tooltip tooltip = new Tooltip(text);
        tooltip.setShowDelay(Duration.millis(100));
        Tooltip.install(shape, tooltip);
    }

    private static String tooltipText(String name, String path, long size, long totalSize) {
        double pct = totalSize > 0 ? size / (double) totalSize : 0;
        return name + "\n" + FormatUtil.formatBytes(size) + " (" + FormatUtil.formatPercent(pct) + ")\n" + path;
    }

    private static Color shade(Color base, int depth) {
        double factor = 1.0 + (depth - 1) * 0.16;
        return base.deriveColor(0, 1.0, factor, 1.0);
    }

    private static Shape ringSegment(double cx, double cy, double innerR, double outerR, double startDeg, double extentDeg) {
        double clampedExtent = Math.min(extentDeg, 359.99);
        double[] outerStart = pointAt(cx, cy, outerR, startDeg);
        double[] outerEnd = pointAt(cx, cy, outerR, startDeg + clampedExtent);
        double[] innerEnd = pointAt(cx, cy, innerR, startDeg + clampedExtent);
        double[] innerStart = pointAt(cx, cy, innerR, startDeg);
        boolean largeArc = clampedExtent > 180;

        Path path = new Path();
        path.getElements().add(new MoveTo(outerStart[0], outerStart[1]));
        path.getElements().add(new ArcTo(outerR, outerR, 0, outerEnd[0], outerEnd[1], largeArc, true));
        path.getElements().add(new LineTo(innerEnd[0], innerEnd[1]));
        path.getElements().add(new ArcTo(innerR, innerR, 0, innerStart[0], innerStart[1], largeArc, false));
        path.getElements().add(new ClosePath());
        return path;
    }

    private static double[] pointAt(double cx, double cy, double r, double angleDeg) {
        double rad = Math.toRadians(angleDeg - 90);
        return new double[]{cx + r * Math.cos(rad), cy + r * Math.sin(rad)};
    }
}
