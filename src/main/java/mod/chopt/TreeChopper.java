package mod.chopt;

import mod.chopt.mixin.AxeItemAccessor;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;

/**
 * Tree chopping helper that scales chops based on tree size.
 */
public final class TreeChopper {
	private static final int MAX_LOGS = 256;
	private static final BlockPos[] NEIGHBOR_OFFSETS = buildNeighborOffsets();
	private static final int LEAF_RADIUS = 2;
	private static final BlockPos[] LEAF_OFFSETS = buildLeafOffsets(LEAF_RADIUS);
	private static final Map<UUID, Session> SESSIONS = new HashMap<>();
	private static final ThreadLocal<Boolean> PROCESSING = ThreadLocal.withInitial(() -> false);

	private TreeChopper() {}

	public static void register() {
		PlayerBlockBreakEvents.BEFORE.register(TreeChopper::beforeBreak);
		PlayerBlockBreakEvents.AFTER.register(TreeChopper::afterBreak);
	}

	private static boolean beforeBreak(Level level, Player player, BlockPos pos, BlockState state, /* nullable */ Object blockEntity) {
		if (level.isClientSide()) {
			return true;
		}

		if (PROCESSING.get()) {
			return true;
		}

		if (player.isShiftKeyDown()) {
			dropSession(player, "ignored: sneaking");
			return true; // allow vanilla breaking while crouching
		}

		if (!state.is(BlockTags.LOGS)) {
			dropSession(player, "reset: not a log");
			return true;
		}

		ItemStack held = player.getMainHandItem();
		if (!held.is(ItemTags.AXES)) {
			dropSession(player, "ignored: not an axe");
			return true;
		}

		Session session = SESSIONS.get(player.getUUID());
		if (session == null || !session.contains(pos)) {
			session = buildSession(level, pos);
			if (session == null) {
				msg(player, "scan failed");
				return true;
			}
			SESSIONS.put(player.getUUID(), session);
			msg(player, "tree size " + session.logsSize() + ", chops needed " + session.requiredChops);
		}

		session.recordAttempt();
		applyDurabilityLoss(player, held, 1); // pay a swing immediately so partial attempts still cost durability
		if (held.isEmpty()) {
			dropSession(player, "axe broke");
			return true;
		}
		int remaining = Math.max(0, session.logsSize() - session.hits);
		if (!session.isComplete()) {
			msg(player, "hit " + session.hits + "/" + session.requiredChops + " (logs left " + remaining + ")");
			applyStripVisual(level, pos, state, session);
			return false; // cancel breaking to allow repeated hits on same log
		}

		if (held.isEmpty()) {
			dropSession(player, "axe broke; finish with another tool");
			return true;
		}

		// Final chop: drop unstripped log and fell rest manually to avoid stripped drops
		BlockState original = session.getOriginal(pos);
		if (original != null) {
			// Use the stored original state so drops aren't stripped
			Block.dropResources(original, level, pos, level.getBlockEntity(pos), player, player.getMainHandItem());
		}
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);

