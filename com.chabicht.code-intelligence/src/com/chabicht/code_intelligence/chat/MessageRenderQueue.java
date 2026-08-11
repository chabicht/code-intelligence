package com.chabicht.code_intelligence.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Coalesces streaming message renders while allowing forced renders to be
 * awaited until the rendered HTML has been applied on the UI thread.
 */
class MessageRenderQueue<T> {
	@FunctionalInterface
	interface Renderer<T> {
		String render(T snapshot);
	}

	@FunctionalInterface
	interface UiApplier {
		CompletableFuture<Void> apply(UUID messageId, String html);
	}

	@FunctionalInterface
	interface ErrorLogger {
		void log(String message, Throwable error);
	}

	private final long throttleMs;
	private final ScheduledExecutorService executor;
	private final Renderer<T> renderer;
	private final UiApplier uiApplier;
	private final ErrorLogger logger;
	private final Map<UUID, PendingUpdate<T>> pendingUpdates = new ConcurrentHashMap<>();

	MessageRenderQueue(long throttleMs, ScheduledExecutorService executor, Renderer<T> renderer, UiApplier uiApplier,
			ErrorLogger logger) {
		this.throttleMs = throttleMs;
		this.executor = executor;
		this.renderer = renderer;
		this.uiApplier = uiApplier;
		this.logger = logger;
	}

	CompletableFuture<Void> queue(UUID messageId, T snapshot, boolean forceImmediate) {
		CompletableFuture<Void> waiter = forceImmediate ? new CompletableFuture<>()
				: CompletableFuture.completedFuture(null);
		PendingUpdate<T> pendingUpdate = pendingUpdates.computeIfAbsent(messageId, PendingUpdate::new);

		synchronized (pendingUpdate) {
			pendingUpdate.latestSnapshot = snapshot;
			pendingUpdate.rerenderRequested = true;
			pendingUpdate.nextRenderImmediate = pendingUpdate.nextRenderImmediate || forceImmediate;
			if (forceImmediate) {
				pendingUpdate.waiters.add(waiter);
			}

			if (pendingUpdate.renderRunning) {
				return waiter;
			}

			if (pendingUpdate.scheduledTask != null && !pendingUpdate.scheduledTask.isDone()) {
				if (!forceImmediate) {
					return waiter;
				}
				pendingUpdate.scheduledTask.cancel(false);
				pendingUpdate.scheduledTask = null;
			}

			long delayMs = calculateDelay(pendingUpdate.nextRenderImmediate, pendingUpdate.lastRenderStartedAt);
			pendingUpdate.nextRenderImmediate = false;
			if (!schedule(pendingUpdate, delayMs)) {
				pendingUpdates.remove(pendingUpdate.messageId, pendingUpdate);
				completeWaiters(pendingUpdate.drainWaiters());
			}
		}
		return waiter;
	}

	void clear() {
		for (PendingUpdate<T> pendingUpdate : pendingUpdates.values()) {
			synchronized (pendingUpdate) {
				if (pendingUpdate.scheduledTask != null) {
					pendingUpdate.scheduledTask.cancel(false);
					pendingUpdate.scheduledTask = null;
				}
				completeWaiters(pendingUpdate.drainWaiters());
			}
		}
		pendingUpdates.clear();
	}

	private long calculateDelay(boolean immediate, long lastRenderStartedAt) {
		if (immediate || lastRenderStartedAt <= 0L) {
			return 0L;
		}

		long nextAllowedRenderAt = lastRenderStartedAt + throttleMs;
		return Math.max(0L, nextAllowedRenderAt - System.currentTimeMillis());
	}

	private boolean schedule(PendingUpdate<T> pendingUpdate, long delayMs) {
		try {
			pendingUpdate.scheduledTask = executor.schedule(() -> render(pendingUpdate), delayMs, TimeUnit.MILLISECONDS);
			return true;
		} catch (RejectedExecutionException e) {
			log("Message render executor rejected update for " + pendingUpdate.messageId, e);
			pendingUpdate.scheduledTask = null;
			return false;
		}
	}

	private void render(PendingUpdate<T> pendingUpdate) {
		T snapshot;
		List<CompletableFuture<Void>> waitersForThisRender;
		synchronized (pendingUpdate) {
			pendingUpdate.scheduledTask = null;
			pendingUpdate.renderRunning = true;
			snapshot = pendingUpdate.latestSnapshot;
			waitersForThisRender = pendingUpdate.drainWaiters();
			pendingUpdate.rerenderRequested = false;
			pendingUpdate.lastRenderStartedAt = System.currentTimeMillis();
		}

		String html = null;
		try {
			html = renderer.render(snapshot);
		} catch (RuntimeException e) {
			log("Error rendering chat message " + pendingUpdate.messageId, e);
		}

		CompletableFuture<Void> applyFuture = CompletableFuture.completedFuture(null);
		if (html != null) {
			try {
				CompletableFuture<Void> suppliedApplyFuture = uiApplier.apply(pendingUpdate.messageId, html);
				if (suppliedApplyFuture != null) {
					applyFuture = suppliedApplyFuture;
				}
			} catch (RuntimeException e) {
				log("Error applying rendered chat message " + pendingUpdate.messageId, e);
			}
		}

		applyFuture.handle((ignored, error) -> {
			if (error != null) {
				log("Error applying rendered chat message " + pendingUpdate.messageId, unwrapCompletionError(error));
			}
			completeWaiters(waitersForThisRender);
			finishRender(pendingUpdate);
			return null;
		});
	}

	private void finishRender(PendingUpdate<T> pendingUpdate) {
		synchronized (pendingUpdate) {
			pendingUpdate.renderRunning = false;
			if (pendingUpdate.rerenderRequested) {
				long delayMs = calculateDelay(pendingUpdate.nextRenderImmediate, pendingUpdate.lastRenderStartedAt);
				pendingUpdate.nextRenderImmediate = false;
				if (!schedule(pendingUpdate, delayMs)) {
					pendingUpdates.remove(pendingUpdate.messageId, pendingUpdate);
					completeWaiters(pendingUpdate.drainWaiters());
				}
				return;
			}
			pendingUpdates.remove(pendingUpdate.messageId, pendingUpdate);
		}
	}

	private void completeWaiters(List<CompletableFuture<Void>> waiters) {
		for (CompletableFuture<Void> waiter : waiters) {
			waiter.complete(null);
		}
	}

	private void log(String message, Throwable error) {
		if (logger != null) {
			logger.log(message, error);
		}
	}

	private Throwable unwrapCompletionError(Throwable error) {
		if (error instanceof java.util.concurrent.CompletionException && error.getCause() != null) {
			return error.getCause();
		}
		return error;
	}

	private static final class PendingUpdate<T> {
		private final UUID messageId;
		private final List<CompletableFuture<Void>> waiters = new ArrayList<>();
		private T latestSnapshot;
		private ScheduledFuture<?> scheduledTask;
		private boolean renderRunning;
		private boolean rerenderRequested;
		private boolean nextRenderImmediate;
		private long lastRenderStartedAt;

		private PendingUpdate(UUID messageId) {
			this.messageId = messageId;
		}

		private List<CompletableFuture<Void>> drainWaiters() {
			List<CompletableFuture<Void>> drained = new ArrayList<>(waiters);
			waiters.clear();
			return drained;
		}
	}
}
