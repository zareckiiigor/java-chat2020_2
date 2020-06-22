package client;

import javafx.event.ActionEvent;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class ChangeNick {
    public TextField newNickField;
    Controller controller;

    public void clickOk(ActionEvent actionEvent) {
        controller.tryChangeNick(newNickField.getText().trim());
        controller.changeNickStage.close();
    }

    public void setNewNickField(String text) {
        newNickField.setText(text);
    }
}
