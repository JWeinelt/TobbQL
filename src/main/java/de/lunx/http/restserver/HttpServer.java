package de.lunx.http.restserver;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.lunx.Main;
import de.lunx.auth.AuthManager;
import de.lunx.auth.User;
import de.lunx.data.Configuration;
import de.lunx.data.DataManager;
import de.lunx.querying.QueryParser;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Slf4j
public class HttpServer {
    private final HttpStatus unauthorized = HttpStatus.UNAUTHORIZED;
    private final HttpStatus internalError = HttpStatus.INTERNAL_SERVER_ERROR;
    private final HttpStatus badRequest = HttpStatus.BAD_REQUEST;
    private final Gson GSON = new Gson();

    public void startServer() {
        Javalin app = Javalin.create()
                .post("/auth", ctx -> {
                    String authBasic = ctx.header("Authorization");
                    if (authBasic == null || !authBasic.startsWith("Basic ")) {
                        ctx.status(badRequest);
                        ctx.result(QueryError.error("Invalid headers", "Headers are invalid."));
                        ctx.skipRemainingHandlers();
                        return;
                    }
                    String base64 = authBasic.substring(6);

                    byte[] base64DecodedBytes = Base64.getDecoder().decode(base64);
                    String decodedString = new String(base64DecodedBytes);

                    User user = AuthManager.getInstance().getUser(decodedString.split(":")[0]);
                    if (user == null) {
                        ctx.result(QueryError.error("User not found", "This user does not exist."));
                        ctx.status(unauthorized); // 401
                        return;
                    }
                    if (!user.isActive()) {
                        ctx.result(QueryError.error("User disabled", "This user has been disabled by an administrator."));
                        ctx.status(unauthorized);
                        return;
                    }
                    if (!user.getHashedPassword().equals(decodedString.split(":")[1])) {
                        ctx.result(QueryError.error("Invalid password", "The password provided is invalid."));
                        ctx.status(unauthorized);
                        return;
                    }

                    JsonObject o = new JsonObject();
                    o.addProperty("success", true);
                    o.addProperty("token", Main.getInstance().getJwt().token(user.getUsername()));
                    o.addProperty("safeMode", Main.getInstance().getDataManager().getConfiguration().isSafeMode());
                    ctx.result(o.toString());
                })
                .before(ctx -> {
                    if (ctx.url().contains("auth")) return;
                    String token = ctx.header("Authorization");
                    if (token == null) {
                        ctx.result(QueryError.error("Token missing",
                                "Please provide a valid token to use the service."));
                        ctx.status(unauthorized);
                        ctx.skipRemainingHandlers();
                        return;
                    }
                    token = token.replace("Bearer ", "");
                    DecodedJWT decoded = Main.getInstance().getJwt().decode(token);
                    if (decoded.getExpiresAt().before(Date.from(Instant.now()))) {
                        ctx.result(QueryError.error("Token expired",
                                "This token cannot be used. Please request a new one."));
                        ctx.status(unauthorized);
                        ctx.skipRemainingHandlers();
                    }
                })
                .post("/query", ctx -> {
                    ctx.result(QueryParser.parseQuery(ctx.body()));
                })
                .start(Configuration.getInstance().getPort());
        log.info("Started HTTP server on port {}", Configuration.getInstance().getPort());
    }
}