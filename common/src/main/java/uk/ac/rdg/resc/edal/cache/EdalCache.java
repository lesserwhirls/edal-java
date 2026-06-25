/*******************************************************************************
 * Copyright (c) 2018 The University of Reading
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
 ******************************************************************************/

package uk.ac.rdg.resc.edal.cache;

import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheManagerBuilder;

/**
 * Holds the singleton Ehcache 3.x {@link CacheManager} used by EDAL.
 *
 * <p>
 * In Ehcache 2.x the {@code CacheManager} could be configured with a name and a
 * global "size of policy" (so the cache could be sized by bytes-on-heap rather
 * than entry count). Ehcache 3.x has a different model: caches are typed
 * ({@code Cache<K,V>}), and any optional XML configuration is loaded directly
 * into a {@link CacheManager} via
 * {@link org.ehcache.xml.XmlConfiguration}. Therefore this class simply
 * exposes a singleton {@link CacheManager} which can be augmented at runtime by
 * the various EDAL components (Domain2DMapper, HorizontalMesh4dDataset,
 * OnDemandVtkDataSource, DataCatalogue, ...).
 *
 * <p>
 * The cached objects are typically large (gridded map features with
 * 256*256 ~= 65,000 values, or collections of point features containing tens
 * of thousands of features). Cache sizing for these objects is therefore
 * handled per-cache, in units of MB on heap, rather than by entry count.
 */
public class EdalCache {
    /**
     * The shared, application-wide Ehcache 3 {@link CacheManager}. Caches are
     * registered against this manager by the various EDAL modules. It is built
     * (and initialised) eagerly so that callers can rely on it being usable
     * immediately.
     */
    public static final CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
            .build(true);

  /**
   * The finalization step from MurmurHash3, used to scramble an integer hash
   * so that the resulting bits are well distributed.
   *
   * <p>
   * This is the same finalizer used by Spring's {@code SimpleKey} (see
   * <a href="https://github.com/spring-projects/spring-framework/blob/c74f897facf830471e2524252a4f46ded05be895/spring-context/src/main/java/org/springframework/cache/interceptor/SimpleKey.java#L92-L94">SimpleKey.java</a>).
   * See <a href="https://github.com/Reading-eScience-Centre/edal-java/issues/171#issuecomment-2708650091">
   * issue #171, comment 2708650091</a> for the discussion that motivated this.
   *
   * @param hash the input combined hash code
   * @return a strongly mixed hash code derived from {@code hash}
   */
  public static int murmur3Finalize(int hash) {
    hash ^= (hash >>> 16);
    hash *= 0x85ebca6b;
    hash ^= (hash >>> 13);
    hash *= 0xc2b2ae35;
    hash ^= (hash >>> 16);
    return hash;
  }
}
