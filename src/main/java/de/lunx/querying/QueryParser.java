package de.lunx.querying;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;

import static de.lunx.Main.printStackTraceLevel;

@Slf4j
public class QueryParser {
    private static final Gson GSON = new Gson();

    public static String parseQuery(String query) {
        JsonObject o = new JsonObject();

        TQuery.QueryResult result = TQuery.parse(query);
        TQuery.Type type = result.getQueryType();

        o.addProperty("queryType", type.name());
        o.addProperty("result", result.getType().name());
        o.addProperty("changedRows", (type.changesRows) ? result.getRowsChanged() : 0);
        if (type.returnsResultSet) o.add("resultSet", GSON.toJsonTree(result.getResultSet()));
        return o.toString();
    }
}
