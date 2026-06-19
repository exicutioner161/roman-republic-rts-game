package utils;

import camera.RTSCamera;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import main.GamePanel;
import units.Unit;
import units.UnitManager;
import world.TileMap;

public class MouseHandler extends MouseAdapter {
    private static final double FORMATION_UNIT_GAP = GamePanel.TILE_SIZE * 0.1;
    private static final double FORMATION_DIRECTION_LOCK_DISTANCE = GamePanel.TILE_SIZE * 0.35;
    private static final double COMPACT_MOVE_SLOT_GAP = GamePanel.TILE_SIZE * 0.15;
    private Point startCoords;
    private Point endCoords;
    private boolean drawing = false;
    private boolean leftPressed = false;
    private boolean rightPressed = false;
    private boolean middlePressed = false;
    private boolean formationOrderActive = false;
    private Double lockedFormationFacingAngle;
    private Point hoverScreenCoords;
    private final GamePanel gp;
    private final RTSCamera camera;

    public MouseHandler(GamePanel gp, RTSCamera camera) {
        this.gp = gp;
        this.camera = camera;
    }

    private Point screenToWorld(MouseEvent e) { return gp.screenToWorld(e.getX(), e.getY()); }

    private void updateHover(MouseEvent e) { hoverScreenCoords = e == null ? null : new Point(e.getX(), e.getY()); }

    @Override
    public void mouseClicked(MouseEvent e) {}

