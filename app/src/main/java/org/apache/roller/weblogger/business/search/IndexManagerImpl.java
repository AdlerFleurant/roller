/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  The ASF licenses this file to You
 * under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.  For additional information regarding
 * copyright in this work, please see the NOTICE file in the top level
 * directory of this distribution.
 */

package org.apache.roller.weblogger.business.search;

import java.io.File;
import java.io.IOException;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.beanutils.ConstructorUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.miscellaneous.LimitTokenCountAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;
import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.InitializationException;
import org.apache.roller.weblogger.business.Weblogger;
import org.apache.roller.weblogger.business.search.operations.AddEntryOperation;
import org.apache.roller.weblogger.business.search.operations.IndexOperation;
import org.apache.roller.weblogger.business.search.operations.ReIndexEntryOperation;
import org.apache.roller.weblogger.business.search.operations.RebuildWebsiteIndexOperation;
import org.apache.roller.weblogger.business.search.operations.RemoveEntryOperation;
import org.apache.roller.weblogger.business.search.operations.RemoveWebsiteIndexOperation;
import org.apache.roller.weblogger.business.search.operations.WriteToIndexOperation;
import org.apache.roller.weblogger.pojos.WeblogEntry;
import org.apache.roller.weblogger.pojos.Weblog;
import org.apache.roller.weblogger.config.WebloggerConfig;

/**
 * Lucene implementation of IndexManager. This is the central entry point into
 * the Lucene searching API.
 * 
 * @author Mindaugas Idzelis (min@idzelis.com)
 * @author mraible (formatting and making indexDir configurable)
 */
@com.google.inject.Singleton
public class IndexManagerImpl implements IndexManager {
    // ~ Static fields/initializers
    // =============================================

    private IndexReader reader;
    private final Weblogger roller;

    static Log mLogger = LogFactory.getFactory().getInstance(
            IndexManagerImpl.class);

    // ~ Instance fields
    // ========================================================

    private boolean searchEnabled = true;

    File indexConsistencyMarker;

    private boolean useRAMIndex = false;

    private RAMDirectory fRAMindex;

    private String indexDir;

    private boolean inconsistentAtStartup = false;

    private ReadWriteLock rwl = new ReentrantReadWriteLock();

    // ~ Constructors
    // ===========================================================

    /**
     * Creates a new lucene index manager. This should only be created once.
     * Creating the index manager more than once will definitely result in
     * errors. The preferred way of getting an index is through the
     * RollerContext.
     * 
     * @param roller - the weblogger instance
     */
    @com.google.inject.Inject
    protected IndexManagerImpl(Weblogger roller) {
        this.roller = roller;

        // check config to see if the internal search is enabled
        String enabled = WebloggerConfig.getProperty("search.enabled");
        if ("false".equalsIgnoreCase(enabled)) {
            this.searchEnabled = false;
        }

        // we also need to know what our index directory is
        // Note: system property expansion is now handled by WebloggerConfig
        String searchIndexDir = WebloggerConfig.getProperty("search.index.dir");
        this.indexDir = searchIndexDir.replace('/', File.separatorChar);

        // a little debugging
        mLogger.info("search enabled: " + this.searchEnabled);
        mLogger.info("index dir: " + this.indexDir);

        String test = indexDir + File.separator + ".index-inconsistent";
        indexConsistencyMarker = new File(test);
    }

