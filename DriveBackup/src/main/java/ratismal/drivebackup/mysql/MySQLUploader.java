/**
 * Uses code from the great library mysql-backup4j
 * https://github.com/SeunMatt/mysql-backup4j
 */

package ratismal.drivebackup.mysql;

import java.io.File;
import java.io.FileOutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import ratismal.drivebackup.util.MessageUtil;

public class MySQLUploader {
    private String host;
    private int port;
    private String username;
    private String password;
    private boolean useSsl;

    private boolean errorOccurred;
    
    private Statement stmt;

    static final String SQL_START_PATTERN = "-- start";
    static final String SQL_END_PATTERN = "-- end";

    /**
     * Creates an instance of the {@code mysqlUploader} object using the specified credentials
     * @param host the hostname of the MySQL database
     * @param port the port
     * @param username the username
     * @param password the password (leave blank if none)
     * @param useSsl whether to connect to the server using SSL/TLS
     */
    public MySQLUploader(String host, int port, String username, String password, boolean useSsl) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.useSsl = useSsl;
    }

    /**
     * Gets whether an error occurred while accessing the MySQL database
     * @return whether an error occurred
     */
    public boolean isErrorWhileUploading() {
        return errorOccurred;
    }

    /**
     * Downloads the specified MySQL database with the specified name into a folder for the specified database type
     * @param name the name of the MySQL database
     * @param type the type of database (ex. users, purchases)
     */
    public void downloadDatabase(String name, String type) {
        downloadDatabase(name, type, Collections.emptyList());
    }

    /**
     * Downloads the specified MySQL database with the specified name into a folder for the specified database type, excluding the specified tables
     * @param name the name of the MySQL database
     * @param type the type of database (ex. users, purchases)
     * @param blacklist a list of tables to not include
     */
    public void downloadDatabase(String name, String type, List<String> blacklist) {
        try {
            String connectionUrl = "jdbc:mysql://" + host + ":" + port + "/" + name + "?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC&useSSL=" + useSsl;
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection connection = DriverManager.getConnection(connectionUrl, username, password);

            stmt = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);

            String sql = getInsertStatements(name, blacklist);
    
            File outputFile = new java.io.File("external-backups" + File.separator + type);
            if (!outputFile.exists()) {
                outputFile.mkdirs();
            }
    
            FileOutputStream outputStream = new FileOutputStream(outputFile + File.separator + name + ".sql");
            outputStream.write(sql.getBytes());
            outputStream.close();
        } catch (Exception e) {
            MessageUtil.sendConsoleException(e);
            setErrorOccurred(true);
        }
    }

    /**
     * Gets the names of all the tables in the remote database
     * @param name the database's name
     * @return a list of the table names
     * @throws SQLException
     */
    private List<String> getAllTables(String name) throws SQLException {
        List<String> table = new ArrayList<>();
        ResultSet rs;
        rs = stmt.executeQuery("SHOW TABLE STATUS FROM `" + name + "`;");
        while ( rs.next() ) {
            table.add(rs.getString("Name"));
        }
        return table;
    }

    /**
     * Generate the SQL insert statement needed to create an empty table locally with the specified name
     * @param name the table's name
     * @return the SQL insert statement
     * @throws SQLException
     */
    private String getTableInsertStatement(String name) throws SQLException {

        StringBuilder sql = new StringBuilder();
        ResultSet rs;

        rs = stmt.executeQuery("SHOW CREATE TABLE " + "`" + name + "`;");
        while ( rs.next() ) {
            String qtbl = rs.getString(1);
            String query = rs.getString(2);
            sql.append("\n\n--");
            sql.append("\n").append(SQL_START_PATTERN).append("  table dump : ").append(qtbl);
            sql.append("\n--\n\n");

            sql.append(query).append(";\n\n");
        }

        sql.append("\n\n--");
        sql.append("\n").append(SQL_END_PATTERN).append("  table dump : ").append(name);
        sql.append("\n--\n\n");

        return sql.toString();
    }


    /**
     * Generates the SQL insert statements needed to copy all of the specified remote table's data to the local table
     * @param name the table's name
     * @return the SQL insert statement
     * @throws SQLException exception
     */
    private String getDataInsertStatement(String name) throws SQLException {

        StringBuilder sql = new StringBuilder();

        ResultSet rs = stmt.executeQuery("SELECT * FROM " + "`" + name + "`;");

        //move to the last row to get max rows returned
        rs.last();
        int rowCount = rs.getRow();

        //there are no records just return empty string
        if(rowCount <= 0) {
            return sql.toString();
        }

        sql.append("\n--").append("\n-- Inserts of ").append(name).append("\n--\n\n");

        //temporarily disable foreign key constraint
        sql.append("\n/*!40000 ALTER TABLE `").append(name).append("` DISABLE KEYS */;\n");

        sql.append("\n--\n")
                .append(SQL_START_PATTERN).append(" table insert : ").append(name)
                .append("\n--\n");

        sql.append("INSERT INTO `").append(name).append("`(");

        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        //generate the column names that are present
        //in the returned result set
        //at this point the insert is INSERT INTO (`col1`, `col2`, ...)
        for(int i = 0; i < columnCount; i++) {
            sql.append("`")
                    .append(metaData.getColumnName( i + 1))
                    .append("`, ");
        }

        //remove the last whitespace and comma
        sql.deleteCharAt(sql.length() - 1).deleteCharAt(sql.length() - 1).append(") VALUES \n");

        //now we're going to build the values for data insertion
        rs.beforeFirst();
        while(rs.next()) {
            sql.append("(");
            for(int i = 0; i < columnCount; i++) {

                int columnType = metaData.getColumnType(i + 1);
                int columnIndex = i + 1;

                //this is the part where the values are processed based on their type
                if(rs.getObject(columnIndex) == null) {
                    sql.append("").append(rs.getObject(columnIndex)).append(", ");
                }
                else if( columnType == Types.INTEGER || columnType == Types.TINYINT || columnType == Types.BIT) {
                    sql.append(rs.getInt(columnIndex)).append(", ");
                }
                else {

                    String val = rs.getString(columnIndex);
                   //escape the single quotes that might be in the value
                    val = val.replace("'", "\\'");

                    sql.append("'").append(val).append("', ");
                }
            }

            //now that we're done with a row
            //let's remove the last whitespace and comma
            sql.deleteCharAt(sql.length() - 1).deleteCharAt(sql.length() - 1);

            //if this is the last row, just append a closing
            //parenthesis otherwise append a closing parenthesis and a comma
            //for the next set of values
            if(rs.isLast()) {
                sql.append(")");
            } else {
                sql.append("),\n");
            }
        }

        //now that we are done processing the entire row
        //let's add the terminator
        sql.append(";");

        sql.append("\n--\n")
                .append(SQL_END_PATTERN).append(" table insert : ").append(name)
                .append("\n--\n");

        //enable FK constraint
        sql.append("\n/*!40000 ALTER TABLE `").append(name).append("` ENABLE KEYS */;\n");

        return sql.toString();
    }

    /**
     * Generates the SQL insert statements needed to recreate the specified remote database locally, excluding the specified tables
     * @param name the database's name
     * @param blacklist a list of tables to not include
     * @return the SQL insert statement
     * @throws SQLException exception
     */
    private String getInsertStatements(String name, List<String> blacklist) throws SQLException {

        StringBuilder sql = new StringBuilder();
        sql.append("--");
        sql.append("\n-- Generated by DriveBackupV2");
        sql.append("\n-- http://dev.bukkit.org/projects/drivebackupv2");
        sql.append("\n-- Date: ").append(new SimpleDateFormat("h:mm M/d/yyyy").format(new Date()));
        sql.append("\n--");

        //these declarations are extracted from HeidiSQL
        sql.append("\n\n/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;")
                .append("\n/*!40101 SET NAMES utf8 */;")
                .append("\n/*!50503 SET NAMES utf8mb4 */;")
                .append("\n/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;")
                .append("\n/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;");

        //get the tables that are in the database
        List<String> tables = getAllTables(name);

        //for every table, get the table creation and data
        // insert statement
        for (String table: tables) {
            if (blacklist.contains(table)) {
                continue;
            }

            try {
                sql.append(getTableInsertStatement(table.trim()));
                sql.append(getDataInsertStatement(table.trim()));
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        sql.append("\n/*!40101 SET SQL_MODE=IFNULL(@OLD_SQL_MODE, '') */;")
                .append("\n/*!40014 SET FOREIGN_KEY_CHECKS=IF(@OLD_FOREIGN_KEY_CHECKS IS NULL, 1, @OLD_FOREIGN_KEY_CHECKS) */;")
                .append("\n/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;");

        return sql.toString();
    }

    /**
     * Sets whether an error occurred while accessing the MySQL database
     * @param errorOccurredValue whether an error occurred
     */
    private void setErrorOccurred(boolean errorOccurredValue) {
        errorOccurred = errorOccurredValue;
    }
}