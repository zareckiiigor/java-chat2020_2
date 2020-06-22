package server;

import java.sql.ResultSet;
import java.sql.SQLException;

public class DBAuthService implements AuthService{

    @Override
    public String getNicknameByLoginAndPassword(String login, String password) throws SQLException {

        Server.getPsSelectUsers().setString(1, login);
        Server.getPsSelectUsers().setString(2, password);
        ResultSet rs = Server.getPsSelectUsers().executeQuery();                 //логин уникален в свойствах столбца БД, т.е. вернется не более одного

        String nickname = null;

        while (rs.next()) {
            nickname  = rs.getString("nick");
        }
        rs.close();

        return nickname;
    }

    @Override
    public boolean registration(String login, String password, String nickname) throws SQLException {

        Server.getPsUniqueLoginUser().setString(1, login);

        ResultSet rs = Server.getPsUniqueLoginUser().executeQuery();                 //логин уникален в свойствах столбца БД, т.е. вернется не более одного

        int countlogin = 0;

        while (rs.next()) {
            countlogin  = Integer.parseInt(rs.getString("countlogin"));
        }
        rs.close();

        System.out.println("countlogin " + countlogin);

        if (countlogin == 0) {

            Server.getPsInsertUser().setString(1, login);
            Server.getPsInsertUser().setString(2, password);
            Server.getPsInsertUser().setString(3, nickname);
            Server.getPsInsertUser().executeUpdate();

            return true;
        }
        return false;
    }
}
