package database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import oracle.jdbc.pool.OracleDataSource;

public class DatabaseConnection {
    private static final String url = "jdbc:oracle:thin:@//127.0.0.1:1521/XEPDB1?oracle.net.disableOob=true";
    private static final String DB_DRIVER = "oracle.jdbc.driver.OracleDriver";
    private static final String USERNAME = "CLUSION";
    private static final String PASSWORD = "oracle";

    private static final String CLEAR = "TRUNCATE TABLE %s";

    private static Connection dbConn;

    private DatabaseConnection() {
        try {
            OracleDataSource ds = new OracleDataSource();
            ds.setURL(url);
            dbConn = ds.getConnection(USERNAME, PASSWORD);
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
        }
    }

}
