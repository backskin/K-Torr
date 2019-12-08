import javafx.application.Application;
import javafx.scene.Parent;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;


public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {

        URL url = Loader.toUrl(new File("src/main/resources/sample.fxml"));
        Pair<Parent, Controller> pair = Loader.loadFXML(url);
        primaryStage.setTitle("Hello Torrent");
        Loader.openInAWindow(primaryStage, pair.getOne(), false);
        pair.getTwo().setOwner(primaryStage);
        primaryStage.show();
    }


    public static void main(String[] args) {
        launch(args);
    }
}
