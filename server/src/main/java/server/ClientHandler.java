package server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ClientHandler {
    private Server server;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    private String nick;

    public void setNick(String nick) {
        this.nick = nick;
    }

    private String login;

    public ClientHandler(Server server, Socket socket) {
        this.server = server;
        this.socket = socket;
        try {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            new Thread(() -> {
                try {
                    //Если в течении 120 секунд не будет сообщений по сокету то вызовится исключение
                    socket.setSoTimeout(120000);

                    //цикл аутентификации
                    while (true) {
                        String str = in.readUTF();

                        if (str.startsWith("/reg ")) {
                            String[] token = str.split(" ");

                            if (token.length < 4) {
                                continue;
                            }

                            boolean succeed = server
                                    .getAuthService()
                                    .registration(token[1], token[2], token[3]);
                            if (succeed) {
                                sendMsg("Регистрация прошла успешно");
                            } else {
                                sendMsg("Регистрация  не удалась. \n" +
                                        "Возможно логин уже занят, или данные содержат пробел");
                            }
                        }

                        if (str.startsWith("/auth ")) {
                            String[] token = str.split(" ");

                            if (token.length < 3) {
                                continue;
                            }

                            String newNick = server.getAuthService()
                                    .getNicknameByLoginAndPassword(token[1], token[2]);

                            login = token[1];

                            if (newNick != null) {
                                if (!server.isLoginAuthorized(login)) {
                                    sendMsg("/authok " + newNick);
                                    nick = newNick;
                                    server.subscribe(this);
                                    System.out.println("Клиент: " + nick + " подключился"+ socket.getRemoteSocketAddress());

                                    Server.getPsSelectMessages().setInt(1, getIdByLogin(login, ""));
                                    ResultSet rs = Server.getPsSelectMessages().executeQuery();

                                    while (rs.next()) {
                                        String msg = rs.getString("message");
                                        String sender = rs.getString("nick");
                                        int broadcast = rs.getInt("receiver");


                                        if (broadcast == 0) {
                                            server.historyBroadcastMsg(this, sender, msg);
                                        }
                                        else {
                                            server.historyMsg(this, sender, msg);
                                        }
                                    }
                                    rs.close();

                                    socket.setSoTimeout(0);
                                    break;
                                } else {
                                    sendMsg("С этим логином уже прошли аутентификацию");
                                }
                            } else {
                                sendMsg("Неверный логин / пароль");
                            }
                        }
                    }

                    //цикл работы
                    while (true) {
                        String str = in.readUTF();

                        if (str.startsWith("/")) {
                            if (str.equals("/end")) {
                                sendMsg("/end");
                                break;
                            }
                            if (str.startsWith("/w ")) {
                                String[] token = str.split(" ", 3);

                                if (token.length < 3) {
                                    continue;
                                }

                                server.privateMsg(this, token[1], token[2]);

                                addHistoryMsg(token[2], getIdByLogin(login, ""), getIdByLogin("", token[1]));
                            }

                            if (str.startsWith("/changenick ")) {
                                String[] token = str.split(" ", 2);

                                if (token.length < 2) {
                                    continue;
                                }

                                server.changeNickMsg(this, token[1]);
                            }

                        } else {
                            server.broadcastMsg(nick, str);

                            addHistoryMsg(str, getIdByLogin(login, ""), 0);
                        }
                    }
                }catch (SocketTimeoutException e){
                    sendMsg("/end");
                }
                ///////
                catch (IOException e) {
                    e.printStackTrace();
                } catch (SQLException throwables) {
                    throwables.printStackTrace();
                } finally {
                    server.unsubscribe(this);
                    System.out.println("Клиент отключился");
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendMsg(String msg) {
        try {
            out.writeUTF(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addHistoryMsg (String message, int sender, int receiver) throws SQLException {

        Server.getPsInsertMsg().setString(1, message);
        Server.getPsInsertMsg().setInt(2, sender);
        Server.getPsInsertMsg().setInt(3, receiver);
        Server.getPsInsertMsg().executeUpdate();

    }

    public String getNick() {
        return nick;
    }

    public String getLogin() {
        return login;
    }

    public int getIdByLogin(String login, String nick) throws SQLException {
        ResultSet rs;

        int id = 0;

        if (login != "") {
            Server.getPsSelectIdByLogin().setString(1, login);
            rs = Server.getPsSelectIdByLogin().executeQuery();
        }
        else {
            Server.getPsSelectIdByNick().setString(1, nick);
            rs = Server.getPsSelectIdByNick().executeQuery();
        }

        while (rs.next()) {
            id = rs.getInt("id");
        }
        rs.close();

        return id;
    }
}
