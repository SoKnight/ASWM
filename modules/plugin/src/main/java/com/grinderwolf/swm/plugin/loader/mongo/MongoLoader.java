package com.grinderwolf.swm.plugin.loader.mongo;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.grinderwolf.swm.api.exception.UnknownWorldException;
import com.grinderwolf.swm.api.exception.WorldInUseException;
import com.grinderwolf.swm.plugin.SWMPlugin;
import com.grinderwolf.swm.plugin.config.DatasourcesConfig;
import com.grinderwolf.swm.plugin.loader.LoaderUtils;
import com.grinderwolf.swm.plugin.loader.UpdatableLoader;
import com.grinderwolf.swm.plugin.logging.Logging;
import com.mongodb.MongoException;
import com.mongodb.MongoNamespace;
import com.mongodb.client.*;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MongoLoader extends UpdatableLoader {

    // World locking executor service
    private static final ScheduledExecutorService SERVICE = Executors.newScheduledThreadPool(2, new ThreadFactoryBuilder().setNameFormat("SWM MongoDB Lock Pool Thread #%1$d").build());

    private final String database;
    private final String collection;
    private final MongoClient client;
    private final Map<String, ScheduledFuture<?>> lockedWorlds;

    public MongoLoader(DatasourcesConfig.MongoDBConfig config) throws MongoException {
        String authParams = !config.getUsername().isEmpty() && !config.getPassword().isEmpty() ? config.getUsername() + ":" + config.getPassword() + "@" : "";
        String authSource = !config.getAuthSource().isEmpty() ? "/?authSource=" + config.getAuthSource() : "";
        String uri = !config.getUri().isEmpty() ? config.getUri() : "mongodb://" + authParams + config.getHost() + ":" + config.getPort() + authSource;

        this.database = config.getDatabase();
        this.collection = config.getCollection();
        this.client = MongoClients.create(uri);
        this.lockedWorlds = new HashMap<>();

        MongoDatabase mongoDatabase = client.getDatabase(database);
        MongoCollection<Document> mongoCollection = mongoDatabase.getCollection(collection);
        mongoCollection.createIndex(Indexes.ascending("name"), new IndexOptions().unique(true));
    }

    @Override
    public void update() {
        MongoDatabase mongoDatabase = client.getDatabase(database);

        // Old GridFS importing
        for (String collectionName : mongoDatabase.listCollectionNames()) {
            if (collectionName.equals(collection + "_files.files") || collectionName.equals(collection + "_files.chunks")) {
                SWMPlugin.logger().info("Updating MongoDB database...");
                long startTime = System.currentTimeMillis();

                mongoDatabase.getCollection(collection + "_files.files").renameCollection(new MongoNamespace(database, collection + ".files"));
                mongoDatabase.getCollection(collection + "_files.chunks").renameCollection(new MongoNamespace(database, collection + ".chunks"));

                long timeTaken = System.currentTimeMillis() - startTime;
                Logging.info("MongoDB database updated in %d ms.", timeTaken);
                break;
            }
        }

        MongoCollection<Document> mongoCollection = mongoDatabase.getCollection(collection);

        // Old world lock importing
        try (MongoCursor<Document> documents = mongoCollection.find(Filters.or(
                Filters.eq("locked", true),
                Filters.eq("locked", false)
        )).cursor()) {
            if (documents.hasNext()) {
                Logging.warn("Your SWM MongoDB database is outdated. The update process will start in 10 seconds.");
                Logging.warn("Note that this update will make your database incompatible with older SWM versions.");
                Logging.warn("Make sure no other servers with older SWM versions are using this database.");
                Logging.warn("Shut down the server to prevent your database from being updated.");

                try {
                    Thread.sleep(10000L);
                } catch (InterruptedException ignored) {
                }

                while (documents.hasNext()) {
                    String worldName = documents.next().getString("name");
                    mongoCollection.updateOne(Filters.eq("name", worldName), Updates.set("locked", 0L));
                }
            }
        }
    }

    @Override
    public byte[] loadWorld(String worldName, boolean readOnly) throws UnknownWorldException, IOException, WorldInUseException {
        try {
            MongoDatabase mongoDatabase = client.getDatabase(database);
            MongoCollection<Document> mongoCollection = mongoDatabase.getCollection(collection);

            Document worldDoc = mongoCollection.find(Filters.eq("name", worldName)).first();
            if (worldDoc == null)
                throw new UnknownWorldException(worldName);

            if (!readOnly) {
                long lockedMillis = worldDoc.getLong("locked");
                if (System.currentTimeMillis() - lockedMillis <= LoaderUtils.MAX_LOCK_TIME)
                    throw new WorldInUseException(worldName);

                updateLock(worldName, true);
            }

            GridFSBucket bucket = GridFSBuckets.create(mongoDatabase, collection);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bucket.downloadToStream(worldName, stream);
            return stream.toByteArray();
        } catch (MongoException ex) {
            throw new IOException(ex);
        }
    }

    private void updateLock(String worldName, boolean forceSchedule) {
        try {
            MongoDatabase mongoDatabase = client.getDatabase(database);
            MongoCollection<Document> mongoCollection = mongoDatabase.getCollection(collection);
            mongoCollection.updateOne(Filters.eq("name", worldName), Updates.set("locked", System.currentTimeMillis()));
        } catch (MongoException ex) {
            Logging.error("Failed to update lock for world '%s'!".formatted(worldName), ex);
        }

        if (forceSchedule || lockedWorlds.containsKey(worldName)) { // Only schedule another update if the world is still on the map
            lockedWorlds.put(worldName, SERVICE.schedule(() -> updateLock(worldName, false), LoaderUtils.LOCK_INTERVAL, TimeUnit.MILLISECONDS));
        }
    }

    @Override
    public boolean worldExists(String worldName) throws IOException {
        try {
            MongoDatabase mongoDatabase = client.getDatabase(database);
            MongoCollection<Document> mongoCollection = mongoDatabase.getCollection(collection);
            Document worldDoc = mongoCollection.find(Filters.eq("name", worldName)).first();
            return worldDoc != null;
        } catch (MongoException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public List<String> listWorlds() throws IOException {
        List<String> worldList = new ArrayList<>();

        try {
            MongoDatabase mongoDatabase = client.getDatabase(database);
            MongoCollection<Document> mongoCollection = mongoDatabase.getCollection(collection);

            try (MongoCursor<Document> documents = mongoCollection.find().cursor()) {
                while (documents.hasNext()) {
                    worldList.add(documents.next().getString("name"));
                }
            }
        } catch (MongoException ex) {
            throw new IOException(ex);
        }

        return worldList;
    }

    @Override
    public void saveWorld(String worldName, byte[] serializedWorld, boolean lock) throws IOException {
        try {
            MongoDatabase mongoDatabase = client.getDatabase(database);
            GridFSBucket bucket = GridFSBuckets.create(mongoDatabase, collection);
            bucket.uploadFromStream(worldName, new ByteArrayInputStream(serializedWorld));

            GridFSFile oldFile = bucket.find(Filters.eq("filename", worldName)).first();
            if (oldFile != null)
                bucket.delete(oldFile.getObjectId());

            MongoCollection<Document> mongoCollection = mongoDatabase.getCollection(collection);

            Document worldDoc = mongoCollection.find(Filters.eq("name", worldName)).first();
            if (worldDoc == null) {
                long lockMillis = lock ? System.currentTimeMillis() : 0L;
                mongoCollection.insertOne(new Document().append("name", worldName).append("locked", lockMillis));
            } else if (System.currentTimeMillis() - worldDoc.getLong("locked") > LoaderUtils.MAX_LOCK_TIME && lock) {
                updateLock(worldName, true);
            }
        } catch (MongoException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public void unlockWorld(String worldName) throws IOException, UnknownWorldException {
        ScheduledFuture<?> future = lockedWorlds.remove(worldName);
        if (future != null)
            future.cancel(false);

        try {
            MongoDatabase mongoDatabase = client.getDatabase(database);
            MongoCollection<Document> mongoCollection = mongoDatabase.getCollection(collection);

            UpdateResult result = mongoCollection.updateOne(Filters.eq("name", worldName), Updates.set("locked", 0L));
            if (result.getMatchedCount() == 0) {
                throw new UnknownWorldException(worldName);
            }
        } catch (MongoException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public boolean isWorldLocked(String worldName) throws IOException, UnknownWorldException {
        if (lockedWorlds.containsKey(worldName))
            return true;

        try {
            MongoDatabase mongoDatabase = client.getDatabase(database);
            MongoCollection<Document> mongoCollection = mongoDatabase.getCollection(collection);

            Document worldDoc = mongoCollection.find(Filters.eq("name", worldName)).first();
            if (worldDoc == null)
                throw new UnknownWorldException(worldName);

            return System.currentTimeMillis() - worldDoc.getLong("locked") <= LoaderUtils.MAX_LOCK_TIME;
        } catch (MongoException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public void deleteWorld(String worldName) throws IOException, UnknownWorldException {
        ScheduledFuture<?> future = lockedWorlds.remove(worldName);
        if (future != null)
            future.cancel(false);

        try {
            MongoDatabase mongoDatabase = client.getDatabase(database);
            GridFSBucket bucket = GridFSBuckets.create(mongoDatabase, collection);

            GridFSFile file = bucket.find(Filters.eq("filename", worldName)).first();
            if (file == null)
                throw new UnknownWorldException(worldName);

            bucket.delete(file.getObjectId());

            // Delete backup file
            for (GridFSFile backupFile : bucket.find(Filters.eq("filename", worldName + "_backup")))
                bucket.delete(backupFile.getObjectId());

            MongoCollection<Document> mongoCollection = mongoDatabase.getCollection(collection);
            mongoCollection.deleteOne(Filters.eq("name", worldName));
        } catch (MongoException ex) {
            throw new IOException(ex);
        }
    }

}
