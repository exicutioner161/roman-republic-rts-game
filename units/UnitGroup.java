package units;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

public class UnitGroup {
    public enum OrderType {
        HOLD, MOVE, ATTACK
    }

    private final List<Unit> members = new ArrayList<>();
    private OrderType currentOrder = OrderType.HOLD;
    private Point orderTarget;

    public UnitGroup() {
    }

    public UnitGroup(List<Unit> units) {
        if (units != null) {
            members.addAll(units);
        }
    }

    public void add(Unit u) {
        members.add(u);
    }

    public void remove(Unit u) {
        members.remove(u);
    }

    public List<Unit> getMembers() {
        return members;
    }

    public void issueMove(Point target, world.TileMap map) {
        this.currentOrder = OrderType.MOVE;
        this.orderTarget = target;
        for (Unit unit : members) {
            unit.setTargetCoordsComputePath(target, map);
        }
    }

    public void issueHold() {
        this.currentOrder = OrderType.HOLD;
        this.orderTarget = null;
        for (Unit unit : members) {
            unit.setMoving(false);
        }
    }

    public OrderType getCurrentOrder() {
        return currentOrder;
    }

    public Point getOrderTarget() {
        return orderTarget;
    }
}
