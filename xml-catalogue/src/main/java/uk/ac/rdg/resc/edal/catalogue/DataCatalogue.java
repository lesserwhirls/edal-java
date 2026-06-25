/*******************************************************************************
 * Copyright (c) 2015 The University of Reading
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

package uk.ac.rdg.resc.edal.catalogue;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.ResourceType;
import org.ehcache.config.SizedResourcePool;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.expiry.ExpiryPolicy;
import org.ehcache.xml.XmlConfiguration;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.rdg.resc.edal.cache.EdalCache;
import uk.ac.rdg.resc.edal.catalogue.jaxb.CacheInfo;
import uk.ac.rdg.resc.edal.catalogue.jaxb.CatalogueConfig;
import uk.ac.rdg.resc.edal.catalogue.jaxb.CatalogueConfig.DatasetStorage;
import uk.ac.rdg.resc.edal.catalogue.jaxb.DatasetConfig;
import uk.ac.rdg.resc.edal.catalogue.jaxb.VariableConfig;
import uk.ac.rdg.resc.edal.dataset.Dataset;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.feature.DiscreteFeature;
import uk.ac.rdg.resc.edal.graphics.exceptions.EdalLayerNotFoundException;
import uk.ac.rdg.resc.edal.graphics.utils.DatasetCatalogue;
import uk.ac.rdg.resc.edal.graphics.utils.EnhancedVariableMetadata;
import uk.ac.rdg.resc.edal.graphics.utils.FeatureCatalogue;
import uk.ac.rdg.resc.edal.graphics.utils.GraphicsUtils;
import uk.ac.rdg.resc.edal.graphics.utils.LayerNameMapper;
import uk.ac.rdg.resc.edal.graphics.utils.PlottingDomainParams;
import uk.ac.rdg.resc.edal.metadata.VariableMetadata;

/**
 * A catalogues which implements {@link DatasetCatalogue},
 * {@link DatasetStorage}, and {@link FeatureCatalogue}. Given a
 * {@link CacheConfiguration}, this is able to return {@link Dataset}s, and
 * {@link Collection}s of {@link DiscreteFeature}s given a single {@link String}
 * layer identifier.
 * 
 * It also provides a cache of {@link DiscreteFeature}s for speed.
 *
 * @author Guy Griffiths
 */
public class DataCatalogue implements DatasetCatalogue, DatasetStorage, FeatureCatalogue {
    private static final Logger log = LoggerFactory.getLogger(DataCatalogue.class);

    private static final String WMS_CACHE_CONFIG = "ehcache.config";
    private static final String CACHE_NAME = "featureCache";
    private static final long CACHE_SIZE_MB = 512;
    private static final int LIFETIME_SECONDS = 0;

    private boolean cachingEnabled;
    @SuppressWarnings("rawtypes")
    private Cache<CacheKey, Collection> featureCache = null;

    protected final CatalogueConfig config;
    protected Map<String, Dataset> datasets;
    private final Map<DatasetVariableId, EnhancedVariableMetadata> layerMetadata;

    protected final LayerNameMapper layerNameMapper;

    private DateTime lastUpdateTime = new DateTime();

    public DataCatalogue() {
        config = null;
        layerMetadata = null;
        layerNameMapper = null;
    }

