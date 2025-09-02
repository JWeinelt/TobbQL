package de.lunx.querying;


import com.google.gson.*;
import de.lunx.auth.AuthManager;
import de.lunx.auth.Permission;
import de.lunx.auth.User;
import de.lunx.data.DataManager;
import de.lunx.data.obj.QueryCondition;
import de.lunx.data.obj.TColumnType;
import de.lunx.data.obj.TDatabase;
import de.lunx.data.obj.TTable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static de.lunx.Main.printStackTraceLevel;

@Slf4j
public class TQuery {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static QueryResult parse(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        Type type = null;
        try {
            type = Type.valueOf(o.get("type").getAsString().toUpperCase());
        } catch (IllegalArgumentException ex) {
            printStackTraceLevel(log, System.Logger.Level.DEBUG, ex);
            return new QueryResult(QueryResultType.UNKNOWN_ACTION, type, 0);
        }
        switch (type) {
            case GET_DATA -> {
                String dbName = o.get("database").getAsString();
                String tableName = o.get("table").getAsString();

                TDatabase database = DataManager.getInstance().getDatabase(dbName);
                if (database == null) {
                    return new QueryResult(QueryResultType.UNKNOWN_DB, type, 0);
                }
                TTable table = database.getTable(tableName);
                if (table == null)
                    return new QueryResult(QueryResultType.UNKNOWN_TABLE, type, 0);

                return new QueryResult(QueryResultType.RESULT_SET, type, 0,
                        table.getData());
            }
            case CLEAR_TABLE -> {
                String dbName = o.get("database").getAsString();
                String tableName = o.get("table").getAsString();

                TDatabase database = DataManager.getInstance().getDatabase(dbName);
                if (database == null) {
                    return new QueryResult(QueryResultType.UNKNOWN_DB, type, 0);
                }
                TTable table = database.getTable(tableName);
                if (table == null)
                    return new QueryResult(QueryResultType.UNKNOWN_TABLE, type, 0);

                int rows = table.getData().size();
                table.truncate();
                return new QueryResult(QueryResultType.SUCCESS, type, rows);
            }
            case UPDATE_DATA -> {
                List<QueryCondition> conditions = new ArrayList<>();
                for (JsonElement jE : o.get("conditions").getAsJsonArray()) {
                    conditions.add(GSON.fromJson(jE, QueryCondition.class));
                }

                if (conditions.isEmpty() && DataManager.getInstance().getConfiguration().isSafeMode()) {
                    return new QueryResult(QueryResultType.SAFE_MODE_ENABLED, type, 0);
                }

                String dbName = o.get("database").getAsString();
                String tableName = o.get("table").getAsString();

                HashMap<String, Object> newData = new HashMap<>();
                for (JsonElement uE : o.get("updates").getAsJsonArray()) {
                    JsonObject ob = uE.getAsJsonObject();
                    newData.put(ob.get("name").getAsString(), convertJsonObj(ob.get("value"),
                            TColumnType.valueOf(ob.get("type").getAsString().toUpperCase())));
                }

                TDatabase database = DataManager.getInstance().getDatabase(dbName);
                if (database == null) {
                    return new QueryResult(QueryResultType.UNKNOWN_DB, type, 0);
                }
                TTable table = database.getTable(tableName);
                if (table == null)
                    return new QueryResult(QueryResultType.UNKNOWN_TABLE, type, 0);

                AtomicInteger changedRows = new AtomicInteger();

                table.getThroughRowsWithIndex((row, index) -> {
                    for (QueryCondition condition : conditions) {
                        if (row.get(condition.getColumn()).equals(condition.getValue())) {
                            for (String col : table.getData().get(index).keySet()) {
                                if (newData.containsKey(col)) {
                                    row.put(col, newData.get(col));
                                    changedRows.getAndIncrement();
                                }
                            }
                        }
                    }
                });
                DataManager.getInstance().save(database);

                return new QueryResult(QueryResultType.SUCCESS, type, changedRows.get());
            }
            case DELETE_DATA -> {
                List<QueryCondition> conditions = new ArrayList<>();
                for (JsonElement jE : o.get("conditions").getAsJsonArray()) {
                    conditions.add(GSON.fromJson(jE, QueryCondition.class));
                }

                if (conditions.isEmpty() && DataManager.getInstance().getConfiguration().isSafeMode()) {
                    return new QueryResult(QueryResultType.SAFE_MODE_ENABLED, type, 0);
                }

                String dbName = o.get("database").getAsString();
                String tableName = o.get("table").getAsString();

                HashMap<String, Object> newData = new HashMap<>();
                for (JsonElement uE : o.get("updates").getAsJsonArray()) {
                    JsonObject ob = uE.getAsJsonObject();
                    newData.put(ob.get("name").getAsString(), convertJsonObj(ob.get("value"),
                            TColumnType.valueOf(ob.get("type").getAsString().toUpperCase())));
                }

                TDatabase database = DataManager.getInstance().getDatabase(dbName);
                if (database == null) {
                    return new QueryResult(QueryResultType.UNKNOWN_DB, type, 0);
                }
                TTable table = database.getTable(tableName);
                if (table == null)
                    return new QueryResult(QueryResultType.UNKNOWN_TABLE, type, 0);

                AtomicInteger changedRows = new AtomicInteger();

                table.getThroughRowsWithIndex((row, index) -> {
                    for (QueryCondition condition : conditions) {
                        if (row.get(condition.getColumn()).equals(condition.getValue())) {
                            for (String col : table.getData().get(index).keySet()) {
                                if (newData.containsKey(col)) {
                                    row.remove(col);
                                    changedRows.getAndIncrement();
                                }
                            }
                        }
                    }
                });

                DataManager.getInstance().save(database);

                return new QueryResult(QueryResultType.SUCCESS, type, changedRows.get());
            }
            case CREATE_DATABASE -> {
                String dbName = o.get("name").getAsString();
                Charset charset = StandardCharsets.UTF_8;
                if (o.has("charSet")) {
                    charset = Charset.forName(o.get("charSet").getAsString());
                }
                TDatabase database = DataManager.getInstance().createDatabase(dbName, charset);
                DataManager.getInstance().save(database);
                return new QueryResult(QueryResultType.SUCCESS, type, 1);
            }
            case CREATE_USER -> {
                String username = o.get("username").getAsString();
                String password = o.get("password").getAsString();

                List<Permission> permissions = new ArrayList<>();
                for (JsonElement p : o.get("permissions").getAsJsonArray()) {
                    permissions.add(Permission.valueOf(p.getAsString().toUpperCase()));
                }

                String role = o.get("role").getAsString();

                User user = new User(username, password).addPermissions(permissions);
                user.setRole(role);
                AuthManager.getInstance().register(user);
                return new QueryResult(QueryResultType.SUCCESS, type, 1);
            } case GET_TABLES -> {
                String dbName = o.get("database").getAsString();

                TDatabase database = DataManager.getInstance().getDatabase(dbName);
                if (database == null) {
                    return new QueryResult(QueryResultType.UNKNOWN_DB, type, 0);
                }

                List<HashMap<String, Object>> tables = new ArrayList<>();
                for (TTable t : database.getTables()) {
                    HashMap<String, Object> h = new HashMap<>();
                    h.put("tables", t.getName());
                    tables.add(h);
                }

                return new QueryResult(QueryResultType.RESULT_SET, type, 0, tables);
            } case GET_DATABASES -> {
                List<TDatabase> databases = DataManager.getInstance().getDatabases();

                List<HashMap<String, Object>> databaseList = new ArrayList<>();
                for (TDatabase t : databases) {
                    HashMap<String, Object> h = new HashMap<>();
                    h.put("dbName", t.getName());
                    databaseList.add(h);
                }
                return new QueryResult(QueryResultType.RESULT_SET, type, 0, databaseList);
            }
        }

        //TODO: add other types
        return new QueryResult(QueryResultType.FAILED, type, 0);
    }

