package world;

public class GameWorld {
    public enum TerrainType {
        FLAT_GRASS("Flat Grass", 0, true, 1.0), HILLY_GRASS("Hilly Grass", 1, true, 1.45),
        IMPASSABLE_MOUNTAIN("Impassable Mountain", 3, false, Double.POSITIVE_INFINITY),
        MOUNTAIN_PASS("Mountain Pass", 2, true, 2.35), RIVER("River", 0, true, 2.9),
        FLAT_FOREST("Flat Forest", 0, true, 1.65), HILLY_FOREST("Hilly Forest", 1, true, 2.15),
        MOUNTAINOUS_FOREST("Mountainous Forest", 2, true, 2.85), MARSHLAND("Marshland", 0, true, 2.55),
        FORESTED_MARSHLAND("Forested Marshland", 0, true, 3.05), FLAT_MUD("Flat Mud", 0, true, 1.55),
        FORESTED_MUD("Forested Mud", 0, true, 2.05), HILLY_MUD("Hilly Mud", 1, true, 1.95);

        private final String displayName;
        private final int heightLevel;
        private final boolean passable;
        private final double baseMoveCost;

        TerrainType(String displayName, int heightLevel, boolean passable, double baseMoveCost) {
            this.displayName = displayName;
            this.heightLevel = heightLevel;
            this.passable = passable;
            this.baseMoveCost = baseMoveCost;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getHeightLevel() {
            return heightLevel;
        }

        public boolean isPassable() {
            return passable;
        }

        public double getBaseMoveCost() {
            return baseMoveCost;
        }

        public boolean isForest() {
            return this == FLAT_FOREST || this == HILLY_FOREST || this == MOUNTAINOUS_FOREST
                    || this == FORESTED_MARSHLAND || this == FORESTED_MUD;
        }

        public boolean isRiver() {
            return this == RIVER;
        }

        public boolean isWetland() {
            return this == MARSHLAND || this == FORESTED_MARSHLAND;
        }

        public boolean isMudTerrain() {
            return this == FLAT_MUD || this == FORESTED_MUD || this == HILLY_MUD;
        }

        public boolean isMountainTerrain() {
            return this == IMPASSABLE_MOUNTAIN || this == MOUNTAIN_PASS || this == MOUNTAINOUS_FOREST;
        }

        public boolean isHillTerrain() {
            return this == HILLY_GRASS || this == HILLY_FOREST || this == HILLY_MUD;
        }

        public boolean isFlatOpenTerrain() {
            return this == FLAT_GRASS;
        }

        public boolean isElevated() {
            return heightLevel > 0;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public enum StructureType {
        NONE("None", true, 1.0, 1.0), BRIDGE("Bridge", true, 0.48, 1.0), FORT("Fort", true, 1.12, 0.78),
        WALL("Wall", true, 1.42, 0.72);

        private final String displayName;
        private final boolean passable;
        private final double traversalMultiplier;
        private final double defenderDamageMultiplier;

        StructureType(String displayName, boolean passable, double traversalMultiplier,
                double defenderDamageMultiplier) {
            this.displayName = displayName;
            this.passable = passable;
            this.traversalMultiplier = traversalMultiplier;
            this.defenderDamageMultiplier = defenderDamageMultiplier;
        }

        public String getDisplayName() {
            return displayName;
        }

        public boolean isPassable() {
            return passable;
        }

        public double getTraversalMultiplier() {
            return traversalMultiplier;
        }

        public double getDefenderDamageMultiplier() {
            return defenderDamageMultiplier;
        }

        public boolean isBridge() {
            return this == BRIDGE;
        }

        public boolean isFort() {
            return this == FORT;
        }

        public boolean isWall() {
            return this == WALL;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public GameWorld() {
    }
}
