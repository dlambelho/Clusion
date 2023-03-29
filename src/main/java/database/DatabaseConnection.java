package database;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import oracle.jdbc.driver.OracleDriver;
import oracle.jdbc.pool.OracleDataSource;

public class DatabaseConnection {
    private static final String url = "jdbc:oracle:thin:@//127.0.0.1:1521/XEPDB1?oracle.net.disableOob=true";
    private static final String DB_DRIVER = "oracle.jdbc.driver.OracleDriver";
    private static final String USERNAME = "CLUSION";
    private static final String PASSWORD = "oracle";

    private static Connection dbConn;

    private DatabaseConnection() {
        try {
            OracleDataSource ds = new OracleDataSource();
            ds.setURL(url);
            //DriverManager.registerDriver(new OracleDriver());
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

}