    public DataCatalogue(CatalogueConfig config, LayerNameMapper layerNameMapper)
            throws IOException {
        /*
         * Initialise the storage for datasets and layer metadata.
         */
        datasets = new HashMap<>();
        layerMetadata = new HashMap<>();

        this.config = config;
        this.config.setDatasetLoadedHandler(this);
        this.config.loadDatasets();

        this.layerNameMapper = layerNameMapper;

        this.cachingEnabled = config.getCacheSettings().isEnabled();
        long cacheLifetimeSeconds = (long) (config.getCacheSettings().getElementLifetimeMinutes()
                * 60);

        String ehcache_file = System.getProperty(WMS_CACHE_CONFIG);
        if (ehcache_file != null && !ehcache_file.isEmpty()) {
            /*
             * We want to load the caches from the XML file into the EDAL cache
             * manager. Ehcache 3 loads XML configurations through
             * XmlConfiguration; we transfer each cache definition into the
             * shared EDAL CacheManager so the rest of EDAL sees them.
             */
            log.debug("Loading cache definitions from file");
            try {
                XmlConfiguration xmlConfig = new XmlConfiguration(
                        new File(ehcache_file).toURI().toURL());
                for (String cacheName : xmlConfig.getCacheConfigurations().keySet()) {
                    if (EdalCache.cacheManager.getRuntimeConfiguration()
                            .getCacheConfigurations().containsKey(cacheName)) {
                        /*
                         * Remove any existing cache
                         */
                        EdalCache.cacheManager.removeCache(cacheName);
                    }
                    EdalCache.cacheManager.createCache(cacheName,
                            xmlConfig.getCacheConfigurations().get(cacheName));
                }
            } catch (MalformedURLException e) {
                throw new EdalException("Invalid ehcache.config path: " + ehcache_file, e);
            }
        }

        if (cachingEnabled) {
            @SuppressWarnings("rawtypes")
            Cache<CacheKey, Collection> existing = EdalCache.cacheManager.getCache(CACHE_NAME,
                    CacheKey.class, Collection.class);
            if (existing != null) {
                /*
                 * Use parameters for featureCache from ehcache.xml config file
                 * if passed in as JVM parameter ehcache.config - Update cache
                 * params in CatalogueConfig
                 */
                featureCache = existing;
                CacheInfo catalogueCacheInfo = config.getCacheSettings();
                CacheConfiguration<?, ?> featureCacheConfiguration = EdalCache.cacheManager
                        .getRuntimeConfiguration().getCacheConfigurations().get(CACHE_NAME);
                catalogueCacheInfo
                        .setInMemorySizeMB((int) getHeapSizeMB(featureCacheConfiguration));
                catalogueCacheInfo
                        .setElementLifetimeMinutes(getTtlSeconds(featureCacheConfiguration) / 60f);
                catalogueCacheInfo.setEnabled(true);
            } else {
                /*
                 * Either no ehcache.xml file is available, or it does not
                 * define "featureCache". In this case, configure with values
                 * from config.xml
                 */
                featureCache = createFeatureCache(config.getCacheSettings().getInMemorySizeMB(),
                        cacheLifetimeSeconds);
            }
        }
    }

    /**
     * Creates the feature cache in the shared {@link EdalCache#cacheManager}
     * with the specified heap size (MB) and time-to-live (seconds; 0 means
     * never expire).
     */
    @SuppressWarnings("rawtypes")
    private static Cache<CacheKey, Collection> createFeatureCache(long sizeMB,
            long lifetimeSeconds) {
        CacheConfigurationBuilder<CacheKey, Collection> builder = CacheConfigurationBuilder
                .newCacheConfigurationBuilder(CacheKey.class, Collection.class,
                        ResourcePoolsBuilder.newResourcePoolsBuilder().heap(sizeMB, MemoryUnit.MB));
        if (lifetimeSeconds <= 0) {
            builder = builder.withExpiry(ExpiryPolicyBuilder.noExpiration());
        } else {
            builder = builder.withExpiry(ExpiryPolicyBuilder
                    .timeToLiveExpiration(Duration.ofSeconds(lifetimeSeconds)));
        }
        return EdalCache.cacheManager.createCache(CACHE_NAME, builder);
    }

    private static long getHeapSizeMB(CacheConfiguration<?, ?> cacheConfiguration) {
        if (cacheConfiguration == null) {
            return 0;
        }
        SizedResourcePool heap = cacheConfiguration.getResourcePools()
                .getPoolForResource(ResourceType.Core.HEAP);
        if (heap == null) {
            return 0;
        }
        org.ehcache.config.units.MemoryUnit unit = (org.ehcache.config.units.MemoryUnit) heap
                .getUnit();
        return unit.toBytes(heap.getSize()) / (1024 * 1024);
    }

