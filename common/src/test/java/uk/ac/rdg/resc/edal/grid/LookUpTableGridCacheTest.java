/*******************************************************************************
 * Copyright (c) 2011 The University of Reading
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the University of Reading, nor the names of the
 *    authors or contributors may be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *******************************************************************************/

package uk.ac.rdg.resc.edal.grid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import uk.ac.rdg.resc.edal.util.Array2D;
import uk.ac.rdg.resc.edal.util.ValuesArray2D;

/**
 * Unit tests for {@link LookUpTableGrid} cache
 */
public class LookUpTableGridCacheTest {

    @Before
    public void setUp() {
        LookUpTableGrid.clearCache();
    }

    @After
    public void tearDown() {
        LookUpTableGrid.clearCache();
    }

    // Simulate curvilinear longitudes
    // Setting the index will let us identify which set of grids are being accessed
    private static Array2D<Number> createLon(int index) {
        Array2D<Number> lonVals = new ValuesArray2D(3, 3);
        for (int j = 0; j < 3; j++) {
            for (int i = 0; i < 3; i++) {
                lonVals.set(-150.0 + (index * 3.0) + i, j, i);
            }
        }
        return lonVals;
    }

    // Simulate curvilinear longitudes
    private static Array2D<Number> createLat() {
        Array2D<Number> latVals = new ValuesArray2D(3, 3);
        for (int j = 0; j < 3; j++) {
            for (int i = 0; i < 3; i++) {
                latVals.set(10.0 + j, j, i);
            }
        }
        return latVals;
    }

    @Test
    public void testCacheHitReturnsSameInstance() {
        LookUpTableGrid grid1 = LookUpTableGrid.generate(createLon(0), createLat());
        LookUpTableGrid grid2 = LookUpTableGrid.generate(createLon(0), createLat());
        assertSame("Cached grid should return the exact same instance", grid1, grid2);
        assertEquals(1, LookUpTableGrid.getCacheSize());
    }

    @Test
    public void testCacheBoundedAndEldestEvicted() {
        int maxCapacity = LookUpTableGrid.MAX_CACHE_SIZE;
        List<LookUpTableGrid> initialGrids = new ArrayList<LookUpTableGrid>(maxCapacity);

        // Fill cache to capacity
        for (int i = 0; i < maxCapacity; i++) {
            initialGrids.add(LookUpTableGrid.generate(createLon(i), createLat()));
        }
        assertEquals(maxCapacity, LookUpTableGrid.getCacheSize());

        // Verify that all entries are currently hits
        for (int i = 0; i < maxCapacity; i++) {
            assertSame(initialGrids.get(i), LookUpTableGrid.generate(createLon(i), createLat()));
        }

        // Let's add a new grid to trigger an eviction
        LookUpTableGrid overflowGrid = LookUpTableGrid.generate(createLon(maxCapacity), createLat());
        assertEquals(maxCapacity, LookUpTableGrid.getCacheSize());

        // lon with index 0 was the least recently used, so it should have been evicted
        LookUpTableGrid regeneratedGrid0 = LookUpTableGrid.generate(createLon(0), createLat());
        assertNotSame("Grid 0 should have been evicted and regenerated", initialGrids.get(0), regeneratedGrid0);
        assertEquals(maxCapacity, LookUpTableGrid.getCacheSize());

        // Overflow grid should be in the cache
        assertSame(overflowGrid, LookUpTableGrid.generate(createLon(maxCapacity), createLat()));
    }

    @Test
    public void testLruAccessOrderPreventsEviction() {
        int maxCapacity = LookUpTableGrid.MAX_CACHE_SIZE;
        List<LookUpTableGrid> initialGrids = new ArrayList<LookUpTableGrid>(maxCapacity);

        // Fill cache to capacity
        for (int i = 0; i < maxCapacity; i++) {
            initialGrids.add(LookUpTableGrid.generate(createLon(i), createLat()));
        }
        assertEquals(maxCapacity, LookUpTableGrid.getCacheSize());

        // Access lon index 0, making it the most recently used
        LookUpTableGrid accessedGrid0 = LookUpTableGrid.generate(createLon(0), createLat());
        assertSame(initialGrids.get(0), accessedGrid0);

        // Now lon index 1 is the oldest entry. Adding a new entry should evict
        // grid index 1, not grid index 0.
        LookUpTableGrid newGrid = LookUpTableGrid.generate(createLon(maxCapacity), createLat());
        assertEquals(maxCapacity, LookUpTableGrid.getCacheSize());

        // Grid index 0 should still be in cache
        assertSame("Grid 0 was recently accessed and should remain cached",
                initialGrids.get(0), LookUpTableGrid.generate(createLon(0), createLat()));

        // Grid index 1 should have been evicted
        LookUpTableGrid regeneratedGrid1 = LookUpTableGrid.generate(createLon(1), createLat());
        assertNotSame("Grid 1 should have been evicted", initialGrids.get(1), regeneratedGrid1);
    }

    @Test
    public void testClearCache() {
        LookUpTableGrid grid = LookUpTableGrid.generate(createLon(0), createLat());
        assertEquals(1, LookUpTableGrid.getCacheSize());

        LookUpTableGrid.clearCache();
        assertEquals(0, LookUpTableGrid.getCacheSize());

        LookUpTableGrid newGrid = LookUpTableGrid.generate(createLon(0), createLat());
        assertNotSame("After clearCache, a new instance should be generated", grid, newGrid);
        assertEquals(1, LookUpTableGrid.getCacheSize());
    }

    @Test
    public void testConcurrentAccessWithinCapacity() throws Exception {
        int numThreads = 8;
        int iterationsPerThread = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Callable<Void>> tasks = new ArrayList<Callable<Void>>();

        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            tasks.add(new Callable<Void>() {
                @Override
                public Void call() {
                    for (int i = 0; i < iterationsPerThread; i++) {
                        // Confine keys within capacity so entries are not evicted during concurrent access
                        int index = (threadId + i) % LookUpTableGrid.MAX_CACHE_SIZE;
                        LookUpTableGrid grid = LookUpTableGrid.generate(createLon(index), createLat());
                        // Access again to verify cache lookup returns the same instance
                        assertSame(grid, LookUpTableGrid.generate(createLon(index), createLat()));
                    }
                    return null;
                }
            });
        }

        List<Future<Void>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        for (Future<Void> future : futures) {
            future.get(); // ensure no exception was thrown
        }

        assertTrue("Cache size should not exceed max capacity under concurrent load",
                LookUpTableGrid.getCacheSize() <= LookUpTableGrid.MAX_CACHE_SIZE);
    }

    @Test
    public void testConcurrentAccessWithEviction() throws Exception {
        int numThreads = 8;
        int iterationsPerThread = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Callable<Void>> tasks = new ArrayList<Callable<Void>>();

        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            tasks.add(new Callable<Void>() {
                @Override
                public Void call() {
                    for (int i = 0; i < iterationsPerThread; i++) {
                        // Access more keys than cache capacity to stress concurrent eviction
                        int index = (threadId + i) % (LookUpTableGrid.MAX_CACHE_SIZE * 2);
                        LookUpTableGrid grid = LookUpTableGrid.generate(createLon(index), createLat());
                        assertNotNull(grid);
                    }
                    return null;
                }
            });
        }

        List<Future<Void>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        for (Future<Void> future : futures) {
            future.get(); // ensure no exception was thrown
        }

        assertTrue("Cache size should not exceed max capacity under concurrent load",
                LookUpTableGrid.getCacheSize() <= LookUpTableGrid.MAX_CACHE_SIZE);
    }
}
