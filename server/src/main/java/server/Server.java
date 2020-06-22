package server;


import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.List;
import java.util.Vector;
import java.util.jar.JarOutputStream;


public class Server {
    private final List<ClientHandler> clients;
    private final AuthService authService;

    private static Connection connection;
    private static Statement stmt;
    private static PreparedStatement psSelectUsers;
    private static PreparedStatement psUniqueLoginUser;
    private static PreparedStatement psUpdateNick;
    private static PreparedStatement psInsertUser;
    private static PreparedStatement psInsertMsg;
    private static PreparedStatement psSelectIdByLogin;
    private static PreparedStatement psSelectIdByNick;
    private static PreparedStatement psSelectMessages;

    public static PreparedStatement getPsSelectMessages() {
        return psSelectMessages;
    }

    public static PreparedStatement getPsSelectIdByNick() {
        return psSelectIdByNick;
    }

    public static PreparedStatement getPsSelectIdByLogin() {
        return psSelectIdByLogin;
    }

    public static PreparedStatement getPsInsertMsg() {
        return psInsertMsg;
    }

    public static PreparedStatement getPsUniqueLoginUser() {
        return psUniqueLoginUser;
    }

    public static PreparedStatement getPsInsertUser() {
        return psInsertUser;
    }

    public static PreparedStatement getPsSelectUsers() {
        return psSelectUsers;
    }

    public static Connection getConnection() {
        return connection;
    }

    public static Statement getStmt() {
        return stmt;
    }

    public static void connect() throws ClassNotFoundException, SQLException {
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:chat.db");
        stmt = connection.createStatement();
    }

    public static void disconnect() {
        try {
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        try {
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void prepareAllStatements() throws SQLException {
        psSelectUsers = connection.prepareStatement("SELECT nick FROM users WHERE login = ? and pass = ?");
        psUniqueLoginUser = connection.prepareStatement("SELECT count(login) countlogin FROM users WHERE login = ?");
        psUpdateNick = connection.prepareStatement("UPDATE users SET nick = ? WHERE login = ?");
        psInsertUser = connection.prepareStatement("INSERT INTO users (login, pass, nick) VALUES (?, ?, ?)");
        psInsertMsg = connection.prepareStatement("INSERT INTO history_chat (message, sender, receiver) VALUES (?, ?, ?)");
        psSelectIdByLogin = connection.prepareStatement("SELECT id FROM users WHERE login = ?");
        psSelectIdByNick = connection.prepareStatement("SELECT id FROM users WHERE nick = ?");
        psSelectMessages = connection.prepareStatement("SELECT u.id, u.nick, h.message, h.sender, h.receiver FROM history_chat h," +
                " users u WHERE h.receiver in (0, ?) and u.id = h.sender order by h.id");
    }

    public Server() {
        clients = new Vector<>();
        authService = new DBAuthService();
        ServerSocket server = null;
        Socket socket;

        final int PORT = 8189;

        try {
            server = new ServerSocket(PORT);
            System.out.println("Сервер запущен!");

            //добавили соединение с БД
            connect();
            System.out.println("Соединились с БД!");

            prepareAllStatements();


            while (true) {
                socket = server.accept();
                System.out.println("Клиент подключился");
                new ClientHandler(this, socket);
            }

        } catch (IOException | SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                disconnect();
                assert server != null;
                server.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void broadcastMsg(String nick, String msg) {
        for (ClientHandler c : clients) {
            c.sendMsg(nick + ": " + msg);
        }
    }

    public void privateMsg(ClientHandler sender, String receiver, String msg) {
        String message = String.format("[ %s ] private [ %s ] : %s",
                sender.getNick(), receiver, msg);

        for (ClientHandler c : clients) {
            if (c.getNick().equals(receiver)) {
                c.sendMsg(message);
                if (!sender.getNick().equals(receiver)) {
                    sender.sendMsg(message);
                }
                return;
            }
        }

        sender.sendMsg("not found user: " + receiver);
    }

    public void historyMsg(ClientHandler sender, String receiver, String msg) {
        String message = String.format("[ %s ] private [ %s ] : %s",
                sender.getNick(), receiver, msg);

        sender.sendMsg(message);
    }

    public void historyBroadcastMsg(ClientHandler sender, String sender1, String msg) {
        String message = String.format("[ %s ]: %s",
                sender1, msg);

        sender.sendMsg(message);
    }


    public void changeNickMsg(ClientHandler sender, String newNick) throws SQLException {
        System.out.println("Change nick : " + sender.getNick() + " -> " + newNick);

        psUpdateNick.setString(1, newNick);
        psUpdateNick.setString(2,sender.getLogin());
        int row = psUpdateNick.executeUpdate();

        if (row == 1) {
            sender.setNick(newNick);
            broadcastClientList();
            sender.sendMsg("/changenickok " + newNick);
        }
    }

    public void subscribe(ClientHandler clientHandler) {
        clients.add(clientHandler);
        broadcastClientList();
    }

    public void unsubscribe(ClientHandler clientHandler) {
        clients.remove(clientHandler);
        broadcastClientList();
    }

    public AuthService getAuthService() {
        return authService;
    }

    public boolean isLoginAuthorized(String login){
        for (ClientHandler c : clients) {
            if (c.getLogin().equals(login)) {
                return true;
            }
        }
        return false;
    }

    private void broadcastClientList() {
        StringBuilder sb = new StringBuilder("/clientlist ");

        for (ClientHandler c : clients) {
            sb.append(c.getNick()).append(" ");
        }
        String msg = sb.toString();

        for (ClientHandler c : clients) {
            c.sendMsg(msg);
        }
    }

}
