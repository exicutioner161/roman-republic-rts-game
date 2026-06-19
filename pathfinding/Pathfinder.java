package pathfinding;

import java.awt.Point;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import units.Unit;
import units.UnitManager;
import world.TileMap;

public class Pathfinder {
    private Pathfinder() {}

    private static class Node {
        int x;
        int y;
        double g;
        double f;
        Node parent;

        Node(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Node)) {
                return false;
            }
            Node n = (Node)o;
            return n.x == x && n.y == y;
        }

        @Override
        public int hashCode() { return x * 31 + y; }
    }

    public static List<Point> findPath(TileMap map, Point start, Point goal, int tileSize, boolean allowDiagonal) {
        return findPath(map, start, goal, tileSize, allowDiagonal, null);
    }

    public static List<Point> findPath(TileMap map, Point start, Point goal, int tileSize, boolean allowDiagonal,
            Unit mover) {
        List<Point> empty = Collections.emptyList();
        if (map == null || start == null || goal == null) {
            return empty;
        }
        int sx = Math.clamp(start.x / tileSize, 0, map.getWidth() - 1);
        int sy = Math.clamp(start.y / tileSize, 0, map.getHeight() - 1);
        int gx = Math.clamp(goal.x / tileSize, 0, map.getWidth() - 1);
        int gy = Math.clamp(goal.y / tileSize, 0, map.getHeight() - 1);
        int w = map.getWidth();
        int h = map.getHeight();
        Comparator<Node> cmp = Comparator.comparingDouble(n -> n.f);
        PriorityQueue<Node> open = new PriorityQueue<>(cmp);
        Map<Integer, Node> openMap = new HashMap<>();
        Set<Integer> closed = new HashSet<>();
        Set<Integer> occupiedTiles = buildOccupiedTileSet(tileSize, mover, gx, gy);
        Node startNode = new Node(sx, sy);
        startNode.g = 0;
        startNode.f = heuristic(sx, sy, gx, gy);
        open.add(startNode);
        openMap.put(key(sx, sy), startNode);
        int[][] dirs = allowDiagonal
                ? new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}}
                : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        Node found = null;
        while (!open.isEmpty()) {
            Node current = open.poll();
            openMap.remove(key(current.x, current.y));
            if (current.x == gx && current.y == gy) {
                found = current;
                break;
            }
            closed.add(key(current.x, current.y));
            for (int[] d : dirs) {
                considerNeighbor(map, mover, occupiedTiles, w, h, current, d, gx, gy, open, openMap, closed);
            }
        }
        if (found == null) {
            return empty;
        }
        List<Point> path = reconstructPath(found, tileSize);
        if (!path.isEmpty() && start.distance(path.get(0)) <= tileSize * 0.75) {
            path.remove(0);
        }
        simplifyPath(path);
        return path;
    }

    private static void considerNeighbor(TileMap map, Unit mover, Set<Integer> occupiedTiles, int w, int h,
            Node current, int[] d, int gx, int gy, PriorityQueue<Node> open, Map<Integer, Node> openMap,
            Set<Integer> closed) {
        int nx = current.x + d[0];
        int ny = current.y + d[1];
        if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
            return;
        }
        if (!map.isPassable(nx, ny)) {
            return;
        }
        int nk = key(nx, ny);
        if ((nx != gx || ny != gy) && occupiedTiles.contains(nk)) {
            return;
        }
        if (closed.contains(nk)) {
            return;
        }
        double factor = (Math.abs(d[0]) + Math.abs(d[1]) == 2) ? 1.414 : 1.0;
        double moveCost = map.getTraversalCost(nx, ny, mover != null ? mover.getSoldierClass() : null) * factor;
        double tentativeG = current.g + moveCost;
        Node neighbor = openMap.get(nk);
        if (neighbor == null) {
            neighbor = new Node(nx, ny);
            neighbor.g = tentativeG;
            neighbor.f = tentativeG + heuristic(nx, ny, gx, gy);
            neighbor.parent = current;
            open.add(neighbor);
            openMap.put(nk, neighbor);
        } else if (tentativeG < neighbor.g) {
            neighbor.g = tentativeG;
            neighbor.f = tentativeG + heuristic(nx, ny, gx, gy);
            neighbor.parent = current;
            open.remove(neighbor);
            open.add(neighbor);
        }
    }

    private static List<Point> reconstructPath(Node found, int tileSize) {
        List<Point> rev = new ArrayList<>();
        Node cur = found;
        while (cur != null) {
            int wx = cur.x * tileSize + tileSize / 2;
            int wy = cur.y * tileSize + tileSize / 2;
            rev.add(new Point(wx, wy));
            cur = cur.parent;
        }
        Collections.reverse(rev);
        return rev;
    }

    private static void simplifyPath(List<Point> path) {
        if (path.size() < 3) {
            return;
        }
        int index = 1;
        while (index < path.size() - 1) {
            Point previous = path.get(index - 1);
            Point current = path.get(index);
            Point next = path.get(index + 1);
            int dx1 = Integer.compare(current.x - previous.x, 0);
            int dy1 = Integer.compare(current.y - previous.y, 0);
            int dx2 = Integer.compare(next.x - current.x, 0);
            int dy2 = Integer.compare(next.y - current.y, 0);
            if (dx1 == dx2 && dy1 == dy2) {
                path.remove(index);
            } else {
                index++;
            }
        }
    }

    private static int key(int x, int y) {
        // Packed coordinate key for typical tilemaps (assumes x/y remain within
        // 16-bit unsigned ranges).
        return (x << 16) | (y & 0xFFFF);
    }

    private static Set<Integer> buildOccupiedTileSet(int tileSize, Unit mover, int goalX, int goalY) {
        Set<Integer> occupiedTiles = new HashSet<>();
        int safeTileSize = Math.max(1, tileSize);
        Rectangle2D moverFootprint = mover != null ? mover.getFootprintBounds() : null;
        for (Unit unit : UnitManager.getUnitsSnapshot()) {
            if (unit == null || unit == mover || unit.hasSurrendered() || unit.getHealth() <= 0) {
                continue;
            }
            addOccupiedFootprintTiles(occupiedTiles, unit, safeTileSize, goalX, goalY, mover, moverFootprint);
        }
        return occupiedTiles;
    }

    private static void addOccupiedFootprintTiles(Set<Integer> occupiedTiles, Unit unit, int tileSize, int goalX,
            int goalY, Unit mover, Rectangle2D moverFootprint) {
        Rectangle2D footprint = unit.getFootprintBounds();
        double searchPaddingX = moverFootprint != null ? moverFootprint.getWidth() * 0.5 : tileSize;
        double searchPaddingY = moverFootprint != null ? moverFootprint.getHeight() * 0.5 : tileSize;
        int minTileX = Math.max(0, tileIndexForMin(footprint.getMinX() - searchPaddingX, tileSize));
        int maxTileX = Math.max(0, tileIndexForMax(footprint.getMaxX() + searchPaddingX, tileSize));
        int minTileY = Math.max(0, tileIndexForMin(footprint.getMinY() - searchPaddingY, tileSize));
        int maxTileY = Math.max(0, tileIndexForMax(footprint.getMaxY() + searchPaddingY, tileSize));
        for (int tileY = minTileY; tileY <= maxTileY; tileY++) {
            for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
                if (tileX == goalX && tileY == goalY) {
                    continue;
                }
                if (occupiesTile(unit, mover, tileX, tileY, tileSize)) {
                    occupiedTiles.add(key(tileX, tileY));
                }
            }
        }
    }

    private static boolean occupiesTile(Unit unit, Unit mover, int tileX, int tileY, int tileSize) {
        if (mover == null) {
            Rectangle2D tileBounds = new Rectangle2D.Double((double)tileX * tileSize, (double)tileY * tileSize,
                    tileSize, tileSize);
            return unit.intersects(tileBounds);
        }
        Point candidateCenter = new Point(tileX * tileSize + tileSize / 2, tileY * tileSize + tileSize / 2);
        return mover.intersectsAt(candidateCenter, unit);
    }

    private static int tileIndexForMin(double coordinate, int tileSize) {
        return (int)Math.floor(coordinate / tileSize);
    }

    private static int tileIndexForMax(double coordinate, int tileSize) {
        return (int)Math.floor(Math.nextAfter(coordinate, Double.NEGATIVE_INFINITY) / tileSize);
    }

    private static double heuristic(int x, int y, int gx, int gy) {
        return (double)Math.abs(x - gx) + Math.abs(y - gy);
    }
}