    /**
     * @inheritDoc
     */
    public void initialize() throws InitializationException {

        // only initialize the index if search is enabled
        if (this.searchEnabled) {

            // 1. If inconsistency marker exists.
            // Delete index
            // 2. if we're using RAM index
            // load ram index wrapper around index
            //
            if (indexConsistencyMarker.exists()) {
                getFSDirectory(true);
                inconsistentAtStartup = true;
                mLogger.debug("Index inconsistent: marker exists");
            } else {
                try {
                    File makeIndexDir = new File(indexDir);
                    if (!makeIndexDir.exists()) {
                        makeIndexDir.mkdirs();
                        inconsistentAtStartup = true;
                        mLogger.debug("Index inconsistent: new");
                    }
                    indexConsistencyMarker.createNewFile();
                } catch (IOException e) {
                    mLogger.error(e);
                }
            }

            if (indexExists()) {
                if (useRAMIndex) {
                    Directory filesystem = getFSDirectory(false);
                    try {
                        fRAMindex = new RAMDirectory(filesystem, IOContext.DEFAULT);
                    } catch (IOException e) {
                        mLogger.error("Error creating in-memory index", e);
                    }
                }
            } else {
                mLogger.debug("Creating index");
                inconsistentAtStartup = true;
                if (useRAMIndex) {
                    fRAMindex = new RAMDirectory();
                    createIndex(fRAMindex);
                } else {
                    createIndex(getFSDirectory(true));
                }
            }

            if (isInconsistentAtStartup()) {
                mLogger.info("Index was inconsistent. Rebuilding index in the background...");
                try {
                    rebuildWebsiteIndex();
                } catch (WebloggerException e) {
                    mLogger.error("ERROR: scheduling re-index operation");
                }
            } else {
                mLogger.info("Index initialized and ready for use.");
            }
        }

    }

    // ~ Methods
    // ================================================================

    public void rebuildWebsiteIndex() throws WebloggerException {
        scheduleIndexOperation(new RebuildWebsiteIndexOperation(roller, this,
                null));
    }

    public void rebuildWebsiteIndex(Weblog website) throws WebloggerException {
        scheduleIndexOperation(new RebuildWebsiteIndexOperation(roller, this,
                website));
    }

    public void removeWebsiteIndex(Weblog website) throws WebloggerException {
        scheduleIndexOperation(new RemoveWebsiteIndexOperation(roller, this,
                website));
    }

    public void addEntryIndexOperation(WeblogEntry entry)
            throws WebloggerException {
        AddEntryOperation addEntry = new AddEntryOperation(roller, this, entry);
        scheduleIndexOperation(addEntry);
    }

    public void addEntryReIndexOperation(WeblogEntry entry)
            throws WebloggerException {
        ReIndexEntryOperation reindex = new ReIndexEntryOperation(roller, this,
                entry);
        scheduleIndexOperation(reindex);
    }

    public void removeEntryIndexOperation(WeblogEntry entry)
            throws WebloggerException {
        RemoveEntryOperation removeOp = new RemoveEntryOperation(roller, this,
                entry);
        executeIndexOperationNow(removeOp);
    }

    public ReadWriteLock getReadWriteLock() {
        return rwl;
    }

    public boolean isInconsistentAtStartup() {
        return inconsistentAtStartup;
    }

    /**
     * This is the analyzer that will be used to tokenize comment text.
     * 
     * @return Analyzer to be used in manipulating the database.
     */
    public static Analyzer getAnalyzer() {
        return instantiateAnalyzer(FieldConstants.LUCENE_VERSION);
    }

    private static Analyzer instantiateAnalyzer(final Version luceneVersion) {
        final String className = WebloggerConfig.getProperty("lucene.analyzer.class");
        try {
            final Class<?> clazz = Class.forName(className);
            return (Analyzer) ConstructorUtils.invokeConstructor(clazz, luceneVersion);
        } catch (final ClassNotFoundException e) {
            mLogger.error("failed to lookup analyzer class: " + className, e);
            return instantiateDefaultAnalyzer(luceneVersion);
        } catch (final NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            mLogger.error("failed to instantiate analyzer: " + className, e);
            return instantiateDefaultAnalyzer(luceneVersion);
        }
    }

    private static Analyzer instantiateDefaultAnalyzer(final Version luceneVersion) {
        return new StandardAnalyzer(luceneVersion);
    }

    private void scheduleIndexOperation(final IndexOperation op) {
        try {
            // only if search is enabled
            if (this.searchEnabled) {
                mLogger.debug("Starting scheduled index operation: "
                        + op.getClass().getName());
                roller.getThreadManager().executeInBackground(op);
            }
        } catch (InterruptedException e) {
            mLogger.error("Error executing operation", e);
        }
    }

