package de.lunx.data;

import com.google.gson.Gson;
import lombok.Getter;
import lombok.Setter;

import java.time.temporal.ChronoUnit;

@Getter
@Setter
public class Configuration {
    private int tokenExpirationTimeInterval = 1;
    private ChronoUnit tokenExpirationUnit = ChronoUnit.HOURS;

    private int port = 8765;
    private String hostAddress = "0.0.0.0";

    private boolean safeMode = true;


    private final String WARNING = "DON'T CHANGE THESE VALUES UNLESS YOU KNOW WHAT YOU ARE DOING!!!";
    private String jwtSecret = "";
    private String dataSecret = "";
    private String version = "1.0.0";

    public static Configuration getInstance() {
        return DataManager.getInstance().getConfiguration();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Configuration c)) return false;
        return c.jwtSecret.equals(jwtSecret)
                && c.tokenExpirationTimeInterval == tokenExpirationTimeInterval
                && c.tokenExpirationUnit.equals(tokenExpirationUnit);
    }

    @Override
    public int hashCode() {
        return jwtSecret.hashCode();
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
