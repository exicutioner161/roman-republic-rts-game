package world;

import java.awt.Color;
import java.awt.Graphics2D;

public final class StructureRenderHelper {
    private static final Color STRUCTURE_FILL_COLOR = new Color(152, 144, 126, 235);
    private static final Color STRUCTURE_OUTLINE_COLOR = new Color(94, 82, 64, 230);
    private static final Color BRIDGE_DECK_COLOR = new Color(156, 119, 74);
    private static final Color BRIDGE_PLANK_COLOR = new Color(116, 81, 48, 190);
    private static final Color BRIDGE_RAIL_COLOR = new Color(86, 58, 34, 220);

    private StructureRenderHelper() {}

    public static void drawBridge(Graphics2D g2d, int x, int y, int tileSize, boolean horizontal, boolean linkStart,
            boolean linkEnd) {
        if (tileSize <= 0) {
            return;
        }
        g2d.setColor(BRIDGE_DECK_COLOR);
        g2d.fillRect(x, y, tileSize, tileSize);
        drawBridgePlanks(g2d, x, y, tileSize, horizontal);
        drawBridgeRails(g2d, x, y, tileSize, horizontal);
        g2d.setColor(STRUCTURE_OUTLINE_COLOR);
        g2d.drawRect(x, y, Math.max(0, tileSize - 1), Math.max(0, tileSize - 1));
    }

    private static void drawBridgePlanks(Graphics2D g2d, int x, int y, int tileSize, boolean horizontal) {
        g2d.setColor(BRIDGE_PLANK_COLOR);
        int seamSpacing = Math.max(6, tileSize / 4);
        if (horizontal) {
            for (int seamX = x + seamSpacing; seamX < x + tileSize; seamX += seamSpacing) {
                g2d.drawLine(seamX, y + 3, seamX, y + tileSize - 4);
            }
            return;
        }
        for (int seamY = y + seamSpacing; seamY < y + tileSize; seamY += seamSpacing) {
            g2d.drawLine(x + 3, seamY, x + tileSize - 4, seamY);
        }
    }

    private static void drawBridgeRails(Graphics2D g2d, int x, int y, int tileSize, boolean horizontal) {
        g2d.setColor(BRIDGE_RAIL_COLOR);
        int railInset = Math.max(4, tileSize / 8);
        if (horizontal) {
            g2d.drawLine(x, y + railInset, x + tileSize - 1, y + railInset);
            g2d.drawLine(x, y + tileSize - railInset - 1, x + tileSize - 1, y + tileSize - railInset - 1);
            return;
        }
        g2d.drawLine(x + railInset, y, x + railInset, y + tileSize - 1);
        g2d.drawLine(x + tileSize - railInset - 1, y, x + tileSize - railInset - 1, y + tileSize - 1);
    }

    public static void drawFort(Graphics2D g2d, int x, int y, int tileSize) {
        int inset = Math.max(4, tileSize / 6);
        int size = tileSize - inset * 2;
        fillOutlinedRoundRect(g2d, x + inset, y + inset, size, size, Math.max(5, tileSize / 5));
    }

    public static void drawWall(Graphics2D g2d, int x, int y, int tileSize, boolean north, boolean south, boolean west,
            boolean east) {
        int connectionCount = (north ? 1 : 0) + (south ? 1 : 0) + (west ? 1 : 0) + (east ? 1 : 0);
        if (connectionCount == 0) {
            drawWallPost(g2d, x, y, tileSize);
            return;
        }
        int thickness = Math.max(4, tileSize / 5);
        int centerX = x + tileSize / 2 - thickness / 2;
        int centerY = y + tileSize / 2 - thickness / 2;
        int verticalY = north ? y : centerY;
        int verticalHeight = (south ? y + tileSize : centerY + thickness) - verticalY;
        int horizontalX = west ? x : centerX;
        int horizontalWidth = (east ? x + tileSize : centerX + thickness) - horizontalX;
        if (north || south) {
            fillOutlinedRect(g2d, centerX, verticalY, thickness, verticalHeight);
            drawVerticalWallBattlements(g2d, centerX, verticalY, thickness, verticalHeight, tileSize);
        }
        if (west || east) {
            fillOutlinedRect(g2d, horizontalX, centerY, horizontalWidth, thickness);
            drawHorizontalWallBattlements(g2d, horizontalX, centerY, horizontalWidth, thickness, tileSize);
        }
        fillOutlinedRect(g2d, centerX, centerY, thickness, thickness);
    }

    private static void drawVerticalWallBattlements(Graphics2D g2d, int centerX, int verticalY, int thickness,
            int verticalHeight, int tileSize) {
        int battlementDepth = Math.max(2, tileSize / 8);
        int battlementLength = Math.max(4, tileSize / 6);
        int step = Math.max(battlementLength + 2, tileSize / 4);
        for (int y = verticalY + battlementLength / 2; y < verticalY + verticalHeight; y += step) {
            fillOutlinedRect(g2d, centerX - battlementDepth, y, battlementDepth, battlementLength);
            fillOutlinedRect(g2d, centerX + thickness, y, battlementDepth, battlementLength);
        }
    }

    private static void drawHorizontalWallBattlements(Graphics2D g2d, int horizontalX, int centerY, int horizontalWidth,
            int thickness, int tileSize) {
        int battlementDepth = Math.max(2, tileSize / 8);
        int battlementLength = Math.max(4, tileSize / 6);
        int step = Math.max(battlementLength + 2, tileSize / 4);
        for (int x = horizontalX + battlementLength / 2; x < horizontalX + horizontalWidth; x += step) {
            fillOutlinedRect(g2d, x, centerY - battlementDepth, battlementLength, battlementDepth);
            fillOutlinedRect(g2d, x, centerY + thickness, battlementLength, battlementDepth);
        }
    }

    private static void drawWallPost(Graphics2D g2d, int x, int y, int tileSize) {
        int size = Math.max(6, tileSize / 3);
        int inset = tileSize / 2 - size / 2;
        fillOutlinedRect(g2d, x + inset, y + inset, size, size);
    }

    private static void fillOutlinedRoundRect(Graphics2D g2d, int x, int y, int width, int height, int arc) {
        if (width <= 0 || height <= 0) {
            return;
        }
        g2d.setColor(STRUCTURE_FILL_COLOR);
        g2d.fillRoundRect(x, y, width, height, arc, arc);
        g2d.setColor(STRUCTURE_OUTLINE_COLOR);
        g2d.drawRoundRect(x, y, Math.max(0, width - 1), Math.max(0, height - 1), arc, arc);
    }

    private static void fillOutlinedRect(Graphics2D g2d, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        g2d.setColor(STRUCTURE_FILL_COLOR);
        g2d.fillRect(x, y, width, height);
        g2d.setColor(STRUCTURE_OUTLINE_COLOR);
        g2d.drawRect(x, y, Math.max(0, width - 1), Math.max(0, height - 1));
    }
}