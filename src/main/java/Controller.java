import bt.Bt;
import bt.data.Storage;
import bt.data.file.FileSystemStorage;
import bt.dht.DHTConfig;
import bt.dht.DHTModule;
import bt.metainfo.MetadataService;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;
import bt.net.Peer;
import bt.runtime.BtClient;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

public class Controller {

    public Label tSizeLabel;
    public ProgressBar progressBar;
    public Label downedLabel;
    public Label estimLabel;
    public Label seedsLabel;
    public Label peersLabel;

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

    String timePattern = "HH:mm:ss";
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat();
    long timeStart;
    BtClient client;

    private Stage owner;
    private Torrent torrent = null;
    private File destination = null;

    public Controller(){
        simpleDateFormat.applyLocalizedPattern(timePattern);
    }

    public void setOwner(Stage owner) {
        this.owner = owner;
    }

    @FXML
    public void handleOpenTorrentFile() {

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choose a torrent");
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("torrent file", "*.torrent"));
        File torrentFile = fileChooser.showOpenDialog(owner);
        if (torrentFile != null) {


            ///////////////////
            /// Здесь будет инициализация всей инфы о торренте
            ///////////////////


            MetadataService metadataService = new MetadataService();
            torrent = metadataService.fromUrl(Loader.toUrl(torrentFile));
            tNameLabel.setText(torrent.getName());
            tSizeLabel.setText(String.format("%.3f", ((double)torrent.getSize())/1024/1024) + " мегабайт");
            ObservableList<TorrentFile> torrentFiles = tFilesTable.getItems();
            torrentFiles.addAll(torrent.getFiles());

            tFilesTableNameColumn.setCellValueFactory(param -> {
                StringBuilder name = new StringBuilder();
                for (String element: param.getValue().getPathElements())
                    name.append(File.separator).append(element);
                return new SimpleStringProperty(name.toString());
            });
            tFilesTableSizeColumn.setCellValueFactory(param -> new SimpleStringProperty(
                    "" + String.format("%.3f", ((double)param.getValue().getSize())/1024/1024) + " мегабайт"));
        }
    }

    @FXML
    public void handleStart() {


        DHTConfig dhtConfig = new DHTConfig();
        dhtConfig.setShouldUseRouterBootstrap(true);
        DHTModule dhtModule = new DHTModule(dhtConfig);
        Storage storage = new FileSystemStorage(destination.toPath());

        client = Bt.client()
                .storage(storage)
                .torrent(() -> torrent)
                .autoLoadModules()
                .module(dhtModule)
                .stopWhenDownloaded()
                .build();

        timeStart = System.currentTimeMillis();

        consoleArea.appendText("Загрузка началась в " + simpleDateFormat.format(new Date(timeStart)) + "\n");

        client.startAsync( state -> {

            Set<Peer> peers = state.getConnectedPeers();
            List<Peer> peerList = new ArrayList<>(peers);

            Platform.runLater(()-> {
                long dw = state.getDownloaded()/1024;
                downedLabel.setText(String.format("%d", dw) + " КБ");
                estimLabel.setText(String.format("%d", torrent.getSize()/1024 - dw) + " КБ");
                progressBar.setProgress( 1.0 - (double)state.getPiecesRemaining() / state.getPiecesTotal());
                peersLabel.setText(String.format("%d",peerList.size()));
            });

            if (state.getPiecesRemaining() == 0) {
                Platform.runLater(()-> consoleArea.appendText("Загрузка завершена! Прошло "
                        + simpleDateFormat.format(new Date(System.currentTimeMillis() - timeStart))
                        + "\n"));
                client.stop();
            }

        }, 500);
    }

    @FXML
    public void handleDestButton() {

        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setInitialDirectory(new File(System.getProperty("user.home")));
        destination = directoryChooser.showDialog(owner);
        if (destination != null)
            torrPathField.setText(destination.getPath());
    }

    public void handlePause() {

        if (client.isStarted()){

            consoleArea.appendText("Остановлено\n");
            client.stop();
        }
    }
}
