package de.lunx.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.lunx.Main;
import de.lunx.auth.User;
import de.lunx.data.obj.TDatabase;
import de.lunx.data.obj.TTable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static de.lunx.Main.printStackTraceLevel;

@Slf4j
public class DataManager {
    private final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(JsonDate.class, new JsonDateAdapter())
            .setPrettyPrinting().create();

    private final File configFile;
    private final File tablesFolder;
    private final File dataBaseFolder;

    @Getter
    private Configuration configuration = new Configuration();

    @Getter
    private final List<TDatabase> databases = new ArrayList<>();

    private DataManager(File baseDirectory) {
        if (!baseDirectory.exists()) if (baseDirectory.mkdirs()) log.debug("Created base data directory.");
        configFile = new File(baseDirectory.getParent(), "config.json");
        tablesFolder = new File(baseDirectory, "tables");
        dataBaseFolder = new File(baseDirectory, "databases");
    }

    public static DataManager create(File file) {
        return new DataManager(file);
    }
    public static DataManager getInstance() {
        return Main.getInstance().getDataManager();
    }


    public void loadData() {
        File[] databases = dataBaseFolder.listFiles();
        if (databases == null) return;
        for (File f : databases) {
            try {
                TDatabase database = EncryptUtil.loadObject(f.getPath(), TDatabase.class);
                if (database == null) {
                    log.error("Database not found.");
                    return;
                }
                this.databases.add(database);

                // ######### LOAD TABLES ##########

                for (UUID u : database.getTablesRaw()) {
                    File tableStoreFile = new File(tablesFolder, u.toString() + ".tbb");
                    TTable table = EncryptUtil.loadObject(tableStoreFile.getPath(), TTable.class);
                    database.registerTTable(table);
                }
            } catch (NoSuchFileException ex) {
                printStackTraceLevel(log, System.Logger.Level.DEBUG, ex);
            }
        }
    }

    public void saveAll() {
        for (TDatabase b : databases) {
            save(b);
        }
    }

    public void save(TDatabase b) {
        EncryptUtil.saveObject(dataBaseFolder, b.getName() + ".tdb", b);

        // ### SAVE TABLES ###
        for (TTable t : b.getTables()) {
            EncryptUtil.saveObject(tablesFolder, t.getUniqueID() + ".tbb", t);
        }
    }

    public void save(TTable table) {
        TDatabase db = getDatabase(table.getDataBase());
        if (db == null) return;
        if (!db.hasTable(table.getName())) {
            db.registerOrUpdateTTable(table);
        }
        save(db);
    }


    public TDatabase createDatabase(String name) {
        TDatabase db = new TDatabase(name, StandardCharsets.UTF_8);
        databases.add(db);
        save(db);
        return db;
    }


    public TDatabase createDatabase(String name, Charset charset) {
        TDatabase db = new TDatabase(name, charset);
        databases.add(db);
        save(db);
        return db;
    }

    public TTable createTable(String database, String name) {
        return new TTable(database, name);
    }

    public TDatabase getDatabase(String name) {
        for (TDatabase d : databases) if (d.getName().equals(name)) return d;
        return null;
    }





    public void loadConfig() {
        try (BufferedReader br = new BufferedReader(new FileReader(configFile))) {
            StringBuilder jsonStringBuilder = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                jsonStringBuilder.append(line);
            }
            configuration = GSON.fromJson(jsonStringBuilder.toString(),
                    new TypeToken<Configuration>(){}.getType());
        } catch (IOException e) {
            log.error(e.getMessage());
            saveConfig();
        }
    }

    public void saveConfig() {
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write(GSON.toJson(configuration));
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
}
