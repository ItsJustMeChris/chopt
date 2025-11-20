package mod.chopt;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import mod.chopt.block.ShrinkingStumpBlock;
import mod.chopt.block.ShrinkingStumpBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tree chopping helper that scales chops based on tree size.
 */
public final class TreeChopper {
	private static final int MAX_LOGS = 256;
	private static final BlockPos[] NEIGHBOR_OFFSETS = buildNeighborOffsets();
	private static final int LEAF_RADIUS = 2;
	private static final BlockPos[] LEAF_OFFSETS = buildLeafOffsets(LEAF_RADIUS);
	private static final Map<SessionKey, Session> SESSIONS = new HashMap<>();
	private static final ThreadLocal<Boolean> PROCESSING = ThreadLocal.withInitial(() -> false);

	private TreeChopper() {}

	public static void register() {
		PlayerBlockBreakEvents.BEFORE.register(TreeChopper::beforeBreak);
		PlayerBlockBreakEvents.AFTER.register(TreeChopper::afterBreak);
	}

	public static Inspection inspect(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		boolean isStump = state.is(ChoptBlocks.SHRINKING_STUMP);
		if (!state.is(BlockTags.LOGS) && !isStump) {
			return Inspection.notTree(pos);
		}

		Session session = findSession(level, pos);
		if (session != null) {
			return new Inspection(true, session.logsSize(), session.requiredChops, session.hits(), pos);
		}

		BlockPos scanOrigin = isStump ? pos.above() : pos;
		Map<BlockPos, BlockState> originals = scanLogs(level, scanOrigin);
		if (originals.isEmpty() || !hasLeavesNearby(level, originals)) {
			return Inspection.notTree(pos);
		}
		int requiredChops = computeRequiredChops(originals.size());
		return new Inspection(true, originals.size(), requiredChops, 0, pos);
	}

	private static boolean beforeBreak(Level level, Player player, BlockPos pos, BlockState state, /* nullable */ Object blockEntity) {
		if (level.isClientSide()) {
			return true;
		}

		if (PROCESSING.get()) {
			return true;
		}

		if (player.isShiftKeyDown()) {
			msg(player, "ignored: sneaking");
			return true; // allow vanilla breaking while crouching
		}

		boolean isLog = state.is(BlockTags.LOGS);
		boolean isStump = state.is(ChoptBlocks.SHRINKING_STUMP);
		if (!isLog && !isStump) {
			Session existing = findSession(level, pos);
			if (existing != null) {
				SESSIONS.remove(existing.key());
			}
			msg(player, "reset: not a log");
			return true;
		}

		ItemStack held = player.getMainHandItem();
		if (!held.is(ItemTags.AXES)) {
			msg(player, "ignored: not an axe");
			return true;
		}

		Session session = findSession(level, pos);
		if (session == null) {
			BlockPos scanOrigin = pos;
			BlockPos baseOverride = null;
			if (isStump) {
				baseOverride = pos;           // keep stump location anchored
				scanOrigin = pos.above();      // actual logs sit above the stump
			}
			session = buildSession(level, scanOrigin, baseOverride);
			if (session == null) {
				msg(player, "scan failed");
				return true;
			}
			SESSIONS.put(session.key(), session);
			msg(player, "tree size " + session.logsSize() + ", chops needed " + session.requiredChops);
		}

		applyDurabilityLoss(player, held, 1); // pay a swing immediately so partial attempts still cost durability
		if (held.isEmpty()) {
			msg(player, "axe broke");
			return true;
		}

		session.recordAttempt();
		updateStumpVisual(level, pos, session);
		int remaining = Math.max(0, session.logsSize() - session.hits());
		if (!session.isComplete()) {
			msg(player, "hit " + session.hits() + "/" + session.requiredChops + " (logs left " + remaining + ")");
			return false; // cancel breaking to allow repeated hits on same log
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
		SESSIONS.remove(session.key());
		return false; // we've handled the break and drops ourselves
	}

	private static void afterBreak(Level level, Player player, BlockPos pos, BlockState state, /* nullable */ Object blockEntity) {
		// No-op: drops and cleanup handled in beforeBreak
	}

	private static Session buildSession(Level level, BlockPos origin, /* nullable */ BlockPos baseOverride) {
		BlockState originState = level.getBlockState(origin);
		if (!originState.is(BlockTags.LOGS)) {
			return null;
		}

		Map<BlockPos, BlockState> originals = scanLogs(level, origin);
		if (originals.isEmpty()) {
			return null;
		}
		if (!hasLeavesNearby(level, originals)) {
			return null; // likely user-placed logs; avoid timbering
		}
		BlockPos base = baseOverride != null ? baseOverride : findBase(originals);
		if (base == null) {
			return null;
		}
		int requiredChops = computeRequiredChops(originals.size());
		int stumpStages = computeStumpStages(originals.size());
		msgOrigin(originals.size(), requiredChops);
		SessionKey key = new SessionKey(level.dimension(), base.immutable());
		return new Session(key, originals, requiredChops, stumpStages);
	}

	public static int computeRequiredChops(int logCount) {
		double value = 0.12 * logCount + 2.0 * Math.log1p(logCount);
		int chops = (int) Math.ceil(value);
		chops = Math.max(1, Math.min(logCount, chops));
		return Math.min(chops, 32); // hard cap for sanity
	}

	public static int computeStumpStages(int logCount) {
		double value = 0.08 * logCount + 1.5 * Math.log1p(logCount);
		int stages = (int) Math.ceil(value);
		return Mth.clamp(stages, 2, ShrinkingStumpBlock.MAX_STAGE);
	}

	private static int computeStumpStage(Session session) {
		if (session.requiredChops == 0) return 0;
		double ratio = (double) session.hits() / (double) session.requiredChops;
		int stage = (int) Math.ceil(ratio * session.stumpStages());
		return Mth.clamp(stage, 1, session.stumpStages());
	}

	private static void updateStumpVisual(Level level, BlockPos pos, Session session) {
		if (level.isClientSide()) return;
		BlockPos stumpPos = session.key().base();
		BlockState resolved = session.getOriginal(stumpPos);
		if (resolved == null) {
			resolved = session.getOriginal(pos);
		}
		if (resolved == null) {
			resolved = session.anyOriginal();
		}
		if (resolved == null) return;

		final BlockState original = resolved;
		BlockState stripped = StripHelper.getStripped(original)
			.map(state -> ShrinkingStumpBlockEntity.copyAxis(original, state))
			.orElse(original);

		int stage = computeStumpStage(session);
		BlockState stumpState = ChoptBlocks.SHRINKING_STUMP.defaultBlockState()
			.setValue(ShrinkingStumpBlock.STAGES, session.stumpStages())
			.setValue(ShrinkingStumpBlock.STAGE, stage);
		BlockState previousState = level.getBlockState(stumpPos);
		level.setBlock(stumpPos, stumpState, Block.UPDATE_CLIENTS);
		if (level.getBlockEntity(stumpPos) instanceof ShrinkingStumpBlockEntity stump) {
			stump.setDisplayState(stripped);
			stump.setChanged();
			level.sendBlockUpdated(stumpPos, previousState, stumpState, Block.UPDATE_CLIENTS);
			level.blockEntityChanged(stumpPos);
			if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
				ChoptNetworking.syncShrinkingStump(serverLevel, stumpPos, stripped);
			}
		}
	}

