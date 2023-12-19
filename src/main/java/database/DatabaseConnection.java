package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DatabaseConnection {
    private static final String url = "jdbc:postgresql://localhost:5432/postgres";
    private static final String USERNAME = "postgres";
    private static final String PASSWORD = "oracle";

    private static final String CLEAR = "TRUNCATE TABLE %s";

    private static Connection dbConn;

    private DatabaseConnection() {
        try {
            dbConn = DriverManager.getConnection(url, USERNAME, PASSWORD);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static Connection getInstance() {
        if(dbConn == null)
            new DatabaseConnection();

        return dbConn;
    }

    public static void clearDatabase() {
        if (dbConn != null) {
            try (PreparedStatement statement = dbConn.prepareStatement(String.format(CLEAR, "GLOBAL_MAP"))) {
                statement.execute();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            try (PreparedStatement statement = dbConn.prepareStatement(String.format(CLEAR, "LOCAL_MAPS"))) {
                statement.execute();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            try (PreparedStatement statement = dbConn.prepareStatement(String.format(CLEAR, "BXT_TSET"))) {
                statement.execute();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            try (PreparedStatement statement = dbConn.prepareStatement(String.format(CLEAR, "BXT_XSET"))) {
                statement.execute();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void main(String[] args) {
        try(PreparedStatement state = getInstance().prepareStatement("INSERT into \"GLOBAL_MAP\" values (?,?)")) {
            state.setString(1, "word");
            state.setBytes(2, new byte[] {(byte) 0x1232});
            state.execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

}
