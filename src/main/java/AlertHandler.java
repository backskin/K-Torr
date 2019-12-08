import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

public class AlertHandler {

    public static void makeError(String content, Stage owner){
        Platform.runLater(()->{
            Alert alert = new Alert(Alert.AlertType.ERROR,content, ButtonType.OK);
            alert.initOwner(owner);
            alert.showAndWait();
        });
    }

    public static void makeInfo(String content, Stage owner){
        Platform.runLater(()-> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, content, ButtonType.OK);
            alert.initOwner(owner);
            alert.showAndWait();
        });
    }
}
