package com.klinik.app.controller;

import com.klinik.app.dao.AntreanDAO;
import com.klinik.app.dao.DokterDAO;
import com.klinik.app.dao.ObatDAO;
import com.klinik.app.dao.PasienDAO;
import com.klinik.app.dao.RekamMedisDAO;
import com.klinik.app.model.*;
import com.klinik.app.util.StrukPrinter;
import com.klinik.app.util.UserSession;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.StringConverter;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class RekamMedisController {

    // Pemilihan Pasien dari Antrean Hari Ini
    @FXML private ComboBox<Antrean> cbAntreanPasien;
    @FXML private ComboBox<Pasien> cbPasienDirect;
    @FXML private CheckBox chkWalkIn;

    // Info Pasien & Dokter Terpilih (Read-Only)
    @FXML private Label lblDokterPemeriksa;
    @FXML private Label lblPasienTerpilih;
    @FXML private Label lblAlergiWarning;
    @FXML private Label lblNoAntreanBadge;

    @FXML private TextArea txtKeluhan;
    @FXML private TextArea txtDiagnosa;
    @FXML private TextArea txtTindakan;

    // Komponen Resep Obat
    @FXML private ComboBox<Obat> cbObat;
    @FXML private TextField txtJumlahObat;
    @FXML private TextField txtDosisObat;
    @FXML private Button btnTambahResep;
    @FXML private Button btnHapusResep;
    @FXML private TableView<DetailResep> tableResepSementara;
    @FXML private TableColumn<DetailResep, String> colResepNamaObat;
    @FXML private TableColumn<DetailResep, Integer> colResepJumlah;
    @FXML private TableColumn<DetailResep, String> colResepSatuan;
    @FXML private TableColumn<DetailResep, String> colResepDosis;

    // Tabel Riwayat Rekam Medis
    @FXML private TableView<RekamMedis> tableRekamMedis;
    @FXML private TableColumn<RekamMedis, LocalDateTime> colTanggal;
    @FXML private TableColumn<RekamMedis, String> colPasien;
    @FXML private TableColumn<RekamMedis, String> colDokter;
    @FXML private TableColumn<RekamMedis, Integer> colAntrean;
    @FXML private TableColumn<RekamMedis, String> colKeluhan;
    @FXML private TableColumn<RekamMedis, String> colDiagnosa;
    @FXML private TableColumn<RekamMedis, String> colTindakan;

    private final RekamMedisDAO rekamMedisDAO = new RekamMedisDAO();
    private final AntreanDAO antreanDAO = new AntreanDAO();
    private final PasienDAO pasienDAO = new PasienDAO();
    private final DokterDAO dokterDAO = new DokterDAO();
    private final ObatDAO obatDAO = new ObatDAO();

    private final ObservableList<RekamMedis> listRekamMedis = FXCollections.observableArrayList();
    private final ObservableList<DetailResep> listResepSementara = FXCollections.observableArrayList();
    private final List<Obat> listObatCache = new ArrayList<>();

    private Pasien currentPasien;
    private Antrean currentAntrean;
    private Dokter currentDokter;

    @FXML
    public void initialize() {
        initDokterIdentitas();

        if (colTanggal != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            colTanggal.setCellValueFactory(new PropertyValueFactory<>("tanggalPeriksa"));
            colTanggal.setCellFactory(col -> new TableCell<RekamMedis, LocalDateTime>() {
                @Override
                protected void updateItem(LocalDateTime item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        setText(item.format(formatter));
                    }
                }
            });
        }
        if (colPasien != null) colPasien.setCellValueFactory(new PropertyValueFactory<>("namaPasien"));
        if (colDokter != null) colDokter.setCellValueFactory(new PropertyValueFactory<>("namaDokter"));
        if (colAntrean != null) colAntrean.setCellValueFactory(new PropertyValueFactory<>("antreanId"));
        if (colKeluhan != null) colKeluhan.setCellValueFactory(new PropertyValueFactory<>("keluhan"));
        if (colDiagnosa != null) colDiagnosa.setCellValueFactory(new PropertyValueFactory<>("diagnosa"));
        if (colTindakan != null) colTindakan.setCellValueFactory(new PropertyValueFactory<>("tindakan"));

        setupKomponenResep();
        setupPasienSelectors();
        loadRekamMedisData();
    }

    private void initDokterIdentitas() {
        UserSession session = UserSession.getInstance();
        if (session != null && session.isDokter() && session.getDokter() != null) {
            currentDokter = session.getDokter();
            if (lblDokterPemeriksa != null) {
                lblDokterPemeriksa.setText("Dokter: " + currentDokter.getNamaLengkap() + 
                        " (" + currentDokter.getSpesialisasi() + " • " + currentDokter.getNoSip() + ")");
            }
        } else {
            // Jika login sebagai Admin, pilih dokter pertama sebagai default
            new Thread(() -> {
                List<Dokter> doctors = dokterDAO.getAllDokter();
                Platform.runLater(() -> {
                    if (!doctors.isEmpty()) {
                        currentDokter = doctors.get(0);
                        if (lblDokterPemeriksa != null) {
                            lblDokterPemeriksa.setText("Dokter: " + currentDokter.getNamaLengkap() + 
                                    " (" + currentDokter.getSpesialisasi() + ")");
                        }
                    }
                });
            }).start();
        }
    }

    private void setupPasienSelectors() {
        if (chkWalkIn != null) {
            chkWalkIn.selectedProperty().addListener((obs, oldV, isWalkIn) -> {
                if (cbAntreanPasien != null) {
                    cbAntreanPasien.setVisible(!isWalkIn);
                    cbAntreanPasien.setManaged(!isWalkIn);
                }
                if (cbPasienDirect != null) {
                    cbPasienDirect.setVisible(isWalkIn);
                    cbPasienDirect.setManaged(isWalkIn);
                }
                clearPatientSelection();
            });
        }

        refreshAntreanDropdown();

        // Setup dropdown Pasien Direct (Walk-in)
        if (cbPasienDirect != null) {
            cbPasienDirect.setConverter(new StringConverter<Pasien>() {
                @Override
                public String toString(Pasien p) {
                    if (p == null) return "";
                    return p.getNamaPasien() + " (NIK: " + p.getNik() + ")";
                }
                @Override
                public Pasien fromString(String string) { return null; }
            });

            cbPasienDirect.valueProperty().addListener((obs, oldP, newP) -> {
                if (newP != null) {
                    currentPasien = newP;
                    currentAntrean = null;
                    displaySelectedPatientInfo(currentPasien, null);
                }
            });
        }
    }

    @FXML
    public void refreshAntreanDropdown() {
        UserSession session = UserSession.getInstance();
        Integer dokterId = (session != null && session.isDokter()) ? session.getDokterPegawaiId() : null;

        new Thread(() -> {
            List<Antrean> antreanList = (dokterId != null) 
                    ? antreanDAO.getAntreanDokterHariIni(dokterId) 
                    : antreanDAO.getAllAntreanHariIni();

            List<Pasien> allPasien = pasienDAO.getAllPasien();

            Platform.runLater(() -> {
                if (cbAntreanPasien != null) {
                    cbAntreanPasien.setItems(FXCollections.observableArrayList(antreanList));
                    cbAntreanPasien.setConverter(new StringConverter<Antrean>() {
                        @Override
                        public String toString(Antrean a) {
                            if (a == null) return "";
                            return "No. " + a.getNoAntrean() + " - " + a.getNamaPasien() + 
                                    " [" + a.getTingkatUrgensi() + " | " + a.getStatus() + "]";
                        }
                        @Override
                        public Antrean fromString(String string) { return null; }
                    });

                    cbAntreanPasien.valueProperty().addListener((obs, oldA, newA) -> {
                        if (newA != null) {
                            currentAntrean = newA;
                            new Thread(() -> {
                                Pasien p = pasienDAO.getPasienById(newA.getPasienId());
                                Platform.runLater(() -> {
                                    currentPasien = p;
                                    displaySelectedPatientInfo(p, newA);
                                });
                            }).start();
                        }
                    });
                }

                if (cbPasienDirect != null) {
                    cbPasienDirect.setItems(FXCollections.observableArrayList(allPasien));
                }
            });
        }).start();
    }

    private void displaySelectedPatientInfo(Pasien p, Antrean a) {
        if (p == null) {
            clearPatientSelection();
            return;
        }

        if (lblPasienTerpilih != null) {
            String jkStr = p.getJenisKelamin() != null ? " (" + p.getJenisKelamin() + ")" : "";
            lblPasienTerpilih.setText("Pasien: " + p.getNamaPasien() + jkStr + " • NIK: " + p.getNik());
        }

        if (lblNoAntreanBadge != null) {
            if (a != null) {
                lblNoAntreanBadge.setText("Antrean No. " + a.getNoAntrean() + " [" + a.getTingkatUrgensi() + "]");
                lblNoAntreanBadge.setVisible(true);
            } else {
                lblNoAntreanBadge.setText("Walk-in (Tanpa Antrean)");
                lblNoAntreanBadge.setVisible(true);
            }
        }

        // Peringatan Alergi Obat
        if (lblAlergiWarning != null) {
            String alergi = p.getAlergiObat();
            if (alergi != null && !alergi.trim().isEmpty() && !"-".equals(alergi.trim())) {
                lblAlergiWarning.setText("⚠ PERINGATAN ALERGI: " + alergi);
                lblAlergiWarning.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #b91c1c; -fx-padding: 4 8; -fx-background-radius: 4; -fx-font-weight: bold;");
                lblAlergiWarning.setVisible(true);
                lblAlergiWarning.setManaged(true);
            } else {
                lblAlergiWarning.setText("Riwayat Alergi: Tidak ada");
                lblAlergiWarning.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");
                lblAlergiWarning.setVisible(true);
                lblAlergiWarning.setManaged(true);
            }
        }

        // Pre-fill keluhan dari antrean jika ada dan keluhan masih kosong
        if (a != null && a.getKeluhan() != null && !a.getKeluhan().trim().isEmpty()) {
            if (txtKeluhan != null && (txtKeluhan.getText() == null || txtKeluhan.getText().trim().isEmpty())) {
                txtKeluhan.setText(a.getKeluhan());
            }
        }
    }

    private void clearPatientSelection() {
        currentPasien = null;
        currentAntrean = null;
        if (lblPasienTerpilih != null) lblPasienTerpilih.setText("Pasien belum dipilih");
        if (lblNoAntreanBadge != null) lblNoAntreanBadge.setVisible(false);
        if (lblAlergiWarning != null) {
            lblAlergiWarning.setVisible(false);
            lblAlergiWarning.setManaged(false);
        }
        if (txtKeluhan != null) txtKeluhan.clear();
        if (txtDiagnosa != null) txtDiagnosa.clear();
        if (txtTindakan != null) txtTindakan.clear();
        listResepSementara.clear();
    }

    private void setupKomponenResep() {
        new Thread(() -> {
            List<Obat> allObat = obatDAO.getAllObat();
            Platform.runLater(() -> {
                listObatCache.clear();
                if (allObat != null) listObatCache.addAll(allObat);

                if (cbObat != null) {
                    cbObat.setItems(FXCollections.observableArrayList(listObatCache));
                    cbObat.setConverter(new StringConverter<Obat>() {
                        @Override
                        public String toString(Obat o) {
                            if (o == null) return "";
                            return o.getNamaObat() + " (Stok: " + o.getStok() + " " + o.getSatuan() + ")";
                        }
                        @Override
                        public Obat fromString(String string) { return null; }
                    });
                }
            });
        }).start();

        if (colResepNamaObat != null) colResepNamaObat.setCellValueFactory(new PropertyValueFactory<>("namaObat"));
        if (colResepJumlah != null) colResepJumlah.setCellValueFactory(new PropertyValueFactory<>("jumlah"));
        if (colResepSatuan != null) colResepSatuan.setCellValueFactory(new PropertyValueFactory<>("satuan"));
        if (colResepDosis != null) colResepDosis.setCellValueFactory(new PropertyValueFactory<>("dosis"));

        if (tableResepSementara != null) {
            tableResepSementara.setItems(listResepSementara);
        }
    }

    private void loadRekamMedisData() {
        new Thread(() -> {
            List<RekamMedis> list = rekamMedisDAO.getAllRekamMedis();
            Platform.runLater(() -> {
                listRekamMedis.setAll(list);
                if (tableRekamMedis != null) {
                    tableRekamMedis.setItems(listRekamMedis);
                }
            });
        }).start();
    }

    @FXML
    private void handleTambahObatKeResep() {
        Obat obatSelected = cbObat != null ? cbObat.getValue() : null;
        String strJml = txtJumlahObat != null ? txtJumlahObat.getText().trim() : "";
        String dosis = txtDosisObat != null ? txtDosisObat.getText().trim() : "";

        if (obatSelected == null || strJml.isEmpty() || dosis.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Validasi Resep", "Silakan pilih obat, isi jumlah, dan aturan pakai (dosis)!");
            return;
        }

        try {
            int jumlah = Integer.parseInt(strJml);
            if (jumlah <= 0) {
                showAlert(Alert.AlertType.WARNING, "Jumlah Tidak Valid", "Jumlah obat harus lebih dari 0!");
                return;
            }

            if (jumlah > obatSelected.getStok()) {
                showAlert(Alert.AlertType.WARNING, "Stok Tidak Cukup", 
                        "Stok obat " + obatSelected.getNamaObat() + " hanya tersisa " + obatSelected.getStok() + " " + obatSelected.getSatuan());
                return;
            }

            // Peringatan jika pasien memiliki alergi terhadap obat ini
            if (currentPasien != null && currentPasien.getAlergiObat() != null) {
                String alergi = currentPasien.getAlergiObat().toLowerCase();
                String namaObat = obatSelected.getNamaObat().toLowerCase();
                if (!alergi.equals("-") && (namaObat.contains(alergi) || alergi.contains(namaObat))) {
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                            "PERHATIAN! Pasien memiliki riwayat alergi: '" + currentPasien.getAlergiObat() + "'!\n" +
                            "Apakah Anda yakin tetap ingin meresepkan " + obatSelected.getNamaObat() + "?",
                            ButtonType.YES, ButtonType.NO);
                    confirm.setTitle("Peringatan Alergi Obat");
                    confirm.setHeaderText("POTENSI INTERAKSI ALERGI");
                    confirm.showAndWait();
                    if (confirm.getResult() != ButtonType.YES) {
                        return;
                    }
                }
            }

            DetailResep dr = new DetailResep(
                    obatSelected.getObatId(), 
                    obatSelected.getNamaObat(), 
                    jumlah, 
                    obatSelected.getSatuan(), 
                    dosis
            );

            listResepSementara.add(dr);

            if (cbObat != null) cbObat.setValue(null);
            if (txtJumlahObat != null) txtJumlahObat.clear();
            if (txtDosisObat != null) txtDosisObat.clear();

        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.WARNING, "Format Angka Salah", "Jumlah obat harus berupa angka!");
        }
    }

    @FXML
    private void handleHapusObatDariResep() {
        if (tableResepSementara != null) {
            DetailResep selected = tableResepSementara.getSelectionModel().getSelectedItem();
            if (selected != null) {
                listResepSementara.remove(selected);
            } else {
                showAlert(Alert.AlertType.INFORMATION, "Pilih Resep", "Pilih baris resep yang ingin dihapus dari tabel resep sementara.");
            }
        }
    }

    @FXML
    private void handleSimpanRekamMedis() {
        if (currentPasien == null) {
            showAlert(Alert.AlertType.WARNING, "Validasi Pasien", "Silakan pilih pasien dari antrean atau walk-in terlebih dahulu!");
            return;
        }

        if (currentDokter == null) {
            initDokterIdentitas();
            if (currentDokter == null) {
                showAlert(Alert.AlertType.ERROR, "Validasi Dokter", "Dokter pemeriksa tidak terdeteksi!");
                return;
            }
        }

        String diagnosa = txtDiagnosa != null ? txtDiagnosa.getText().trim() : "";
        if (diagnosa.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Validasi Medis", "Diagnosa pemeriksaan wajib diisi!");
            return;
        }

        try {
            RekamMedis rm = new RekamMedis();
            rm.setPasienId(currentPasien.getPasienId());
            rm.setDokterId(currentDokter.getPegawaiId());
            rm.setKeluhan(txtKeluhan != null ? txtKeluhan.getText().trim() : "-");
            rm.setDiagnosa(diagnosa);
            rm.setTindakan(txtTindakan != null ? txtTindakan.getText().trim() : "-");

            Integer antreanId = (currentAntrean != null) ? currentAntrean.getAntreanId() : null;
            rm.setAntreanId(antreanId);

            // SIMPAN SEMUA DALAM SATU TRANSAKSI ATOMIK (RM, Resep, Pengurangan Stok, Update Antrean)
            boolean sukses = rekamMedisDAO.simpanPemeriksaanLengkap(rm, listResepSementara, antreanId);

            if (sukses) {
                // Cetak Nota Struk Pemeriksaan
                StrukPrinter.cetakNota(currentPasien, currentDokter, rm, listResepSementara);

                String receiptPreview = StrukPrinter.buildReceiptText(currentPasien, currentDokter, rm, listResepSementara);

                // Dialog Sukses dengan Nota Struk
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Pemeriksaan Selesai");
                alert.setHeaderText("REKAM MEDIS & RESEP BERHASIL DISIMPAN!");
                
                TextArea ta = new TextArea(receiptPreview);
                ta.setEditable(false);
                ta.setWrapText(false);
                ta.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 11px;");
                ta.setPrefRowCount(15);
                ta.setPrefWidth(550);
                alert.getDialogPane().setContent(ta);
                alert.showAndWait();

                // Refresh data tabel & form
                loadRekamMedisData();
                setupKomponenResep(); // Refresh stok obat yang baru dikurangi
                refreshAntreanDropdown();
                clearPatientSelection();

            } else {
                showAlert(Alert.AlertType.ERROR, "Gagal", "Gagal menyimpan rekam medis ke database.");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error Transaksi Database", 
                    "Transaksi dibatalkan (Rollback):\n" + e.getMessage());
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