    @Override
    public void mousePressed(MouseEvent e) {
        updateHover(e);
        startCoords = screenToWorld(e);
        endCoords = startCoords;
        if (e.getButton() == MouseEvent.BUTTON1 && gp.hasPendingStructureBuild()) {
            gp.tryBuildStructure(startCoords);
            gp.repaint();
            return;
        }
        switch (e.getButton()) {
        case MouseEvent.BUTTON1 -> leftPressed = true;
        case MouseEvent.BUTTON2 -> middlePressed = true;
        default -> {
            rightPressed = true;
            formationOrderActive = e.isControlDown();
            lockedFormationFacingAngle = null;
        }
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        updateHover(e);
        if (leftPressed && !rightPressed && !middlePressed) {
            drawing = true;
            endCoords = screenToWorld(e);
            gp.repaint();
        }
        if (!leftPressed && rightPressed && !middlePressed) {
            endCoords = screenToWorld(e);
            if (formationOrderActive) {
                lockFormationFacingIfNeeded();
                gp.repaint();
            }
        }
        if (!leftPressed && !rightPressed && middlePressed) {
            endCoords = screenToWorld(e);
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        updateHover(e);
        endCoords = screenToWorld(e);
        switch (e.getButton()) {
        case MouseEvent.BUTTON1 -> {
            leftPressed = false;
            findUnits();
        }
        case MouseEvent.BUTTON2 -> {
            middlePressed = false;
            manualMoveUnits();
        }
        default -> {
            rightPressed = false;
            if (formationOrderActive) {
                moveUnitsInFormation();
            } else {
                moveUnits();
            }
            formationOrderActive = false;
            lockedFormationFacingAngle = null;
        }
        }
        drawing = false;
        gp.repaint();
    }

    @Override
    public void mouseMoved(MouseEvent e) { updateHover(e); }

    @Override
    public void mouseExited(MouseEvent e) { hoverScreenCoords = null; }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        double oldZoom = camera.getZoomLevel();
        double wheel = e.getWheelRotation();
        double change = camera.getZoomChange();
        double newZoom = oldZoom;
        if (wheel < 0) {
            newZoom = oldZoom + change;
        } else if (wheel > 0) {
            newZoom = oldZoom - change;
        }
        if (newZoom < camera.getMinZoom())
            newZoom = camera.getMinZoom();
        if (newZoom > camera.getMaxZoom())
            newZoom = camera.getMaxZoom();
        Point viewportPoint = gp.screenToViewport(e.getX(), e.getY());
        double mouseViewportX = viewportPoint.getX();
        double mouseViewportY = viewportPoint.getY();
        double worldX = mouseViewportX / oldZoom + camera.getX();
        double worldY = mouseViewportY / oldZoom + camera.getY();
        camera.setZoomLevel(newZoom);
        double newCameraX = worldX - mouseViewportX / newZoom;
        double newCameraY = worldY - mouseViewportY / newZoom;
        camera.getCoords().setLocation((int)newCameraX, (int)newCameraY);
    }

    private void findUnits() {
        double lowestX = Math.min(startCoords.x, endCoords.x);
        double lowestY = Math.min(startCoords.y, endCoords.y);
        double highestX = Math.max(startCoords.x, endCoords.x);
        double highestY = Math.max(startCoords.y, endCoords.y);
        double width = highestX - lowestX;
        double height = highestY - lowestY;
        gp.updateSelectedUnits(new Rectangle2D.Double(lowestX, lowestY, width, height));
    }

    private void moveUnits() {
        List<Unit> selectedUnits = getSelectedUnits();
        Unit engageTarget = findEnemyUnitAt(endCoords, selectedUnits);
        if (engageTarget != null) {
            issueEngageOrders(selectedUnits, engageTarget);
            return;
        }
        issueMoveOrders(selectedUnits, true);
    }

    private void moveUnitsInFormation() {
        List<Unit> selectedUnits = getSelectedUnits();
        if (selectedUnits.isEmpty()) {
            return;
        }
        double pathLength = getFormationPathLength();
        Double formationFacingAngle = getFormationFacingAngle();
        if (pathLength < GamePanel.TILE_SIZE * 0.5 || selectedUnits.size() == 1) {
            Point fallbackTarget = new Point((startCoords.x + endCoords.x) / 2, (startCoords.y + endCoords.y) / 2);
            for (Unit unit : selectedUnits) {
                unit.setTargetCoordsComputePath(fallbackTarget, gp.getTileManager().getTileMap());
                if (formationFacingAngle != null) {
                    unit.setDesiredFacingAngleRadians(formationFacingAngle);
                }
                unit.setSelected(false);
            }
            return;
        }
        Point firstPoint = startCoords;
        Point lastPoint = endCoords;
        double directionX = lastPoint.x * 1.0 - firstPoint.x;
        double directionY = lastPoint.y * 1.0 - firstPoint.y;
        double directionLength = Math.hypot(directionX, directionY);
        double normalizedDirectionX = 0.0;
        double normalizedDirectionY = 0.0;
        if (directionLength >= 1e-6) {
            normalizedDirectionX = directionX / directionLength;
            normalizedDirectionY = directionY / directionLength;
        }
        final double sortDirectionX = normalizedDirectionX;
        final double sortDirectionY = normalizedDirectionY;
        selectedUnits
                .sort(Comparator.comparingDouble(unit -> unit.getX() * sortDirectionX + unit.getY() * sortDirectionY));
        List<Double> slotDistances = computeFormationSlotDistances(selectedUnits, pathLength);
        for (int index = 0; index < selectedUnits.size(); index++) {
            double slotDistance = slotDistances.get(index);
            Point targetPoint = getPointAlongFormationPath(slotDistance);
            Unit unit = selectedUnits.get(index);
            unit.setTargetCoordsComputePath(targetPoint, gp.getTileManager().getTileMap());
            if (formationFacingAngle != null) {
                unit.setDesiredFacingAngleRadians(formationFacingAngle);
            }
            unit.setSelected(false);
        }
    }

    private List<Double> computeFormationSlotDistances(List<Unit> selectedUnits, double pathLength) {
        List<Double> slotDistances = new ArrayList<>();
        double[] footprints = new double[selectedUnits.size()];
        double totalRequiredLength = 0.0;
        for (int index = 0; index < selectedUnits.size(); index++) {
            footprints[index] = Math.max(GamePanel.TILE_SIZE * 0.6, selectedUnits.get(index).getFormationWidth());
            totalRequiredLength += footprints[index];
        }
        if (selectedUnits.size() > 1) {
            totalRequiredLength += FORMATION_UNIT_GAP * (selectedUnits.size() - 1);
        }
        double compression = 1.0;
        double safeRequiredLength = Math.max(totalRequiredLength, 1e-6);
        if (totalRequiredLength > 1e-6 && totalRequiredLength > pathLength) {
            compression = pathLength / safeRequiredLength;
        }
        double currentDistance = Math.max(0.0, (pathLength - totalRequiredLength * compression) * 0.5);
        for (int index = 0; index < selectedUnits.size(); index++) {
            currentDistance += footprints[index] * 0.5 * compression;
            slotDistances.add(Math.clamp(currentDistance, 0.0, pathLength));
            currentDistance += footprints[index] * 0.5 * compression;
            if (index < selectedUnits.size() - 1) {
                currentDistance += FORMATION_UNIT_GAP * compression;
            }
        }
        return slotDistances;
    }

    private double getFormationPathLength() {
        return startCoords == null || endCoords == null ? 0.0 : startCoords.distance(endCoords);
    }

    private Point getPointAlongFormationPath(double distanceAlongPath) {
        if (startCoords == null || endCoords == null) {
            return endCoords;
        }
        double totalLength = getFormationPathLength();
        if (totalLength < 1e-6) {
            return endCoords;
        }
        double t;
        if (distanceAlongPath <= 0.0) {
            t = 0.0;
        } else if (totalLength <= 0.0 || distanceAlongPath >= totalLength) {
            t = 1.0;
        } else {
            t = distanceAlongPath / totalLength;
        }
        int targetX = (int)Math.round(startCoords.x + (endCoords.x - startCoords.x) * t);
        int targetY = (int)Math.round(startCoords.y + (endCoords.y - startCoords.y) * t);
        return new Point(targetX, targetY);
    }

    private Double getFormationFacingAngle() {
        if (startCoords == null || endCoords == null) {
            return null;
        }
        if (lockedFormationFacingAngle != null) {
            return lockedFormationFacingAngle;
        }
        return resolveFormationFacingAngle(startCoords, endCoords);
    }

    private void lockFormationFacingIfNeeded() {
        if (lockedFormationFacingAngle != null || startCoords == null || endCoords == null) {
            return;
        }
        if (startCoords.distance(endCoords) < FORMATION_DIRECTION_LOCK_DISTANCE) {
            return;
        }
        lockedFormationFacingAngle = resolveFormationFacingAngle(startCoords, endCoords);
    }

    public static Double resolveFormationFacingAngle(Point startPoint, Point endPoint) {
        if (startPoint == null || endPoint == null) {
            return null;
        }
        double directionX = endPoint.x * 1.0 - startPoint.x;
        double directionY = endPoint.y * 1.0 - startPoint.y;
        if (Math.hypot(directionX, directionY) < 1e-6) {
            return null;
        }
        if (Math.abs(directionX) >= Math.abs(directionY)) {
            return directionX >= 0.0 ? -Math.PI * 0.5 : Math.PI * 0.5;
        }
        return directionY >= 0.0 ? 0.0 : Math.PI;
    }

    private List<Unit> getSelectedUnits() {
        List<Unit> selectedUnits = new ArrayList<>();
        for (Unit unit : UnitManager.getUnitsSnapshot()) {
            if (unit.isSelected()) {
                selectedUnits.add(unit);
            }
        }
        return selectedUnits;
    }

    private void manualMoveUnits() { issueMoveOrders(getSelectedUnits(), false); }

    private void issueMoveOrders(List<Unit> selectedUnits, boolean usePathfinding) {
        if (selectedUnits.isEmpty() || endCoords == null) {
            return;
        }
        TileMap tileMap = gp.getTileManager().getTileMap();
        List<Point> targetPoints = resolveCompactMoveTargets(selectedUnits, endCoords, tileMap);
        for (int index = 0; index < selectedUnits.size(); index++) {
            Unit unit = selectedUnits.get(index);
            Point targetPoint = targetPoints.get(index);
            if (usePathfinding) {
                unit.setTargetCoordsComputePath(targetPoint, tileMap);
            } else {
                unit.setTargetCoords(targetPoint);
                unit.setMoving(true);
            }
            unit.setSelected(false);
        }
    }

    private void issueEngageOrders(List<Unit> selectedUnits, Unit targetUnit) {
        if (selectedUnits.isEmpty() || targetUnit == null) {
            return;
        }
        TileMap tileMap = gp.getTileManager().getTileMap();
        for (Unit unit : selectedUnits) {
            unit.setEngagementTarget(targetUnit, tileMap);
            unit.setSelected(false);
        }
    }

    private Unit findEnemyUnitAt(Point worldPoint, List<Unit> selectedUnits) {
        if (worldPoint == null || selectedUnits.isEmpty()) {
            return null;
        }
        Rectangle2D clickArea = new Rectangle2D.Double(worldPoint.x - 2.0, worldPoint.y - 2.0, 4.0, 4.0);
        Unit closestEnemy = null;
        double bestDistance = Double.MAX_VALUE;
        Unit.SoldierCulture selectedCulture = selectedUnits.get(0).getSoldierCulture();
        for (Unit unit : UnitManager.getUnitsSnapshot()) {
            if (unit == null || unit.getHealth() <= 0 || unit.hasSurrendered()
                    || unit.getSoldierCulture() == selectedCulture || !unit.intersects(clickArea)) {
                continue;
            }
            double distance = unit.getCoords().distance(worldPoint);
            if (distance < bestDistance) {
                bestDistance = distance;
                closestEnemy = unit;
            }
        }
        return closestEnemy;
    }

    public static List<Point> resolveCompactMoveTargets(List<Unit> units, Point centerPoint, TileMap map) {
        List<Point> targets = new ArrayList<>();
        if (units == null || units.isEmpty() || centerPoint == null) {
            return targets;
        }
        if (units.size() == 1) {
            targets.add(new Point(centerPoint));
            return targets;
        }
        double slotWidth = GamePanel.TILE_SIZE;
        double slotHeight = GamePanel.TILE_SIZE;
        for (Unit unit : units) {
            slotWidth = Math.max(slotWidth, unit.getFormationWidth() + COMPACT_MOVE_SLOT_GAP);
            slotHeight = Math.max(slotHeight, unit.getFormationHeight() + COMPACT_MOVE_SLOT_GAP);
        }
        List<Point> candidateTargets = buildCompactMoveCandidates(centerPoint, map, units.size(), slotWidth,
                slotHeight);
        return assignNearestTargets(units, candidateTargets);
    }

    private static List<Point> buildCompactMoveCandidates(Point centerPoint, TileMap map, int requiredCount,
            double slotWidth, double slotHeight) {
        List<Point> candidateTargets = new ArrayList<>();
        Set<Long> seenTargets = new HashSet<>();
        int maxRadius = map != null ? Math.max(map.getWidth(), map.getHeight()) : Math.max(4, requiredCount);
        for (int radius = 0; radius <= maxRadius && candidateTargets.size() < requiredCount; radius++) {
            addCompactMoveCandidateRing(centerPoint, map, requiredCount, slotWidth, slotHeight, radius, true,
                    candidateTargets, seenTargets);
        }
        for (int radius = 0; radius <= maxRadius && candidateTargets.size() < requiredCount; radius++) {
            addCompactMoveCandidateRing(centerPoint, map, requiredCount, slotWidth, slotHeight, radius, false,
                    candidateTargets, seenTargets);
        }
        if (candidateTargets.isEmpty()) {
            candidateTargets.add(new Point(centerPoint));
        }
        return candidateTargets;
    }

    private static void addCompactMoveCandidateRing(Point centerPoint, TileMap map, int requiredCount, double slotWidth,
            double slotHeight, int radius, boolean requirePassable, List<Point> candidateTargets,
            Set<Long> seenTargets) {
        for (int rowOffset = -radius; rowOffset <= radius; rowOffset++) {
            for (int colOffset = -radius; colOffset <= radius; colOffset++) {
                if (Math.max(Math.abs(rowOffset), Math.abs(colOffset)) != radius) {
                    continue;
                }
                Point candidate = new Point((int)Math.round(centerPoint.x + colOffset * slotWidth),
                        (int)Math.round(centerPoint.y + rowOffset * slotHeight));
                if (!isUsableMoveTarget(candidate, map, requirePassable)) {
                    continue;
                }
                long key = (((long)candidate.x) << 32) ^ (candidate.y & 0xffffffffL);
                if (!seenTargets.add(key)) {
                    continue;
                }
                candidateTargets.add(candidate);
                if (candidateTargets.size() >= requiredCount) {
                    return;
                }
            }
        }
    }

    private static boolean isUsableMoveTarget(Point candidate, TileMap map, boolean requirePassable) {
        if (candidate == null || map == null) {
            return candidate != null;
        }
        int worldWidth = map.getWidth() * GamePanel.TILE_SIZE;
        int worldHeight = map.getHeight() * GamePanel.TILE_SIZE;
        if (candidate.x < 0 || candidate.y < 0 || candidate.x >= worldWidth || candidate.y >= worldHeight) {
            return false;
        }
        if (!requirePassable) {
            return true;
        }
        int col = Math.clamp(candidate.x / GamePanel.TILE_SIZE, 0, map.getWidth() - 1);
        int row = Math.clamp(candidate.y / GamePanel.TILE_SIZE, 0, map.getHeight() - 1);
        return map.isPassable(col, row);
    }

    private static List<Point> assignNearestTargets(List<Unit> units, List<Point> candidateTargets) {
        List<Point> remainingTargets = new ArrayList<>(candidateTargets);
        List<Point> assignments = new ArrayList<>();
        for (Unit unit : units) {
            Point assignedTarget = findNearestTarget(unit, remainingTargets);
            if (assignedTarget == null) {
                assignedTarget = new Point(unit.getX(), unit.getY());
            }
            assignments.add(assignedTarget);
            remainingTargets.remove(assignedTarget);
        }
        return assignments;
    }

    private static Point findNearestTarget(Unit unit, List<Point> remainingTargets) {
        Point nearestTarget = null;
        double bestDistance = Double.MAX_VALUE;
        for (Point candidate : remainingTargets) {
            double distance = unit.getCoords().distance(candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                nearestTarget = candidate;
            }
        }
        return nearestTarget;
    }

    public Point getStartCoords() { return startCoords; }

    public Point getEndCoords() { return endCoords; }

    public int getStartX() { return startCoords.x; }

    public int getStartY() { return startCoords.y; }

    public int getEndX() { return endCoords.x; }

    public int getEndY() { return endCoords.y; }

    public boolean isDrawing() { return drawing; }

    public boolean isFormationDrawing() {
        return formationOrderActive && rightPressed && startCoords != null && endCoords != null;
    }

    public List<Point> getFormationPoints() {
        List<Point> previewPoints = new ArrayList<>(2);
        if (startCoords != null) {
            previewPoints.add(startCoords);
        }
        if (endCoords != null && (startCoords == null || startCoords.distance(endCoords) > 0.5)) {
            previewPoints.add(endCoords);
        }
        return previewPoints;
    }

    public Point getHoverScreenCoords() { return hoverScreenCoords == null ? null : new Point(hoverScreenCoords); }
}