    /**
     * @param op
     */
    public void executeIndexOperationNow(final IndexOperation op) {
        try {
            // only if search is enabled
            if (this.searchEnabled) {
                mLogger.debug("Executing index operation now: "
                        + op.getClass().getName());
                roller.getThreadManager().executeInForeground(op);
            }
        } catch (InterruptedException e) {
            mLogger.error("Error executing operation", e);
        }
    }

    public synchronized void resetSharedReader() {
        reader = null;
    }

    public synchronized IndexReader getSharedIndexReader() {
        if (reader == null) {
            try {
                reader = DirectoryReader.open(getIndexDirectory());
            } catch (IOException ignored) {
            }
        }
        return reader;
    }

    /**
     * Get the directory that is used by the lucene index. This method will
     * return null if there is no index at the directory location. If we are
     * using a RAM index, the directory will be a ram directory.
     * 
     * @return Directory The directory containing the index, or null if error.
     */
    public Directory getIndexDirectory() {
        if (useRAMIndex) {
            return fRAMindex;
        } else {
            return getFSDirectory(false);
        }
    }

    private boolean indexExists() {
        try {
            return DirectoryReader.indexExists(getIndexDirectory());
        } catch (IOException e) {
            mLogger.error("Problem accessing index directory", e);
        }
        return false;
    }

    private Directory getFSDirectory(boolean delete) {

        Directory directory = null;

        try {

            directory = FSDirectory.open(new File(indexDir));

            if (delete && directory != null) {
                // clear old files
                String[] files = directory.listAll();
                for (String s : files) {
                    File file = new File(indexDir, s);
                    if (!file.delete()) {
                        throw new IOException("couldn't delete " + s);
                    }
                }
            }

        } catch (IOException e) {
            mLogger.error("Problem accessing index directory", e);
        }

        return directory;

    }

    private void createIndex(Directory dir) {
        IndexWriter writer = null;

        try {

            IndexWriterConfig config = new IndexWriterConfig(
                    FieldConstants.LUCENE_VERSION, new LimitTokenCountAnalyzer(
                            IndexManagerImpl.getAnalyzer(),
                            IndexWriterConfig.DEFAULT_TERM_INDEX_INTERVAL));

            writer = new IndexWriter(dir, config);

        } catch (IOException e) {
            mLogger.error("Error creating index", e);
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException ignored) {
            }
        }
    }

    private IndexOperation getSaveIndexOperation() {
        return new WriteToIndexOperation(this) {
            public void doRun() {
                Directory dir = getIndexDirectory();
                Directory fsdir = getFSDirectory(true);
                IndexWriter writer = null;
                try {
                    IndexWriterConfig config = new IndexWriterConfig(FieldConstants.LUCENE_VERSION,
                            new LimitTokenCountAnalyzer(IndexManagerImpl.getAnalyzer(),
                                    IndexWriterConfig.DEFAULT_TERM_INDEX_INTERVAL));
                    writer = new IndexWriter(fsdir, config);
                    writer.addIndexes(dir);
                    writer.commit();
                    indexConsistencyMarker.delete();
                } catch (IOException e) {
                    mLogger.error("Problem saving index to disk", e);
                    // Delete the directory, since there was a problem saving the RAM contents
                    getFSDirectory(true);
                } finally {
                    try {
                        if (writer != null) {
                            writer.close();
                        }
                    } catch (IOException e1) {
                        mLogger.warn("Unable to close IndexWriter.");
                    }
                }
            }
        };
    }

    public void release() {
        // no-op
    }

    public void shutdown() {
        if (useRAMIndex) {
            scheduleIndexOperation(getSaveIndexOperation());
        } else {
            indexConsistencyMarker.delete();
        }

        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException e) {
            // won't happen, since it was
        }
    }

}
