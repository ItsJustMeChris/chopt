package mod.chopt;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import mod.chopt.mixin.AxeItemAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tree chopping helper that scales chops based on tree size.
 */
public final class TreeChopper {
	private static final int MAX_LOGS = 256;
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
		int remaining = Math.max(0, session.logsSize() - session.hits);
		if (!session.isComplete()) {
			msg(player, "hit " + session.hits + "/" + session.requiredChops + " (logs left " + remaining + ")");
			applyStripVisual(level, pos, state);
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
			chopRemaining(level, player, session, pos);
		} finally {
			PROCESSING.set(false);
		}
		SESSIONS.remove(player.getUUID());
		msg(player, "quota reached, timber!");
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
		int requiredChops = computeRequiredChops(originals.size());
		msgOrigin(originals.size(), requiredChops);
		return new Session(originals, requiredChops);
	}

	private static int computeRequiredChops(int logCount) {
		double value = 0.12 * logCount + 2.0 * Math.log1p(logCount);
		int chops = (int) Math.ceil(value);
		chops = Math.max(1, Math.min(logCount, chops));
		return Math.min(chops, 32); // hard cap for sanity
	}

	private static void msgOrigin(int logs, int chops) {
		// purely for debugging; no player context available here
		Chopt.LOGGER.info("[chopt] scan logs={} chops={}", logs, chops);
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

			for (Direction dir : Direction.values()) {
				BlockPos next = current.relative(dir);
				if (!originals.containsKey(next) && level.getBlockState(next).is(BlockTags.LOGS)) {
					queue.add(next);
				}
			}
		}

		return originals;
	}

	private static void chopRemaining(Level level, Player player, Session session, BlockPos alreadyBroken) {
		for (Map.Entry<BlockPos, BlockState> entry : session.originals.entrySet()) {
			BlockPos pos = entry.getKey();
			if (pos.equals(alreadyBroken)) continue;
			BlockState original = entry.getValue();
			Block.dropResources(original, level, pos, level.getBlockEntity(pos), player, player.getMainHandItem());
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		}
	}

	private static void msg(Player player, String text) {
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendSystemMessage(Component.literal("[chopt] " + text));
		}
	}

	private static void dropSession(Player player, String reason) {
		SESSIONS.remove(player.getUUID());
		msg(player, reason);
	}

	private static final class Session {
		private final Map<BlockPos, BlockState> originals;
		private final int requiredChops;
		private int hits = 0;

		Session(Map<BlockPos, BlockState> originals, int requiredChops) {
			this.originals = originals;
			this.requiredChops = requiredChops;
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
	}

	private static void applyStripVisual(Level level, BlockPos pos, BlockState currentState) {
		Block stripped = AxeItemAccessor.getStrippables().get(currentState.getBlock());
		if (stripped == null) {
			return;
		}
		BlockState newState = stripped.defaultBlockState();
		if (currentState.hasProperty(RotatedPillarBlock.AXIS) && newState.hasProperty(RotatedPillarBlock.AXIS)) {
			newState = newState.setValue(RotatedPillarBlock.AXIS, currentState.getValue(RotatedPillarBlock.AXIS));
		}
		if (!newState.equals(currentState)) {
			level.setBlock(pos, newState, 11);
		}
	}
}
