package logbook.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import logbook.bean.AppExpRecords;
import logbook.bean.AppQuestDuration;

/**
 * {@link Config#store(Class[])} と {@link Config#requestStore(Class[])} のテスト。
 */
class ConfigRequestStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void store_writesOnlySpecifiedClass() throws Exception {
        Config config = new Config(this.tempDir);
        AppExpRecords records = config.get(AppExpRecords.class, AppExpRecords::new);
        records.setExp12h(42L);
        config.get(AppQuestDuration.class, AppQuestDuration::new);

        config.store(AppExpRecords.class);

        Path expPath = this.jsonPath(AppExpRecords.class);
        Path questPath = this.jsonPath(AppQuestDuration.class);
        assertTrue(Files.isRegularFile(expPath));
        assertFalse(Files.exists(questPath));

        AppExpRecords loaded = JsonMappers.LENIENT_READER.forType(AppExpRecords.class).readValue(expPath);
        assertEquals(42L, loaded.getExp12h());
        assertEquals(1, config.writeCountForTest());
    }

    @Test
    void requestStore_coalescesConcurrentCallsAndKeepsLatestMemory() throws Exception {
        Config config = new Config(this.tempDir);
        AppExpRecords records = config.get(AppExpRecords.class, AppExpRecords::new);
        records.setExp12h(99L);

        int n = 40;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch submitted = new CountDownLatch(n);
        for (int i = 0; i < n; i++) {
            ThreadManager.getExecutorService().execute(() -> {
                try {
                    start.await();
                    config.requestStore(AppExpRecords.class);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    submitted.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(submitted.await(5, TimeUnit.SECONDS));
        config.awaitRequestStore();

        int writes = config.writeCountForTest();
        assertTrue(writes >= 1, "少なくとも 1 回は書く");
        assertTrue(writes < n, "連打では 1 対 1 にならない: writes=" + writes);

        records.setExp12h(100L);
        config.requestStore(AppExpRecords.class);
        config.awaitRequestStore();

        Path expPath = this.jsonPath(AppExpRecords.class);
        AppExpRecords loaded = JsonMappers.LENIENT_READER.forType(AppExpRecords.class).readValue(expPath);
        assertEquals(100L, loaded.getExp12h());
    }

    private Path jsonPath(Class<?> clazz) {
        return this.tempDir.resolve(clazz.getCanonicalName() + ".json");
    }
}