    private static long getTtlSeconds(CacheConfiguration<?, ?> cacheConfiguration) {
        if (cacheConfiguration == null) {
            return 0;
        }
        @SuppressWarnings({ "unchecked", "rawtypes" })
        ExpiryPolicy<Object, Object> expiry = (ExpiryPolicy) cacheConfiguration.getExpiryPolicy();
        if (expiry == null) {
            return 0;
        }
        Duration ttl = expiry.getExpiryForCreation(null, null);
        if (ttl == null || ExpiryPolicy.INFINITE.equals(ttl)) {
            return 0;
        }
        return ttl.getSeconds();
    }

    public CatalogueConfig getConfig() {
        return config;
    }

    public void shutdown() {
        CatalogueConfig.shutdown();
    }

    /**
     * Configures the cache used to store features
     * 
     * @param cacheConfig
     *            The (new) configuration to use for the cache. Must not be
     *            <code>null</code>
     */
    public void setCache(CacheInfo cacheConfig) {
        long configCacheSizeMB = cacheConfig.getInMemorySizeMB();
        long configLifetimeSeconds = (long) (cacheConfig.getElementLifetimeMinutes() * 60);

        CacheConfiguration<?, ?> currentConfig = EdalCache.cacheManager.getRuntimeConfiguration()
                .getCacheConfigurations().get(CACHE_NAME);
        if (featureCache != null && cachingEnabled == cacheConfig.isEnabled()
                && currentConfig != null
                && configCacheSizeMB == getHeapSizeMB(currentConfig)
                && configLifetimeSeconds == getTtlSeconds(currentConfig)) {
            /*
             * We are not changing anything about the cache.
             */
            return;
        }

        cachingEnabled = cacheConfig.isEnabled();

        if (cachingEnabled) {
            /*-
             * Ehcache 3 cache configurations are immutable, so any
             * resize/TTL change is implemented by removing and recreating
             * the cache.
             *
             * Precedence:
             * - Admin config
             * - XML file "ehcache.config"
             * - Default values
             */

            /*
             * Default values
             */
            long cacheSizeMB = CACHE_SIZE_MB;
            long lifetimeSeconds = LIFETIME_SECONDS;

            /*
             * XML config: pull values from the existing (XML-loaded)
             * featureCache, if it is currently registered.
             */
            if (currentConfig != null) {
                cacheSizeMB = getHeapSizeMB(currentConfig);
                lifetimeSeconds = getTtlSeconds(currentConfig);
            }

            /*
             * Admin
             */
            if (cacheConfig.getInMemorySizeMB() != 0) {
                cacheSizeMB = configCacheSizeMB;
            }
            if (cacheConfig.getElementLifetimeMinutes() != 0) {
                lifetimeSeconds = configLifetimeSeconds;
            }

            if (currentConfig != null) {
                EdalCache.cacheManager.removeCache(CACHE_NAME);
            }
            featureCache = createFeatureCache(cacheSizeMB, lifetimeSeconds);
        } else {
            /*
             * Remove existing cache to free up memory
             */
            if (currentConfig != null) {
                EdalCache.cacheManager.removeCache(CACHE_NAME);
            }
            featureCache = null;
        }
    }

    /**
     * Removes a dataset from the catalogue. This will also delete any config
     * information about the dataset from the config file.
     * 
     * @param id
     *            The ID of the dataset to remove
     */
    public void removeDataset(String id) {
        datasets.remove(id);
        config.removeDataset(config.getDatasetInfo(id));
    }

    /**
     * Changes a dataset's ID. This will also change the name in the saved
     * config file.
     * 
     * @param oldId
     *            The old ID
     * @param newId
     *            The new ID
     */
    public void changeDatasetId(String oldId, String newId) {
        Dataset dataset = datasets.get(oldId);
        datasets.remove(oldId);
        datasets.put(newId, dataset);
        config.changeDatasetId(config.getDatasetInfo(oldId), newId);
    }

