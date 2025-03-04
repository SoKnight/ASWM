package com.grinderwolf.swm.plugin.config;

import lombok.Getter;
import lombok.Setter;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

@Getter @Setter
@ConfigSerializable
public class DatasourcesConfig {

    @Setting("file") private FileConfig fileConfig = new FileConfig();
    @Setting("mongodb") private MongoDBConfig mongoDbConfig = new MongoDBConfig();
    @Setting("mysql") private MysqlConfig mysqlConfig = new MysqlConfig();
    @Setting("redis") private RedisConfig redisConfig = new RedisConfig();

    @Getter @Setter
    @ConfigSerializable
    public static class FileConfig {

        @Setting("path") private String path = "slime_worlds";

    }

    @Getter @Setter
    @ConfigSerializable
    public static class MongoDBConfig {

        @Setting("enabled") private boolean enabled = false;

        @Setting("host") private String host = "127.0.0.1";
        @Setting("port") private int port = 27017;

        @Setting("auth") private String authSource = "admin";
        @Setting("username") private String username = "slimeworldmanager";
        @Setting("password") private String password = "";

        @Setting("database") private String database = "slimeworldmanager";
        @Setting("collection") private String collection = "worlds";

        @Setting("uri") private String uri = "";
    }

    @Getter @Setter
    @ConfigSerializable
    public static class MysqlConfig {

        @Setting("enabled") private boolean enabled = false;

        @Setting("host") private String host = "127.0.0.1";
        @Setting("port") private int port = 3306;

        @Setting("username") private String username = "slimeworldmanager";
        @Setting("password") private String password = "";

        @Setting("database") private String database = "slimeworldmanager";

        @Setting("usessl") private boolean usessl = false;

        @Setting("sqlUrl") private String sqlUrl = "jdbc:mysql://{host}:{port}/{database}?autoReconnect=true&allowMultiQueries=true&useSSL={usessl}";
    }

    @Getter @Setter
    @ConfigSerializable
    public static class RedisConfig {

        @Setting("enabled") private boolean enabled = false;
        @Setting("uri") private String uri = "redis://127.0.0.1/";

    }

}
