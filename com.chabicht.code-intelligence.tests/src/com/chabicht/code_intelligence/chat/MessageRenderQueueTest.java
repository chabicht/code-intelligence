package com.chabicht.code_intelligence.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class MessageRenderQueueTest {
	@Test
	void throttledUpdatesCoalesce() throws Exception {
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		CompletableFuture<Void> releaseExecutor = new CompletableFuture<>();
		executor.submit(() -> releaseExecutor.join());
		try {
			UUID messageId = UUID.randomUUID();
			List<String> appliedHtml = new ArrayList<>();
			AtomicInteger renderCount = new AtomicInteger();
			MessageRenderQueue<String> queue = new MessageRenderQueue<>(150L, executor, snapshot -> {
				renderCount.incrementAndGet();
				return snapshot;
			}, (id, html) -> {
				appliedHtml.add(html);
				return CompletableFuture.completedFuture(null);
			}, (message, error) -> {
			});

			queue.queue(messageId, "first", false);
			queue.queue(messageId, "second", false);

			releaseExecutor.complete(null);
			executor.shutdown();
			assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));

			assertEquals(1, renderCount.get());
			assertEquals(List.of("second"), appliedHtml);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void forcedUpdateCompletesOnlyAfterUiApply() throws Exception {
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		try {
			UUID messageId = UUID.randomUUID();
			CompletableFuture<Void> applyFuture = new CompletableFuture<>();
			MessageRenderQueue<String> queue = new MessageRenderQueue<>(150L, executor, snapshot -> snapshot,
					(id, html) -> applyFuture, (message, error) -> {
					});

			CompletableFuture<Void> forcedFlush = queue.queue(messageId, "final", true);

			Thread.sleep(100L);
			assertFalse(forcedFlush.isDone());

			applyFuture.complete(null);
			forcedFlush.get(1, TimeUnit.SECONDS);
			assertTrue(forcedFlush.isDone());
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void finalWaiterCompletesOnRenderFailure() throws Exception {
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		try {
			UUID messageId = UUID.randomUUID();
			MessageRenderQueue<String> queue = new MessageRenderQueue<>(150L, executor, snapshot -> {
				throw new IllegalStateException("render failed");
			}, (id, html) -> CompletableFuture.completedFuture(null), (message, error) -> {
			});

			CompletableFuture<Void> forcedFlush = queue.queue(messageId, "final", true);

			forcedFlush.get(1, TimeUnit.SECONDS);
			assertTrue(forcedFlush.isDone());
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void finalWaiterCompletesOnApplyFailure() throws Exception {
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		try {
			UUID messageId = UUID.randomUUID();
			MessageRenderQueue<String> queue = new MessageRenderQueue<>(150L, executor, snapshot -> snapshot,
					(id, html) -> CompletableFuture.failedFuture(new IllegalStateException("apply failed")),
					(message, error) -> {
					});

			CompletableFuture<Void> forcedFlush = queue.queue(messageId, "final", true);

			forcedFlush.get(1, TimeUnit.SECONDS);
			assertTrue(forcedFlush.isDone());
		} finally {
			executor.shutdownNow();
		}
	}
}
