package com.klinik.app.controller;

import com.klinik.app.dao.AntreanDAO;
import com.klinik.app.dao.DokterDAO;
import com.klinik.app.dao.PasienDAO;
import com.klinik.app.model.AdminSystem;
import com.klinik.app.model.Antrean;
import com.klinik.app.model.Dokter;
import com.klinik.app.model.Pasien;
import com.klinik.app.util.UserSession;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class AntreanController {

    @FXML private ComboBox<Pasien> cbPasien;
    @FXML private ComboBox<Dokter> cbDokter;
    @FXML private TextField txtNoAntrean;
    @FXML private ComboBox<String> cbTingkatUrgensi;
    @FXML private Button btnTambahAntrean;
    @FXML private Label lblPasienBerikutnya;
    @FXML private Label lblQueueStats;
    @FXML private VBox boxAdminInputs;
    @FXML private Button btnPanggil;

    @FXML private TableView<Antrean> tableAntrean;
    @FXML private TableColumn<Antrean, Integer> colNoAntrean;
    @FXML private TableColumn<Antrean, String> colNamaPasien;
    @FXML private TableColumn<Antrean, String> colNamaDokter;
    @FXML private TableColumn<Antrean, String> colTingkatUrgensi;
    @FXML private TableColumn<Antrean, String> colStatus;
    @FXML private TableColumn<Antrean, String> colKeluhan;

    private final AntreanDAO antreanDAO = new AntreanDAO();
    private final PasienDAO pasienDAO = new PasienDAO();
    private final DokterDAO dokterDAO = new DokterDAO();

    private final ObservableList<Antrean> listTableAntrean = FXCollections.observableArrayList();

    private final Comparator<Antrean> triaseComparator = (a1, a2) -> {
        int p1 = getBobotUrgensi(a1 != null ? a1.getTingkatUrgensi() : "");
        int p2 = getBobotUrgensi(a2 != null ? a2.getTingkatUrgensi() : "");

        if (p1 != p2) {
            return Integer.compare(p1, p2);
        }
        return Integer.compare(a1.getNoAntrean(), a2.getNoAntrean());
    };

    private final PriorityQueue<Antrean> antreanPriorityQueue = new PriorityQueue<>(triaseComparator);

    private int getBobotUrgensi(String urgensi) {
        if (urgensi == null) return 3;
        switch (urgensi.toUpperCase()) {
            case "MERAH": return 1;
            case "KUNING": return 2;
            default: return 3;
        }
    }

    @FXML
    public void initialize() {
        if (cbTingkatUrgensi != null) {
            cbTingkatUrgensi.setItems(FXCollections.observableArrayList("HIJAU", "KUNING", "MERAH"));
            cbTingkatUrgensi.setValue("HIJAU");
        }

        if (colNoAntrean != null) colNoAntrean.setCellValueFactory(new PropertyValueFactory<>("noAntrean"));
        if (colNamaPasien != null) colNamaPasien.setCellValueFactory(new PropertyValueFactory<>("namaPasien"));
        if (colNamaDokter != null) colNamaDokter.setCellValueFactory(new PropertyValueFactory<>("namaDokter"));
        if (colTingkatUrgensi != null) colTingkatUrgensi.setCellValueFactory(new PropertyValueFactory<>("tingkatUrgensi"));
        if (colStatus != null) colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        if (colKeluhan != null) colKeluhan.setCellValueFactory(new PropertyValueFactory<>("keluhan"));

        // Format tampilan badge tabel
        setupTableStyling();

        UserSession session = UserSession.getInstance();
        if (session != null && session.isDokter()) {
            disableAntreanInputs();
        }

        setupDropdowns();
        loadDataAntrean();
    }

    private void setupTableStyling() {
        if (colTingkatUrgensi != null) {
            colTingkatUrgensi.setCellFactory(col -> new TableCell<Antrean, String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        setStyle("");
                    } else {
                        setText(item);
                        switch (item.toUpperCase()) {
                            case "MERAH":
                                setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #b91c1c; -fx-font-weight: bold; -fx-alignment: CENTER; -fx-background-radius: 4;");
                                break;
                            case "KUNING":
                                setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #b45309; -fx-font-weight: bold; -fx-alignment: CENTER; -fx-background-radius: 4;");
                                break;
                            default:
                                setStyle("-fx-background-color: #d1fae5; -fx-text-fill: #047857; -fx-font-weight: bold; -fx-alignment: CENTER; -fx-background-radius: 4;");
                                break;
                        }
                    }
                }
            });
        }

        if (colStatus != null) {
            colStatus.setCellFactory(col -> new TableCell<Antrean, String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setStyle("");
                    } else {
                        setText(item);
                        switch (item.toUpperCase()) {
                            case "DIPERIKSA":
                                setStyle("-fx-background-color: #ffedd5; -fx-text-fill: #c2410c; -fx-font-weight: bold; -fx-alignment: CENTER; -fx-background-radius: 4;");
                                break;
                            case "SELESAI":
                                setStyle("-fx-background-color: #ecfdf5; -fx-text-fill: #065f46; -fx-font-weight: bold; -fx-alignment: CENTER; -fx-background-radius: 4;");
                                break;
                            default:
                                setStyle("-fx-background-color: #e0f2fe; -fx-text-fill: #0369a1; -fx-font-weight: bold; -fx-alignment: CENTER; -fx-background-radius: 4;");
                                break;
                        }
                    }
                }
            });
        }
    }

    private void setupDropdowns() {
        new Thread(() -> {
            List<Pasien> listPasien = pasienDAO.getAllPasien();
            List<Dokter> listDokter = dokterDAO.getAllDokter();

            Platform.runLater(() -> {
                if (cbPasien != null) {
                    cbPasien.setItems(FXCollections.observableArrayList(listPasien));
                    cbPasien.setConverter(new StringConverter<Pasien>() {
                        @Override
                        public String toString(Pasien p) {
                            if (p == null) return "";
                            return p.getNamaPasien() + " (NIK: " + p.getNik() + ")";
                        }
                        @Override
                        public Pasien fromString(String string) { return null; }
                    });
                }

                if (cbDokter != null) {
                    cbDokter.setItems(FXCollections.observableArrayList(listDokter));
                    cbDokter.setConverter(new StringConverter<Dokter>() {
                        @Override
                        public String toString(Dokter d) {
                            if (d == null) return "";
                            return d.getNamaLengkap() + " (" + d.getSpesialisasi() + ")";
                        }
                        @Override
                        public Dokter fromString(String string) { return null; }
                    });

                    cbDokter.valueProperty().addListener((obs, oldDokter, newDokter) -> {
                        if (newDokter != null && txtNoAntrean != null) {
                            new Thread(() -> {
                                int nextNo = antreanDAO.getNomorAntreanBerikutnya(newDokter.getPegawaiId());
                                Platform.runLater(() -> txtNoAntrean.setText(String.valueOf(nextNo)));
                            }).start();
                        }
                    });
                }
            });
        }).start();
    }

    private void disableAntreanInputs() {
        if (boxAdminInputs != null) {
            boxAdminInputs.setVisible(false);
            boxAdminInputs.setManaged(false);
        }
        if (cbPasien != null) cbPasien.setDisable(true);
        if (cbDokter != null) cbDokter.setDisable(true);
        if (txtNoAntrean != null) txtNoAntrean.setDisable(true);
        if (cbTingkatUrgensi != null) cbTingkatUrgensi.setDisable(true);
        if (btnTambahAntrean != null) btnTambahAntrean.setDisable(true);
    }

    @FXML
    public void loadDataAntrean() {
        UserSession session = UserSession.getInstance();
        Integer dokterIdLogin = (session != null && session.isDokter()) ? session.getDokterPegawaiId() : null;

        new Thread(() -> {
            List<Antrean> listFromDb = (dokterIdLogin != null) 
                    ? antreanDAO.getAntreanDokterHariIni(dokterIdLogin)
                    : antreanDAO.getAllAntreanHariIni();

            Platform.runLater(() -> {
                antreanPriorityQueue.clear();
                if (listFromDb != null) {
                    for (Antrean a : listFromDb) {
                        if ("MENUNGGU".equalsIgnoreCase(a.getStatus())) {
                            antreanPriorityQueue.add(a);
                        }
                    }
                }
                updateTableAndLabel(listFromDb);
            });
        }).start();
    }

    private void updateTableAndLabel(List<Antrean> allList) {
        listTableAntrean.clear();
        PriorityQueue<Antrean> tempQueue = new PriorityQueue<>(triaseComparator);
        tempQueue.addAll(antreanPriorityQueue);

        // Pasien MENUNGGU urut triase prioritas
        while (!tempQueue.isEmpty()) {
            listTableAntrean.add(tempQueue.poll());
        }

        // Tampilkan juga pasien yang sedang DIPERIKSA di bagian paling atas
        if (allList != null) {
            for (Antrean a : allList) {
                if ("DIPERIKSA".equalsIgnoreCase(a.getStatus())) {
                    if (!listTableAntrean.contains(a)) {
                        listTableAntrean.add(0, a);
                    }
                }
            }
        }

        if (tableAntrean != null) {
            tableAntrean.setItems(listTableAntrean);
        }

        Antrean berikutnya = antreanPriorityQueue.peek();
        if (lblPasienBerikutnya != null) {
            if (berikutnya != null) {
                lblPasienBerikutnya.setText("Pasien Prioritas Berikutnya: No. " + berikutnya.getNoAntrean() + " • " + 
                        berikutnya.getNamaPasien() + " [" + berikutnya.getTingkatUrgensi() + "]");
                lblPasienBerikutnya.setStyle("-fx-font-weight: bold; -fx-text-fill: " + 
                        ("MERAH".equalsIgnoreCase(berikutnya.getTingkatUrgensi()) ? "#dc2626" : 
                        ("KUNING".equalsIgnoreCase(berikutnya.getTingkatUrgensi()) ? "#d97706" : "#059669")));
            } else {
                lblPasienBerikutnya.setText("Tidak ada antrean pasien menunggu");
                lblPasienBerikutnya.setStyle("-fx-font-weight: normal; -fx-text-fill: #64748b;");
            }
        }

        if (lblQueueStats != null) {
            int total = listTableAntrean.size();
            lblQueueStats.setText("Total Pasien Aktif: " + total + " orang");
        }
    }

    @FXML
    private void handleTambahAntrean() {
        UserSession session = UserSession.getInstance();
        if (session != null && session.isDokter()) {
            showAlert(Alert.AlertType.ERROR, "Akses Ditolak", "Dokter tidak diperkenankan menambahkan antrean baru!");
            return;
        }

        Pasien pasienPilihan = cbPasien != null ? cbPasien.getValue() : null;
        Dokter dokterPilihan = cbDokter != null ? cbDokter.getValue() : null;

        if (pasienPilihan == null || dokterPilihan == null) {
            showAlert(Alert.AlertType.WARNING, "Validasi Gagal", "Silakan pilih Pasien dan Dokter Tujuan!");
            return;
        }

        try {
            int pasienId = pasienPilihan.getPasienId();
            int dokterId = dokterPilihan.getPegawaiId();

            String strNoAntrean = txtNoAntrean != null ? txtNoAntrean.getText().trim() : "";
            int noAntrean = strNoAntrean.isEmpty() ? 0 : Integer.parseInt(strNoAntrean);

            int adminId = (session != null && session.isAdmin() && session.getAdmin() != null)
                    ? session.getAdmin().getPegawaiId() : 1;

            String triase = (cbTingkatUrgensi != null && cbTingkatUrgensi.getValue() != null)
                    ? cbTingkatUrgensi.getValue() : "HIJAU";

            if (antreanDAO.prosesAntreanOlehAdmin(pasienId, dokterId, adminId, noAntrean, triase)) {
                showAlert(Alert.AlertType.INFORMATION, "Sukses", 
                        "Antrean untuk pasien " + pasienPilihan.getNamaPasien() + " berhasil ditambahkan!");
                loadDataAntrean();
                if (cbPasien != null) cbPasien.setValue(null);
                if (cbDokter != null) cbDokter.setValue(null);
                if (txtNoAntrean != null) txtNoAntrean.clear();
            } else {
                showAlert(Alert.AlertType.ERROR, "Gagal", "Gagal menyimpan antrean ke database!");
            }
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.WARNING, "Input Tidak Valid", "Nomor Antrean harus berupa angka!");
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Error Database", e.getMessage());
        }
    }

    @FXML
    private void handlePanggilPasienBerikutnya() {
        Antrean dipanggil = antreanPriorityQueue.poll();

        if (dipanggil != null) {
            antreanDAO.updateStatus(dipanggil.getAntreanId(), "DIPERIKSA");

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Panggilan Pasien");
            alert.setHeaderText("MEMANGGIL PASIEN KE RUANG PEMERIKSAAN");
            alert.setContentText("Nomor Antrean: " + dipanggil.getNoAntrean() + 
                    "\nNama Pasien: " + dipanggil.getNamaPasien() +
                    "\nTriase: " + dipanggil.getTingkatUrgensi() +
                    (dipanggil.getKeluhan() != null ? "\nKeluhan: " + dipanggil.getKeluhan() : ""));
            alert.show();

            loadDataAntrean();
        } else {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Tidak ada antrean pasien yang berstatus MENUNGGU!");
            alert.setTitle("Antrean Kosong");
            alert.setHeaderText(null);
            alert.show();
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
