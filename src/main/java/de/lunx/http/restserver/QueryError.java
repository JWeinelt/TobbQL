package de.lunx.http.restserver;

import com.google.gson.JsonObject;

public record QueryError(String title, String description) {
    public static String error(String title, String description) {
        JsonObject o = new JsonObject();
        o.addProperty("title", title);
        o.addProperty("description", description);
        o.addProperty("success", false);
        return o.toString();
    }
}