		PROCESSING.set(true);
		try {
			int felled = chopRemainingWithDurability(level, player, session, pos, held);
			int remainingLogs = Math.max(0, session.logsSize() - 1 - felled); // exclude the already broken log
			if (remainingLogs > 0) {
				msg(player, "axe broke; " + remainingLogs + " logs remain");
			} else {
				msg(player, "quota reached, timber!");
			}
		} finally {
			PROCESSING.set(false);
		}
		SESSIONS.remove(player.getUUID());
		return false; // we've handled the break and drops ourselves
	}

	private static void afterBreak(Level level, Player player, BlockPos pos, BlockState state, /* nullable */ Object blockEntity) {
		if (level.isClientSide()) {
			return;
		}
		Session session = SESSIONS.get(player.getUUID());
		if (session == null) {
			return;
		}
		// No-op now: final felling handled synchronously in beforeBreak to control drops
		SESSIONS.remove(player.getUUID());
	}

	private static Session buildSession(Level level, BlockPos origin) {
		Map<BlockPos, BlockState> originals = scanLogs(level, origin);
		if (originals.isEmpty()) {
			return null;
		}
		if (!hasLeavesNearby(level, originals)) {
			return null; // likely user-placed logs; avoid timbering
		}
		int requiredChops = computeRequiredChops(originals.size());
		msgOrigin(originals.size(), requiredChops);
		BlockPos base = findBase(originals);
		List<BlockPos> stripOrder = orderLogs(originals, base);
		return new Session(originals, requiredChops, base, stripOrder);
	}

	private static int computeRequiredChops(int logCount) {
		double value = 0.12 * logCount + 2.0 * Math.log1p(logCount);
		int chops = (int) Math.ceil(value);
		chops = Math.max(1, Math.min(logCount, chops));
		return Math.min(chops, 32); // hard cap for sanity
	}

	private static void msgOrigin(int logs, int chops) {
		// logging disabled for release
	}

	private static Map<BlockPos, BlockState> scanLogs(Level level, BlockPos origin) {
		Map<BlockPos, BlockState> originals = new HashMap<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		queue.add(origin);

		while (!queue.isEmpty() && originals.size() < MAX_LOGS) {
			BlockPos current = queue.removeFirst();
			if (originals.containsKey(current)) {
				continue;
			}

			BlockState state = level.getBlockState(current);
				if (!state.is(BlockTags.LOGS)) {
					continue;
				}
				originals.put(current, state);

				for (BlockPos offset : NEIGHBOR_OFFSETS) {
					BlockPos next = current.offset(offset);
					if (!originals.containsKey(next) && level.getBlockState(next).is(BlockTags.LOGS)) {
						queue.add(next);
					}
				}
			}

		return originals;
	}

	private static BlockPos findBase(Map<BlockPos, BlockState> originals) {
		return originals.keySet().stream()
			.min((a, b) -> {
				int cmp = Integer.compare(a.getY(), b.getY());
				if (cmp != 0) return cmp;
				cmp = Integer.compare(a.getX(), b.getX());
				if (cmp != 0) return cmp;
				return Integer.compare(a.getZ(), b.getZ());
			})
			.orElse(null);
	}

	private static List<BlockPos> orderLogs(Map<BlockPos, BlockState> originals, BlockPos base) {
		Comparator<BlockPos> byPos = (a, b) -> {
			int cmp = Integer.compare(a.getY(), b.getY());
			if (cmp != 0) return cmp;
			cmp = Integer.compare(a.getX(), b.getX());
			if (cmp != 0) return cmp;
			return Integer.compare(a.getZ(), b.getZ());
		};
		return originals.keySet().stream()
			.sorted(byPos)
			.collect(Collectors.toList());
	}

	private static int chopRemainingWithDurability(Level level, Player player, Session session, BlockPos alreadyBroken, ItemStack tool) {
		int felled = 0;

		for (Map.Entry<BlockPos, BlockState> entry : session.originals.entrySet()) {
			BlockPos pos = entry.getKey();
			if (pos.equals(alreadyBroken)) continue;

			if (!player.isCreative()) {
				if (tool.isEmpty()) {
					break;
				}
				applyDurabilityLoss(player, tool, 1);
				if (tool.isEmpty()) {
					break;
				}
			}

			BlockState original = entry.getValue();
			Block.dropResources(original, level, pos, level.getBlockEntity(pos), player, player.getMainHandItem());
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			felled++;
		}

		return felled;
	}

	private static void msg(Player player, String text) {
		// chat logging disabled for release
	}

	private static void applyDurabilityLoss(Player player, ItemStack tool, int amount) {
		if (amount <= 0) return;
		if (player.isCreative()) return;
		if (tool.isEmpty()) return;
		tool.hurtAndBreak(amount, player, EquipmentSlot.MAINHAND);
	}

	private static void dropSession(Player player, String reason) {
		SESSIONS.remove(player.getUUID());
		msg(player, reason);
	}

	private static final class Session {
		private final Map<BlockPos, BlockState> originals;
		private final int requiredChops;
		private final BlockPos base;
		private final List<BlockPos> stripOrder;
		private int hits = 0;

		Session(Map<BlockPos, BlockState> originals, int requiredChops, BlockPos base, List<BlockPos> stripOrder) {
			this.originals = originals;
			this.requiredChops = requiredChops;
			this.base = base;
			this.stripOrder = stripOrder;
		}

		boolean contains(BlockPos pos) {
			return originals.containsKey(pos);
		}

		void recordAttempt() {
			hits++;
		}

		int logsSize() {
			return originals.size();
		}

		boolean isComplete() {
			return hits >= requiredChops;
		}

		BlockState getOriginal(BlockPos pos) {
			return originals.get(pos);
		}

		BlockPos getBase() {
			return base;
		}

		int getHits() {
			return hits;
		}

		BlockPos getStripTargetForCurrentHit() {
			if (stripOrder.isEmpty()) {
				return null;
			}
			int idx = Math.min(Math.max(hits - 1, 0), stripOrder.size() - 1);
			return stripOrder.get(idx);
		}
	}

	private static void applyStripVisual(Level level, BlockPos pos, BlockState currentState, Session session) {
		BlockPos targetPos = session.getStripTargetForCurrentHit();
		if (targetPos == null) {
			targetPos = session.getBase();
		}
		if (targetPos == null) {
			targetPos = pos;
		}

		// Prefer the original state for the target so we preserve axis
		BlockState targetState = session.getOriginal(targetPos);
		if (targetState == null) {
			targetState = level.getBlockState(targetPos);
		}

		Block stripped = AxeItemAccessor.getStrippables().get(targetState.getBlock());
		if (stripped == null) {
			return;
		}
			BlockState newState = stripped.defaultBlockState();
			if (targetState.hasProperty(RotatedPillarBlock.AXIS) && newState.hasProperty(RotatedPillarBlock.AXIS)) {
				newState = newState.setValue(RotatedPillarBlock.AXIS, targetState.getValue(RotatedPillarBlock.AXIS));
			}
			if (!newState.equals(targetState)) {
				level.setBlock(targetPos, newState, 11);
			}
		}

	private static boolean hasLeavesNearby(Level level, Map<BlockPos, BlockState> originals) {
		for (BlockPos log : originals.keySet()) {
			for (BlockPos offset : LEAF_OFFSETS) {
				BlockState maybeLeaf = level.getBlockState(log.offset(offset));
				if (maybeLeaf.is(BlockTags.LEAVES)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Build a 26-neighbor cube around origin (excludes 0,0,0) so we catch diagonally
	 * touching logs (e.g., jungle branches) instead of only face-adjacent ones.
	 */
	private static BlockPos[] buildNeighborOffsets() {
		List<BlockPos> offsets = new ArrayList<>(26);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dy == 0 && dz == 0) continue;
					offsets.add(new BlockPos(dx, dy, dz));
				}
			}
		}
		return offsets.toArray(BlockPos[]::new);
	}

	private static BlockPos[] buildLeafOffsets(int radius) {
		List<BlockPos> offsets = new ArrayList<>();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (dx == 0 && dy == 0 && dz == 0) continue;
					offsets.add(new BlockPos(dx, dy, dz));
				}
			}
		}
		return offsets.toArray(BlockPos[]::new);
	}
}
