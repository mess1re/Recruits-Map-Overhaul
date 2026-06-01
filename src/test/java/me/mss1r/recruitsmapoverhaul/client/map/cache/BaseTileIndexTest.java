package me.mss1r.recruitsmapoverhaul.client.map.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseTileIndexTest {
    @TempDir
    Path tempDir;

    @Test
    void scansOnlyNonEmptyBaseTileFiles() throws IOException {
        Files.write(tempDir.resolve("2_-3.png"), new byte[]{1});
        Files.createFile(tempDir.resolve("4_5.png"));
        Files.write(tempDir.resolve("bad_name.png"), new byte[]{1});
        Files.createDirectory(tempDir.resolve("overview_v1"));

        BaseTileIndex index = BaseTileIndex.scan(tempDir.toFile());

        assertTrue(index.hasAny(2, 2, -3, -3));
        assertFalse(index.hasAny(4, 4, 5, 5));
        assertFalse(index.hasAny(-10, -1, -10, -1));
    }

    @Test
    void addMakesNewTilesVisibleToSubtreeLookups() {
        BaseTileIndex index = BaseTileIndex.scan(tempDir.toFile());

        assertFalse(index.hasAny(-10, -1, 10, 20));

        index.add(-8, 12);

        assertTrue(index.hasAny(-10, -1, 10, 20));
    }
}
