package mod.chopt;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
			msg(player, "tree size " + session.logs.size() + ", chops needed " + session.requiredChops);
		}

			session.recordAttempt();
			int remaining = Math.max(0, session.logs.size() - session.hits);
			if (!session.isComplete()) {
				msg(player, "hit " + session.hits + "/" + session.requiredChops + " (logs left " + remaining + ")");
				return false; // cancel breaking to allow repeated hits on same log
			}

		session.markReadyToFell();
		msg(player, "quota reached, breaking now");
		return true; // allow this break to proceed
	}

	private static void afterBreak(Level level, Player player, BlockPos pos, BlockState state, /* nullable */ Object blockEntity) {
		if (level.isClientSide()) {
			return;
		}
		Session session = SESSIONS.get(player.getUUID());
		if (session == null || !session.readyToFell) {
			return;
		}
		PROCESSING.set(true);
		try {
			chopRemaining(level, player, session, pos);
		} finally {
			PROCESSING.set(false);
		}
		SESSIONS.remove(player.getUUID());
	}

	private static Session buildSession(Level level, BlockPos origin) {
		Set<BlockPos> logs = scanLogs(level, origin);
		if (logs.isEmpty()) {
			return null;
		}
		int requiredChops = computeRequiredChops(logs.size());
		msgOrigin(logs.size(), requiredChops);
		return new Session(logs, requiredChops);
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

	private static Set<BlockPos> scanLogs(Level level, BlockPos origin) {
		Set<BlockPos> visited = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		queue.add(origin);

		while (!queue.isEmpty() && visited.size() < MAX_LOGS) {
			BlockPos current = queue.removeFirst();
			if (!visited.add(current)) {
				continue;
			}

			BlockState state = level.getBlockState(current);
			if (!state.is(BlockTags.LOGS)) {
				continue;
			}

			for (Direction dir : Direction.values()) {
				BlockPos next = current.relative(dir);
				if (!visited.contains(next) && level.getBlockState(next).is(BlockTags.LOGS)) {
					queue.add(next);
				}
			}
		}

		return visited;
	}

	private static void chopRemaining(Level level, Player player, Session session, BlockPos alreadyBroken) {
		for (BlockPos pos : session.logs) {
			if (pos.equals(alreadyBroken)) continue;
			BlockState state = level.getBlockState(pos);
			if (state.is(BlockTags.LOGS)) {
				level.destroyBlock(pos, true, player);
			}
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
		private final Set<BlockPos> logs;
		private final int requiredChops;
		private int hits = 0;
		private boolean readyToFell = false;

		Session(Set<BlockPos> logs, int requiredChops) {
			this.logs = logs;
			this.requiredChops = requiredChops;
		}

		boolean contains(BlockPos pos) {
			return logs.contains(pos);
		}

		void recordAttempt() {
			hits++;
		}

		boolean isComplete() {
			return hits >= requiredChops;
		}

		void markReadyToFell() {
			readyToFell = true;
		}
	}
}
