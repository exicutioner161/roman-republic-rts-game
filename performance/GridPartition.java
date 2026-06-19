package performance;

import java.util.ArrayList;
import java.util.List;
import units.Unit;

// spatial grid partition
public class GridPartition {
    private final int cols;
    private final int rows;
    private final int cellSize;
    private final List<Unit>[][] cells;

    @SuppressWarnings("unchecked")
    public GridPartition(int cols, int rows, int cellSize) {
        this.cols = cols;
        this.rows = rows;
        this.cellSize = cellSize;
        cells = new List[cols][rows];
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                cells[x][y] = new ArrayList<>();
            }
        }
    }

    public void add(Unit u) {
        if (u == null) {
            return;
        }
        int cx = Math.clamp(0, u.getX() / cellSize, cols - 1);
        int cy = Math.clamp(0, u.getY() / cellSize, rows - 1);
        cells[cx][cy].add(u);
    }

    public void remove(Unit u) {
        if (u == null) {
            return;
        }
        int cx = Math.clamp(0, u.getX() / cellSize, cols - 1);
        int cy = Math.clamp(0, u.getY() / cellSize, rows - 1);
        cells[cx][cy].remove(u);
    }

    public List<Unit> queryNearby(int x, int y, int radius) {
        List<Unit> result = new ArrayList<>();
        addNearbyTo(result, x, y, radius);
        return result;
    }

    public void addNearbyTo(List<Unit> result, int x, int y, int radius) {
        if (result == null || radius < 0) {
            return;
        }
        int minCx = Math.clamp((x - radius) / cellSize, 0, cols - 1);
        int maxCx = Math.clamp((x + radius) / cellSize, 0, cols - 1);
        int minCy = Math.clamp((y - radius) / cellSize, 0, rows - 1);
        int maxCy = Math.clamp((y + radius) / cellSize, 0, rows - 1);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cy = minCy; cy <= maxCy; cy++) {
                result.addAll(cells[cx][cy]);
            }
        }
    }

    public void clear() {
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                cells[x][y].clear();
            }
        }
    }
}
