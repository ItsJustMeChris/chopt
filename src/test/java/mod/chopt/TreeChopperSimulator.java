package mod.chopt;

import java.util.*;

/**
 * Standalone simulator to validate TreeChopper algorithm logic.
 * Run with: java TreeChopperSimulator.java
 */
public class TreeChopperSimulator {

    // Simulated block types
    enum BlockType { AIR, LOG, STUMP, LEAVES }

    // Simulated world
    static class World {
        private final Map<Pos, BlockType> blocks = new HashMap<>();

        void setBlock(Pos pos, BlockType type) {
            if (type == BlockType.AIR) {
                blocks.remove(pos);
            } else {
                blocks.put(pos, type);
            }
        }

        BlockType getBlock(Pos pos) {
            return blocks.getOrDefault(pos, BlockType.AIR);
        }

        boolean isLog(Pos pos) {
            return getBlock(pos) == BlockType.LOG;
        }

        boolean isStump(Pos pos) {
            return getBlock(pos) == BlockType.STUMP;
        }
    }

    record Pos(int x, int y, int z) {
        Pos above() { return new Pos(x, y + 1, z); }
        Pos offset(int dx, int dy, int dz) { return new Pos(x + dx, y + dy, z + dz); }
    }

    static class Session {
        final Pos base;
        final Map<Pos, BlockType> originals;
        int requiredChops;
        int hits = 0;

        Session(Pos base, Map<Pos, BlockType> originals) {
            this.base = base;
            this.originals = new HashMap<>(originals);
            this.requiredChops = computeRequiredChops(originals.size());
        }

        boolean contains(Pos pos) {
            return pos.equals(base) || originals.containsKey(pos);
        }

        void recordAttempt() { hits++; }
        boolean isComplete() { return hits >= requiredChops; }
        int logsSize() { return originals.size(); }
        BlockType getOriginal(Pos pos) { return originals.get(pos); }

        boolean pruneAndRecalculate(World world) {
            originals.entrySet().removeIf(entry -> {
                Pos pos = entry.getKey();
                if (pos.equals(base)) return false; // Keep stump position
                return !world.isLog(pos);
            });
            if (originals.isEmpty()) return false;
            requiredChops = computeRequiredChops(originals.size());
            return true;
        }

        static int computeRequiredChops(int logCount) {
            double value = 0.12 * logCount + 2.0 * Math.log1p(logCount);
            int chops = (int) Math.ceil(value);
            chops = Math.max(1, Math.min(logCount, chops));
            return Math.min(chops, 32);
        }
    }

    // Simulation state
    static World world;
    static Map<Pos, Session> sessions = new HashMap<>();
    static List<Pos> droppedItems = new ArrayList<>();
    static int axeDurability;
    static boolean isCreative;

    // Test harness
    static int testsRun = 0;
    static int testsPassed = 0;
    static List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        System.out.println("=== TreeChopper Algorithm Simulator ===\n");

        testNormalCompletionHittingStump();
        testCompletionHittingDifferentLog();
        testManualBreaksDuringSession();
        testAxeBreaksMidSession();
        testAxeBreaksDuringTimber();
        testShiftBreakStump();
        testMultipleTreesNoInterference();
        testSingleLogTree();
        testLargeTree();
        testResumeAfterServerRestart();

        System.out.println("\n=== Results ===");
        System.out.println("Tests run: " + testsRun);
        System.out.println("Tests passed: " + testsPassed);
        System.out.println("Tests failed: " + (testsRun - testsPassed));

        if (!failures.isEmpty()) {
            System.out.println("\nFailures:");
            failures.forEach(f -> System.out.println("  - " + f));
        }