    @Override
    public synchronized void datasetLoaded(Dataset dataset, Collection<VariableConfig> variables) {
        /*
         * If we have any tiles in the cache with this dataset ID, we want to remove them.
         */
        if (cachingEnabled && featureCache != null) {
            /*
             * Ehcache 3 caches are Iterable<Cache.Entry<K,V>>; collect keys to
             * a temporary list before removing to avoid concurrent modification.
             */
            List<CacheKey> toRemove = new ArrayList<>();
            for (@SuppressWarnings("rawtypes")
            Cache.Entry<CacheKey, Collection> entry : featureCache) {
                CacheKey cacheKey = entry.getKey();
                String datasetId = layerNameMapper.getDatasetIdFromLayerName(cacheKey.layerName);
                if (dataset.getId().equals(datasetId)) {
                    toRemove.add(cacheKey);
                }
            }
            for (CacheKey cacheKey : toRemove) {
                featureCache.remove(cacheKey);
            }
        }
        /*
         * If we already have a dataset with this ID, it will be replaced. This
         * is exactly what we want.
         */
        datasets.put(dataset.getId(), dataset);

        /*
         * Re-sort the datasets map according to the titles of the datasets, so
         * that they appear in the menu in this order.
         */
        List<Map.Entry<String, Dataset>> entryList = new ArrayList<Map.Entry<String, Dataset>>(
                datasets.entrySet());
        try {
            Collections.sort(entryList, new Comparator<Map.Entry<String, Dataset>>() {
                public int compare(Map.Entry<String, Dataset> d1, Map.Entry<String, Dataset> d2) {
                    return config.getDatasetInfo(d1.getKey()).getTitle()
                            .compareTo(config.getDatasetInfo(d2.getKey()).getTitle());
                }
            });
        } catch (NullPointerException e) {
            log.error("Problem when sorting datasets", e);
            /*
             * Sometimes this gives a NullPointerException with remote datasets
             * which are unavailable (?)
             * 
             * It's been seen a couple of times on the issue tracker, but I've
             * been unable to reproduce it. I think it may be that the title is
             * not getting set correctly (or at all?). Perhaps this needs some
             * more robust checking in the CatalogueConfig object?
             * 
             * Since sorting the datasets by title isn't critical, we can ignore
             * the error.
             */
        }

        datasets = new LinkedHashMap<String, Dataset>();
        for (Map.Entry<String, Dataset> entry : entryList) {
            datasets.put(entry.getKey(), entry.getValue());
        }

        /*
         * Now add the layer metadata to a map for future reference
         */
        for (VariableConfig variable : variables) {
            DatasetVariableId id = new DatasetVariableId(variable.getParentDataset().getId(),
                    variable.getId());
            layerMetadata.put(id, variable);
        }
        lastUpdateTime = new DateTime();

        /*
         * The config has changed, so we save it.
         */
        try {
            config.save();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public DateTime getLastUpdateTime() {
        return lastUpdateTime;
    }

    @Override
    public Collection<Dataset> getAllDatasets() {
        /*
         * This catalogue stores all possible datasets, but this method must
         * only return those which are available (i.e. not disabled and ready to
         * go)
         */
        List<Dataset> allDatasets = new ArrayList<Dataset>();
        for (Dataset dataset : datasets.values()) {
            DatasetConfig datasetInfo = config.getDatasetInfo(dataset.getId());
            if (datasetInfo != null && !datasetInfo.isDisabled() && datasetInfo.isReady()) {
                allDatasets.add(dataset);
            }
        }
        return allDatasets;
    }

    @Override
    public Dataset getDatasetFromId(String datasetId) {
        if (datasets.containsKey(datasetId)) {
            return datasets.get(datasetId);
        } else {
            return null;
        }
    }

    public DatasetConfig getDatasetInfo(String datasetId) {
        return config.getDatasetInfo(datasetId);
    }

    @Override
    public EnhancedVariableMetadata getLayerMetadata(final VariableMetadata variableMetadata)
            throws EdalLayerNotFoundException {
        DatasetVariableId key = new DatasetVariableId(variableMetadata.getDataset().getId(),
                variableMetadata.getId());
        if (layerMetadata.containsKey(key)) {
            return layerMetadata.get(key);
        } else {
            throw new EdalLayerNotFoundException(
                    "No layer exists for the variable: " + variableMetadata.getId()
                            + " in the dataset: " + variableMetadata.getDataset().getId());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public FeaturesAndMemberName getFeaturesForLayer(String layerName, PlottingDomainParams params)
            throws EdalException {
        String variable = layerNameMapper.getVariableIdFromLayerName(layerName);
        Collection<? extends DiscreteFeature<?, ?>> mapFeatures;
        if (cachingEnabled && featureCache != null) {
            CacheKey key = new CacheKey(layerName, params);
            @SuppressWarnings("rawtypes")
            Collection cached = featureCache.get(key);

            if (cached != null) {
                /*
                 * This is why we added the SuppressWarnings("unchecked").
                 */
                mapFeatures = (Collection<? extends DiscreteFeature<?, ?>>) cached;
            } else {
                mapFeatures = doExtraction(layerName, variable, params);
                try {
                    featureCache.put(key, mapFeatures);
                } catch (Exception e) {
                    log.error("Problem adding features to cache", e);
                    /*
                     * Just log and carry on - not caching isn't the end of the world
                     */
                }
            }
        } else {
            mapFeatures = doExtraction(layerName, variable, params);
        }
        return new FeaturesAndMemberName(mapFeatures, variable);
    }

    private Collection<? extends DiscreteFeature<?, ?>> doExtraction(String layerName,
            String variable, PlottingDomainParams params) {
        Dataset dataset = getDatasetFromLayerName(layerName);
        return GraphicsUtils.extractGeneralMapFeatures(dataset, variable, params);
    }

    private Dataset getDatasetFromLayerName(String layerName) {
        return getDatasetFromId(layerNameMapper.getDatasetIdFromLayerName(layerName));
    }

    public static class CacheKey implements Serializable {
        private static final long serialVersionUID = 1L;
        final String layerName;
        final PlottingDomainParams params;

        public CacheKey(String layerName, PlottingDomainParams params) {
            super();
            this.layerName = layerName;
            this.params = params;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((layerName == null) ? 0 : layerName.hashCode());
            result = prime * result + ((params == null) ? 0 : params.hashCode());
            return EdalCache.murmur3Finalize(result);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            CacheKey other = (CacheKey) obj;
            if (layerName == null) {
                if (other.layerName != null)
                    return false;
            } else if (!layerName.equals(other.layerName))
                return false;
            if (params == null) {
                if (other.params != null)
                    return false;
            } else if (!params.equals(other.params))
                return false;
            return true;
        }
    }

    private class DatasetVariableId {
        String datasetId;
        String variableId;

        public DatasetVariableId(String datasetId, String variableId) {
            super();
            this.datasetId = datasetId;
            this.variableId = variableId;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode();
            result = prime * result + ((datasetId == null) ? 0 : datasetId.hashCode());
            result = prime * result + ((variableId == null) ? 0 : variableId.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            DatasetVariableId other = (DatasetVariableId) obj;
            if (!getOuterType().equals(other.getOuterType()))
                return false;
            if (datasetId == null) {
                if (other.datasetId != null)
                    return false;
            } else if (!datasetId.equals(other.datasetId))
                return false;
            if (variableId == null) {
                if (other.variableId != null)
                    return false;
            } else if (!variableId.equals(other.variableId))
                return false;
            return true;
        }

        private DataCatalogue getOuterType() {
            return DataCatalogue.this;
        }
    }
}
