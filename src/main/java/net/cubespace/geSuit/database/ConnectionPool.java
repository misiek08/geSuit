package net.cubespace.geSuit.database;

import net.cubespace.Yamler.Config.InvalidConfigurationException;
import net.cubespace.geSuit.geSuit;
import net.cubespace.geSuit.configs.SubConfig.Database;
import net.cubespace.geSuit.managers.ConfigManager;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

public class ConnectionPool {
    private Database dbConfig;
    private ArrayList<IRepository> repositories = new ArrayList<>();
    private ArrayList<ConnectionHandler> connections = new ArrayList<>();

    public void addRepository(IRepository repository) {
        repositories.add(repository);
    }

    public boolean initialiseConnections(Database database) {
        this.dbConfig = database;

        for (int i = 0; i < database.Threads; i++) {
            ConnectionHandler ch;

            try {
                Class.forName("com.mysql.jdbc.Driver");
                Connection connection = DriverManager.getConnection("jdbc:mysql://" + database.Host + ":" + database.Port + "/" + database.Database, database.Username, database.Password);

                ch = new ConnectionHandler(connection);
                for(IRepository repository : repositories) {
                    repository.registerPreparedStatements(ch);
                }
            } catch (SQLException | ClassNotFoundException ex) {
                System.out.println(ChatColor.DARK_RED + "SQL is unable to conect");
                ex.printStackTrace();
                throw new IllegalStateException();
            }

            connections.add(ch);
        }

        ProxyServer.getInstance().getScheduler().schedule(geSuit.instance, new Runnable() {
            public void run() {
                Iterator<ConnectionHandler> cons = connections.iterator();
                while (cons.hasNext()) {
                    ConnectionHandler con = cons.next();

                    if (!con.isUsed() && con.isOldConnection()) {
                        con.closeConnection();
                        cons.remove();
                    }
                }
            }
        }, 10, 10, TimeUnit.SECONDS);

        if (!ConfigManager.main.Inited) {
            for(IRepository repository : repositories) {
                String[] tableInformation = repository.getTable();

                if (!doesTableExist(tableInformation[0])) {
                    try {
                        standardQuery("CREATE TABLE IF NOT EXISTS `"+ tableInformation[0] +"` (" + tableInformation[1] + ");");
                    } catch (SQLException e) {
                        e.printStackTrace();
                        geSuit.instance.getLogger().severe("Could not create Table");
                        throw new IllegalStateException();
                    }
                }
            }

            ConfigManager.main.Inited = true;
            try {
                ConfigManager.main.save();
            } catch (InvalidConfigurationException e) {

            }
        } else {
            for(IRepository repository : repositories) {
                repository.checkUpdate();
            }
        }

        return true;
    }

    /**
     * @return Returns a free connection from the pool of connections. Creates a new connection if there are none available
     */
    public synchronized ConnectionHandler getConnection() {
        for (ConnectionHandler c : connections) {
            if (!c.isUsed()) {
                return c;
            }
        }

        // create a new connection as none are free
        ConnectionHandler ch;

        try {
            Class.forName("com.mysql.jdbc.Driver");
            Connection connection = DriverManager.getConnection("jdbc:mysql://" + dbConfig.Host + ":" + dbConfig.Port + "/" + dbConfig.Database, dbConfig.Username, dbConfig.Password);

            ch = new ConnectionHandler(connection);
            for(IRepository repository : repositories) {
                repository.registerPreparedStatements(ch);
            }
        } catch (SQLException | ClassNotFoundException ex) {
            System.out.println(ChatColor.DARK_RED + "SQL is unable to conect");
            return null;
        }

        connections.add(ch);

        return ch;

    }

    private void standardQuery(String query) throws SQLException {
        ConnectionHandler ch = getConnection();

        Statement statement = ch.getConnection().createStatement();
        statement.executeUpdate(query);
        statement.close();

        ch.release();
    }

    private boolean doesTableExist(String table) {
        ConnectionHandler ch = getConnection();
        boolean check = checkTable(table, ch.getConnection());
        ch.release();

        return check;
    }

    private boolean checkTable(String table, Connection connection) {
        DatabaseMetaData dbm = null;
        try {
            dbm = connection.getMetaData();
        } catch (SQLException e2) {
            e2.printStackTrace();
            return false;
        }

        ResultSet tables = null;
        try {
            tables = dbm.getTables(null, null, table, null);
        } catch (SQLException e1) {
            e1.printStackTrace();
            return false;
        }

        boolean check = false;
        try {
            check = tables.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }

        return check;
    }

    public void closeConnections() {
        for (ConnectionHandler c : connections) {
            c.closeConnection();
        }
    }
}