        System.exit(failures.isEmpty() ? 0 : 1);
    }

    static void reset() {
        world = new World();
        sessions.clear();
        droppedItems.clear();
        axeDurability = 100;
        isCreative = false;
    }

    static void buildTree(Pos base, int height) {
        for (int y = 0; y < height; y++) {
            world.setBlock(new Pos(base.x, base.y + y, base.z), BlockType.LOG);
        }
        // Add leaves around top
        Pos top = new Pos(base.x, base.y + height - 1, base.z);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    world.setBlock(top.offset(dx, 0, dz), BlockType.LEAVES);
                    world.setBlock(top.offset(dx, 1, dz), BlockType.LEAVES);
                }
            }
        }
    }

    static Session findSession(Pos pos) {
        for (Session s : sessions.values()) {
            if (s.contains(pos)) return s;
        }
        return null;
    }

    static Map<Pos, BlockType> scanLogs(Pos origin) {
        Map<Pos, BlockType> logs = new HashMap<>();
        Deque<Pos> queue = new ArrayDeque<>();
        queue.add(origin);

        while (!queue.isEmpty() && logs.size() < 256) {
            Pos current = queue.removeFirst();
            if (logs.containsKey(current)) continue;
            if (!world.isLog(current)) continue;

            logs.put(current, BlockType.LOG);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Pos next = current.offset(dx, dy, dz);
                        if (!logs.containsKey(next) && world.isLog(next)) {
                            queue.add(next);
                        }
                    }
                }
            }
        }
        return logs;
    }

    static Pos findBase(Map<Pos, BlockType> logs) {
        return logs.keySet().stream()
            .min(Comparator.comparingInt((Pos p) -> p.y)
                .thenComparingInt(p -> p.x)
                .thenComparingInt(p -> p.z))
            .orElse(null);
    }

    static Session buildSession(Pos origin, Pos baseOverride) {
        if (!world.isLog(origin)) return null;
        Map<Pos, BlockType> logs = scanLogs(origin);
        if (logs.isEmpty()) return null;
        Pos base = baseOverride != null ? baseOverride : findBase(logs);
        return new Session(base, logs);
    }

    static void dropResource(Pos pos, BlockType original) {
        droppedItems.add(pos);
    }

    static void applyDurabilityLoss(int amount) {
        if (!isCreative) {
            axeDurability -= amount;
        }
    }

    // Core algorithm simulation (mirrors TreeChopper.beforeBreak)
    static boolean hitBlockWithAxe(Pos pos) {
        boolean isLog = world.isLog(pos);
        boolean isStump = world.isStump(pos);

        if (!isLog && !isStump) {
            Session existing = findSession(pos);
            if (existing != null) {
                sessions.remove(existing.base);
            }
            return true; // allow vanilla break
        }

        Session session = findSession(pos);
        if (session == null) {
            Pos scanOrigin = pos;
            Pos baseOverride = null;
            if (isStump) {
                baseOverride = pos;
                scanOrigin = pos.above();
            }
            session = buildSession(scanOrigin, baseOverride);
            if (session == null) return true;
            sessions.put(session.base, session);
        }

        // Prune manually broken logs
        if (!session.pruneAndRecalculate(world)) {
            world.setBlock(session.base, BlockType.AIR);
            sessions.remove(session.base);
            return false;
        }

        applyDurabilityLoss(1);
        boolean brokeAxe = axeDurability <= 0;

        session.recordAttempt();

        // Place stump visual
        world.setBlock(session.base, BlockType.STUMP);

        if (brokeAxe) {
            return false; // stump stays for next tool
        }

        if (!session.isComplete()) {
            return false; // keep hitting
        }

        // Final chop - drop the hit log
        BlockType original = session.getOriginal(pos);
        if (original != null) {
            dropResource(pos, original);
        }
        world.setBlock(pos, BlockType.AIR);

        // Fell remaining logs
        int felled = chopRemainingWithDurability(session, pos);

        // Remove stump if it still exists (completion by hitting different log)
        Pos stumpPos = session.base;
        if (world.isStump(stumpPos)) {
            BlockType stumpOriginal = session.getOriginal(stumpPos);
            if (stumpOriginal != null) {
                dropResource(stumpPos, stumpOriginal);
            }
            world.setBlock(stumpPos, BlockType.AIR);
        }

        sessions.remove(session.base);
        return false;
    }

    static int chopRemainingWithDurability(Session session, Pos alreadyBroken) {
        int felled = 0;
        for (Pos pos : session.originals.keySet()) {
            if (pos.equals(alreadyBroken)) continue;

            // Skip if already removed
            if (!world.isLog(pos)) continue;

            if (!isCreative) {
                if (axeDurability <= 0) break;
                applyDurabilityLoss(1);
                if (axeDurability <= 0) break;
            }

            BlockType original = session.getOriginal(pos);
            dropResource(pos, original);
            world.setBlock(pos, BlockType.AIR);
            felled++;
        }
        return felled;
    }

    static void manualBreakLog(Pos pos) {
        if (world.isLog(pos)) {
            dropResource(pos, BlockType.LOG);
            world.setBlock(pos, BlockType.AIR);
        }
    }

    // === TESTS ===

    static void testNormalCompletionHittingStump() {
        reset();
        buildTree(new Pos(0, 0, 0), 5);

        Pos stumpPos = new Pos(0, 0, 0);
        int expectedLogs = 5;
        int requiredChops = Session.computeRequiredChops(expectedLogs);

        // Hit the base log until complete
        for (int i = 0; i < requiredChops; i++) {
            hitBlockWithAxe(stumpPos);
        }

        // Verify all logs dropped
        assertEq("Normal completion: drops", expectedLogs, droppedItems.size());
        assertEq("Normal completion: stump removed", BlockType.AIR, world.getBlock(stumpPos));
        assertEq("Normal completion: no remaining logs", 0, countLogs());
    }

    static void testCompletionHittingDifferentLog() {
        reset();
        buildTree(new Pos(0, 0, 0), 5);

        Pos basePos = new Pos(0, 0, 0);
        Pos hitPos = new Pos(0, 2, 0); // Hit middle log instead
        int expectedLogs = 5;
        int requiredChops = Session.computeRequiredChops(expectedLogs);

        // Start by hitting base to create session
        hitBlockWithAxe(basePos);

        // Complete by hitting different log
        for (int i = 1; i < requiredChops; i++) {
            hitBlockWithAxe(hitPos);
        }

        // Verify all logs dropped (including the one at stump position)
        assertEq("Different log completion: drops", expectedLogs, droppedItems.size());
        assertEq("Different log completion: stump removed", BlockType.AIR, world.getBlock(basePos));
        assertEq("Different log completion: no remaining logs", 0, countLogs());
    }

    static void testManualBreaksDuringSession() {
        reset();
        buildTree(new Pos(0, 0, 0), 6);

        Pos basePos = new Pos(0, 0, 0);

        // Start chopping
        hitBlockWithAxe(basePos);
        hitBlockWithAxe(basePos);

        // Manually break some logs (simulating another player or explosion)
        manualBreakLog(new Pos(0, 3, 0));
        manualBreakLog(new Pos(0, 4, 0));
        int manualDrops = 2;

        // Continue chopping until complete
        while (sessions.containsKey(basePos)) {
            hitBlockWithAxe(basePos);
        }

        // All logs should be accounted for (no dupes)
        assertEq("Manual breaks: total drops", 6, droppedItems.size());
        assertEq("Manual breaks: no remaining logs", 0, countLogs());
    }

    static void testAxeBreaksMidSession() {
        reset();
        buildTree(new Pos(0, 0, 0), 5);
        axeDurability = 2; // Will break after 2 hits

        Pos basePos = new Pos(0, 0, 0);

        hitBlockWithAxe(basePos); // durability: 2 -> 1
        hitBlockWithAxe(basePos); // durability: 1 -> 0, axe breaks

        // Session should still exist, stump should remain
        assertTrue("Axe break mid-session: session exists", sessions.containsKey(basePos));
        assertTrue("Axe break mid-session: stump remains", world.isStump(basePos));

        // Get new axe and continue
        axeDurability = 100;
        int requiredChops = Session.computeRequiredChops(5);
        for (int i = 2; i < requiredChops; i++) {
            hitBlockWithAxe(basePos);
        }

        assertEq("Axe break mid-session: all drops", 5, droppedItems.size());
    }

    static void testAxeBreaksDuringTimber() {
        reset();
        buildTree(new Pos(0, 0, 0), 10);

        Pos basePos = new Pos(0, 0, 0);
        int requiredChops = Session.computeRequiredChops(10);

        // Hit until almost complete
        for (int i = 0; i < requiredChops - 1; i++) {
            hitBlockWithAxe(basePos);
        }

        // Set low durability so axe breaks during timber
        axeDurability = 3;

        hitBlockWithAxe(basePos); // This triggers timber

        // Some logs should remain (axe broke mid-timber)
        int logsRemaining = countLogs();
        int logsDropped = droppedItems.size();

        assertEq("Axe break during timber: total accounts for 10", 10, logsRemaining + logsDropped);
        assertTrue("Axe break during timber: some logs remain", logsRemaining > 0);
        assertEq("Axe break during timber: stump removed", BlockType.AIR, world.getBlock(basePos));
    }

    static void testShiftBreakStump() {
        reset();
        buildTree(new Pos(0, 0, 0), 5);

        Pos basePos = new Pos(0, 0, 0);

        // Start chopping to create stump
        hitBlockWithAxe(basePos);
        hitBlockWithAxe(basePos);

        assertTrue("Shift break: stump exists", world.isStump(basePos));

        // Simulate shift-break (manual stump removal with partial timber)
        // For simplicity, just verify stump can be removed
        Session session = sessions.get(basePos);
        double progress = (double) session.hits / session.requiredChops;
        int targetLogs = (int) Math.floor(progress * session.logsSize());
        if (progress > 0 && targetLogs == 0) targetLogs = 1;

        assertTrue("Shift break: partial progress yields logs", targetLogs > 0);
    }

    static void testMultipleTreesNoInterference() {
        reset();
        buildTree(new Pos(0, 0, 0), 5);
        buildTree(new Pos(10, 0, 0), 5); // Separate tree

        Pos tree1Base = new Pos(0, 0, 0);
        Pos tree2Base = new Pos(10, 0, 0);

        // Start chopping tree 1
        hitBlockWithAxe(tree1Base);
        hitBlockWithAxe(tree1Base);

        // Start chopping tree 2
        hitBlockWithAxe(tree2Base);

        // Both sessions should exist independently
        assertTrue("Multiple trees: tree1 session", sessions.containsKey(tree1Base));
        assertTrue("Multiple trees: tree2 session", sessions.containsKey(tree2Base));

        // Complete tree 1
        int chops1 = Session.computeRequiredChops(5);
        for (int i = 2; i < chops1; i++) {
            hitBlockWithAxe(tree1Base);
        }

        // Tree 1 done, tree 2 still in progress
        assertFalse("Multiple trees: tree1 session removed", sessions.containsKey(tree1Base));
        assertTrue("Multiple trees: tree2 session still exists", sessions.containsKey(tree2Base));
    }

    static void testSingleLogTree() {
        reset();
        world.setBlock(new Pos(0, 0, 0), BlockType.LOG);
        world.setBlock(new Pos(0, 1, 0), BlockType.LEAVES);

        Pos logPos = new Pos(0, 0, 0);

        hitBlockWithAxe(logPos);

        assertEq("Single log: drops", 1, droppedItems.size());
        assertEq("Single log: removed", BlockType.AIR, world.getBlock(logPos));
    }

    static void testLargeTree() {
        reset();
        // Build a large tree (32+ logs)
        for (int y = 0; y < 40; y++) {
            world.setBlock(new Pos(0, y, 0), BlockType.LOG);
        }
        world.setBlock(new Pos(1, 39, 0), BlockType.LEAVES);

        Pos basePos = new Pos(0, 0, 0);
        int requiredChops = Session.computeRequiredChops(40);

        assertTrue("Large tree: chops capped at 32", requiredChops <= 32);

        for (int i = 0; i < requiredChops; i++) {
            hitBlockWithAxe(basePos);
        }

        assertEq("Large tree: all 40 logs dropped", 40, droppedItems.size());
    }

    static void testResumeAfterServerRestart() {
        reset();
        buildTree(new Pos(0, 0, 0), 5);

        Pos basePos = new Pos(0, 0, 0);

        // Start chopping
        hitBlockWithAxe(basePos);
        hitBlockWithAxe(basePos);

        assertTrue("Resume: stump exists", world.isStump(basePos));

        // Simulate server restart (clear sessions)
        sessions.clear();

        // Hit stump again - should rebuild session
        hitBlockWithAxe(basePos);

        assertTrue("Resume: session rebuilt", sessions.containsKey(basePos));
    }

    // === Test utilities ===

    static int countLogs() {
        int count = 0;
        for (int x = -10; x <= 50; x++) {
            for (int y = 0; y < 50; y++) {
                for (int z = -10; z <= 10; z++) {
                    if (world.isLog(new Pos(x, y, z))) count++;
                }
            }
        }
        return count;
    }

    static void assertEq(String name, Object expected, Object actual) {
        testsRun++;
        if (Objects.equals(expected, actual)) {
            testsPassed++;
            System.out.println("✓ " + name);
        } else {
            failures.add(name + ": expected " + expected + ", got " + actual);
            System.out.println("✗ " + name + ": expected " + expected + ", got " + actual);
        }
    }

    static void assertTrue(String name, boolean condition) {
        assertEq(name, true, condition);
    }

    static void assertFalse(String name, boolean condition) {
        assertEq(name, false, condition);
    }
}