	/**
	 * Accesses AxeItem's protected strippables map via subclassing to avoid mixins.
	 */
	private static final class StripHelper extends AxeItem {
		private StripHelper() {
			super(null, 0.0F, 0.0F, new Properties());
		}

		static java.util.Optional<BlockState> getStripped(BlockState state) {
			Block target = STRIPPABLES.get(state.getBlock());
			if (target == null) return java.util.Optional.empty();
			return java.util.Optional.of(target.withPropertiesOf(state));
		}
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

	private static Session findSession(Level level, BlockPos pos) {
		for (Session session : SESSIONS.values()) {
			if (!session.key().dimension().equals(level.dimension())) {
				continue;
			}
			if (session.contains(pos)) {
				return session;
			}
		}
		return null;
	}

	private static BlockPos findBase(Map<BlockPos, BlockState> originals) {
		BlockPos best = null;
		for (BlockPos candidate : originals.keySet()) {
			if (best == null) {
				best = candidate;
				continue;
			}
			if (candidate.getY() < best.getY()) {
				best = candidate;
				continue;
			}
			if (candidate.getY() == best.getY()) {
				if (candidate.getX() < best.getX() || (candidate.getX() == best.getX() && candidate.getZ() < best.getZ())) {
					best = candidate;
				}
			}
		}
		return best;
	}

	private static final class Session {
		private final SessionKey key;
		private final Map<BlockPos, BlockState> originals;
		private final int requiredChops;
		private final int stumpStages;
		private int hits = 0;

		Session(SessionKey key, Map<BlockPos, BlockState> originals, int requiredChops, int stumpStages) {
			this.key = key;
			this.originals = originals;
			this.requiredChops = requiredChops;
			this.stumpStages = stumpStages;
		}

		SessionKey key() {
			return key;
		}

		boolean contains(BlockPos pos) {
			return pos.equals(key.base()) || originals.containsKey(pos);
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

		BlockState anyOriginal() {
			return originals.values().stream().findFirst().orElse(null);
		}

		int hits() {
			return hits;
		}

		int stumpStages() {
			return stumpStages;
		}
	}

	private record SessionKey(ResourceKey<Level> dimension, BlockPos base) {}

	public record Inspection(boolean isTree, int logs, int requiredChops, int hits, BlockPos inspectedPos) {
		public static Inspection notTree(BlockPos pos) {
			return new Inspection(false, 0, 0, 0, pos);
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