    private static Object convertJsonObj(JsonElement element, TColumnType type) {
        switch (type) {
            case CHAR -> {
                return element.getAsCharacter();
            }
            case TEXT, UNIQUE_IDENTIFIER -> {
                return element.getAsString();
            }
            case INTEGER -> {
                return element.getAsInt();
            }
            case DECIMAL -> {
                return element.getAsDouble();
            }
            case BOOLEAN -> {
                return element.getAsBoolean();
            }
            //TODO Add others
        }
        return null;
    }

    public static class QueryResult {
        @Getter
        private final QueryResultType type;
        @Getter
        private final Type queryType;
        @Getter
        private final int rowsChanged;
        @Getter
        private final List<HashMap<String, Object>> resultSet;

        public QueryResult(QueryResultType type, Type queryType, int rowsChanged) {
            this.type = type;
            this.queryType = queryType;
            this.rowsChanged = rowsChanged;
            resultSet = new ArrayList<>();
        }

        public QueryResult(QueryResultType type, Type queryType, int rowsChanged, List<HashMap<String, Object>> resultSet) {
            this.type = type;
            this.queryType = queryType;
            this.rowsChanged = rowsChanged;
            this.resultSet = resultSet;
        }
    }

    public enum QueryResultType {
        SUCCESS,
        RESULT_SET,
        FAILED,
        ALREADY_EXISTS,
        SAFE_MODE_ENABLED,
        UNKNOWN_DB,
        UNKNOWN_TABLE,
        UNKNOWN_ACTION,
        EMPTY
    }

    public enum Type {
        GET_DATA(true, false),
        GET_TABLES(true, false),
        INSERT_DATA(false, true),
        UPDATE_DATA(false, true),
        DELETE_DATA(false, true),
        CREATE_TABLE(false, true),
        DELETE_TABLE(false, true),
        EDIT_TABLE(false, true),
        CLEAR_TABLE(false, true),
        GRANT_PERMISSION(false, true),
        CREATE_DATABASE(false, true),
        DELETE_DATABASE(false, true),

        CREATE_USER(false, true),
        DEACTIVATE_USER(false, true),
        EDIT_USER(false, true),
        DELETE_USER(false, true),

        GET_DATABASES(true, false),

        CREATE_ROLE(false, true),
        EDIT_ROLE(false, true),
        DELETE_ROLE(false, true),

        UNKNOWN(false, false);

        public final boolean returnsResultSet;
        public final boolean changesRows;

        Type(boolean returnsResultSet, boolean changesRows) {
            this.returnsResultSet = returnsResultSet;
            this.changesRows = changesRows;
        }
    }
}