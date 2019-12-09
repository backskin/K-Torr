import bt.Bt;
import bt.data.file.FileSystemStorage;
import bt.dht.DHTConfig;
import bt.dht.DHTModule;
import bt.metainfo.MetadataService;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;
import bt.runtime.BtClient;
import bt.runtime.BtRuntime;
import bt.runtime.BtRuntimeBuilder;
import bt.runtime.Config;
import bt.torrent.TorrentSessionState;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

public class Controller {

    public Label tSizeLabel;
    public ProgressBar progressBar;
    public Label downedLabel;
    public Label estimLabel;
    public Label seedsLabel;
    public Label peersLabel;
    public Label speedLabel;
    public Label trackerLabel;

    @FXML
    private TableView<TorrentFile> tFilesTable;
    public TableColumn<TorrentFile, String> tFilesTableSizeColumn;
    public TableColumn<TorrentFile, String> tFilesTableNameColumn;
    @FXML
    private TextArea consoleArea;
    @FXML
    private Label tNameLabel;
    @FXML
    private TextField torrPathField;

    private SimpleDateFormat simpleDateFormat = new SimpleDateFormat();
    private long timeStart;
    private BtClient client;
    private long lastDownloadedBytes = 0;

    private Stage owner;
    private Torrent torrent = null;
    private File destination = null;

    public Controller(){
        String timePattern = "HH:mm:ss";
        simpleDateFormat.applyLocalizedPattern(timePattern);
    }

    void setOwner(Stage owner) {
        this.owner = owner;
        owner.setOnCloseRequest(event -> {
            if (client != null){
                if (client.isStarted()){
                    client.stop();
                    Platform.exit();
                    System.exit(0);
                }
            }
        });
    }

    @FXML
    private void initialize(){
        resetView();
    }

    private void resetView(){

        progressBar.setProgress(.0);
        downedLabel.setText("");
        estimLabel.setText("");
        peersLabel.setText("");
        speedLabel.setText("");
        trackerLabel.setText("");
        tNameLabel.setText("");
    }

    @FXML
    public void handleOpenTorrentFile() {

        tFilesTable.getItems().clear();
        torrent = null;
        consoleArea.appendText("Загрузка нового торрента...\n");
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choose a torrent");
        fileChooser.setInitialDirectory(new File(System.getProperty("user.dir")));
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("torrent file", "*.torrent"));
        File fileOfTorrent = fileChooser.showOpenDialog(owner);
        if (fileOfTorrent != null) {

            MetadataService metadataService = new MetadataService();
            torrent = metadataService.fromUrl(Loader.toUrl(fileOfTorrent));

            tNameLabel.setText(torrent.getName());

            consoleArea.appendText("Адреса трекеров из торрент-файла:\n");
            torrent.getAnnounceKey().get().getTrackerUrls().forEach(
                    list -> list.forEach(
                            s -> consoleArea.appendText(s + "\n")));

            tSizeLabel.setText(String.format("%.2f", ((double)torrent.getSize())/1024/1024) + " мегабайт");

            ObservableList<TorrentFile> torrentFiles = tFilesTable.getItems();
            torrentFiles.addAll(torrent.getFiles());

            tFilesTableNameColumn.setCellValueFactory(param -> {
                StringBuilder name = new StringBuilder();

                for (String element: param.getValue().getPathElements())
                    name.append(File.separator).append(element);
                return new SimpleStringProperty(name.toString());
            });

            tFilesTableSizeColumn.setCellValueFactory(param -> new SimpleStringProperty(
                    "" + String.format("%.2f", ((double)param.getValue().getSize())/1024/1024) + " мегабайт"));
        }
    }

    @FXML
    public void handleStart() {

        if (torrent == null || destination == null)
            AlertHandler.makeInfo("Вы не выбрали путь загрузки или сам торрент!", owner);

        DHTModule dhtModule = new DHTModule(new DHTConfig(){
            @Override
            public boolean shouldUseRouterBootstrap() {
                return true;
            }
        });
        BtRuntimeBuilder builder = new BtRuntimeBuilder();
        Config config = new Config();

        BtRuntime runtime = builder
                .disableAutomaticShutdown()
                .module(dhtModule)
                .config(config)
                .autoLoadModules()
                .build();


        client = Bt.client(runtime)
                .storage(new FileSystemStorage(destination.toPath()))
                .torrent(() -> torrent)
                .stopWhenDownloaded()
                .build();

        timeStart = System.currentTimeMillis();
        consoleArea.appendText("Загрузка началась в " + simpleDateFormat.format(new Date(timeStart)) + "\n");
        long period = 100;

        client.startAsync(new Consumer<TorrentSessionState>() {

            int skipped = 1;

            @Override
            public void accept(TorrentSessionState state) {

                    Platform.runLater(()-> {

                        owner.setTitle(String.format("%d секунд мы что-то грузим",
                                Duration.ofMillis(System.currentTimeMillis() - timeStart).getSeconds()));

                        long dw = state.getDownloaded()/1024;

                        downedLabel.setText(String.format("%d КБ", dw));

                        estimLabel.setText(String.format("%d КБ", torrent.getSize()/1024 - dw));

                        if (state.getDownloaded() != lastDownloadedBytes) {
                            speedLabel.setText(String.format("%d КБ/сек",
                                    (state.getDownloaded() - lastDownloadedBytes) / (long)(1.024 * period * skipped)));
                            lastDownloadedBytes = state.getDownloaded();
                            skipped = 1;
                        } else skipped++;

                        progressBar.setProgress( (double)state.getPiecesComplete() / state.getPiecesTotal());

                        peersLabel.setText(String.format("%d", state.getConnectedPeers().size()));
                    });

                    if (state.getPiecesIncomplete() == 0 && lastDownloadedBytes != 0) {
                        long timeFinish = System.currentTimeMillis();
                        Platform.runLater(()-> consoleArea.appendText("Загрузка завершена! Прошло "
                                + Duration.ofMillis(timeFinish - timeStart).getSeconds()
                                + " секунд\n"));
                        lastDownloadedBytes = 0;
                    }
            }
        }, period);
    }

    @FXML
    public void handleDestButton() {

        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setInitialDirectory(new File(System.getProperty("user.dir")));
        destination = directoryChooser.showDialog(owner);
        if (destination != null)
            torrPathField.setText(destination.getPath());
    }

    public void handlePause() {

        if (client.isStarted()){

            resetView();

            consoleArea.appendText("Остановлено\n");
            client.stop();
        }
    }
}
