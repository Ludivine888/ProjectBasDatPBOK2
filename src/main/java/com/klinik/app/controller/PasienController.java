package com.klinik.app.controller;

import com.klinik.app.dao.PasienDAO;
import com.klinik.app.model.JenisKelamin;
import com.klinik.app.model.Pasien;
import com.klinik.app.util.PasswordUtil;
import com.klinik.app.util.UserSession;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.sql.SQLException;
import java.util.List;

public class PasienController {

    @FXML private TextField txtSearch; 
    @FXML private TextField txtNik;
    @FXML private TextField txtNamaPasien;
    @FXML private DatePicker dpTanggalLahir;
    @FXML private ComboBox<JenisKelamin> cbJenisKelamin;
    @FXML private TextField txtNoHp;
    @FXML private TextArea txtAlamat;
    @FXML private TextField txtAlergiObat;

    @FXML private Button btnSimpan;
    @FXML private Button btnUpdate;
    @FXML private Button btnHapus;
    @FXML private Label lblTotalPasien;

    @FXML private TableView<Pasien> tvPasien;
    @FXML private TableColumn<Pasien, Integer> colPasienId;
    @FXML private TableColumn<Pasien, String> colNik;
    @FXML private TableColumn<Pasien, String> colNamaPasien;
    @FXML private TableColumn<Pasien, String> colKeluhanUtama;
    @FXML private TableColumn<Pasien, Object> colTanggalLahir;
    @FXML private TableColumn<Pasien, JenisKelamin> colJenisKelamin;
    @FXML private TableColumn<Pasien, String> colNoHp;
    @FXML private TableColumn<Pasien, String> colAlamat;
    @FXML private TableColumn<Pasien, String> colAlergiObat;

    private final PasienDAO pasienDAO = new PasienDAO();
    private final ObservableList<Pasien> masterData = FXCollections.observableArrayList();
    private FilteredList<Pasien> filteredData;
    private Pasien selectedPasien;

    @FXML
    public void initialize() {
        if (cbJenisKelamin != null) {
            cbJenisKelamin.setItems(FXCollections.observableArrayList(JenisKelamin.values()));
        }

        if (colPasienId != null) colPasienId.setCellValueFactory(new PropertyValueFactory<>("pasienId"));
        if (colNik != null) colNik.setCellValueFactory(new PropertyValueFactory<>("nik"));
        if (colNamaPasien != null) colNamaPasien.setCellValueFactory(new PropertyValueFactory<>("namaPasien"));
        if (colKeluhanUtama != null) colKeluhanUtama.setCellValueFactory(new PropertyValueFactory<>("keluhanUtama"));
        if (colTanggalLahir != null) colTanggalLahir.setCellValueFactory(new PropertyValueFactory<>("tanggalLahir"));
        if (colJenisKelamin != null) colJenisKelamin.setCellValueFactory(new PropertyValueFactory<>("jenisKelamin"));
        if (colNoHp != null) colNoHp.setCellValueFactory(new PropertyValueFactory<>("noHp"));
        if (colAlamat != null) colAlamat.setCellValueFactory(new PropertyValueFactory<>("alamat"));
        if (colAlergiObat != null) colAlergiObat.setCellValueFactory(new PropertyValueFactory<>("alergiObat"));

        // FAIL-CLOSED SECURITY: Hanya Admin yang dapat memanipulasi data master pasien
        UserSession session = UserSession.getInstance();
        if (session == null || !session.isAdmin()) {
            disableFormInputs();
        }

        setupPencarian();
        loadTableData();

        if (tvPasien != null) {
            tvPasien.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
                if (newSelection != null) {
                    selectedPasien = newSelection;
                    populateForm(selectedPasien);
                }
            });
        }
    }

    private void setupPencarian() {
        filteredData = new FilteredList<>(masterData, p -> true);

        if (txtSearch != null) {
            txtSearch.textProperty().addListener((observable, oldValue, newValue) -> {
                filteredData.setPredicate(pasien -> {
                    if (newValue == null || newValue.trim().isEmpty()) {
                        return true;
                    }

                    String lowerFilter = newValue.toLowerCase().trim();
                    if (pasien.getNamaPasien() != null && pasien.getNamaPasien().toLowerCase().contains(lowerFilter)) return true;
                    if (pasien.getNik() != null && pasien.getNik().contains(lowerFilter)) return true;
                    if (pasien.getNoHp() != null && pasien.getNoHp().contains(lowerFilter)) return true;
                    if (pasien.getKeluhanUtama() != null && pasien.getKeluhanUtama().toLowerCase().contains(lowerFilter)) return true;
                    return false;
                });
                updateTotalLabel();
            });
        }

        if (tvPasien != null) {
            tvPasien.setItems(filteredData);
        }
    }

    private void loadTableData() {
        new Thread(() -> {
            List<Pasien> data = pasienDAO.getAllPasien();
            Platform.runLater(() -> {
                masterData.setAll(data);
                updateTotalLabel();
            });
        }).start();
    }

    private void updateTotalLabel() {
        if (lblTotalPasien != null) {
            int displayed = filteredData != null ? filteredData.size() : masterData.size();
            lblTotalPasien.setText("Total Pasien: " + displayed + (masterData.size() != displayed ? " (dari " + masterData.size() + ")" : ""));
        }
    }

    private void disableFormInputs() {
        if (txtNik != null) txtNik.setDisable(true);
        if (txtNamaPasien != null) txtNamaPasien.setDisable(true);
        if (dpTanggalLahir != null) dpTanggalLahir.setDisable(true);
        if (cbJenisKelamin != null) cbJenisKelamin.setDisable(true);
        if (txtNoHp != null) txtNoHp.setDisable(true);
        if (txtAlamat != null) txtAlamat.setDisable(true);
        if (txtAlergiObat != null) txtAlergiObat.setDisable(true);
        if (btnSimpan != null) btnSimpan.setDisable(true);
        if (btnUpdate != null) btnUpdate.setDisable(true);
        if (btnHapus != null) btnHapus.setDisable(true);
    }

    private void populateForm(Pasien p) {
        if (txtNik != null) txtNik.setText(p.getNik());
        if (txtNamaPasien != null) txtNamaPasien.setText(p.getNamaPasien());
        if (dpTanggalLahir != null) dpTanggalLahir.setValue(p.getTanggalLahir());
        if (cbJenisKelamin != null) cbJenisKelamin.setValue(p.getJenisKelamin());
        if (txtNoHp != null) txtNoHp.setText(p.getNoHp());
        if (txtAlamat != null) txtAlamat.setText(p.getAlamat());
        if (txtAlergiObat != null) txtAlergiObat.setText(p.getAlergiObat());
    }

    @FXML
    private void handleSimpan() {
        UserSession session = UserSession.getInstance();
        if (session == null || !session.isAdmin()) {
            showAlert(Alert.AlertType.ERROR, "Akses Ditolak", "Hanya Administrator yang memiliki wewenang mendaftarkan pasien baru!");
            return;
        }

        String nik = txtNik != null ? txtNik.getText().trim() : "";
        String nama = txtNamaPasien != null ? txtNamaPasien.getText().trim() : "";

        if (nik.isEmpty() || nama.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Validasi Gagal", "NIK dan Nama Pasien wajib diisi!");
            return;
        }

        if (!nik.matches("\\d{16}")) {
            showAlert(Alert.AlertType.WARNING, "Format NIK Salah", "NIK harus terdiri dari 16 digit angka!");
            return;
        }

        Pasien p = new Pasien();
        p.setNik(nik);
        p.setPasswordHash(PasswordUtil.hashPassword("123456")); // default password terhash aman
        p.setNamaPasien(nama);
        p.setTanggalLahir(dpTanggalLahir != null ? dpTanggalLahir.getValue() : null);
        p.setJenisKelamin(cbJenisKelamin != null ? cbJenisKelamin.getValue() : null);
        p.setNoHp(txtNoHp != null ? txtNoHp.getText().trim() : "");
        p.setAlamat(txtAlamat != null ? txtAlamat.getText().trim() : "");
        p.setAlergiObat(txtAlergiObat != null ? txtAlergiObat.getText().trim() : "-");

        try {
            if (pasienDAO.insertPasien(p)) {
                showAlert(Alert.AlertType.INFORMATION, "Sukses", "Data pasien baru berhasil disimpan!");
                loadTableData();
                handleClear();
            } else {
                showAlert(Alert.AlertType.ERROR, "Gagal", "Gagal menyimpan pasien ke database.");
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) {
                showAlert(Alert.AlertType.ERROR, "Duplikat NIK", "Pasien dengan NIK " + nik + " sudah terdaftar di sistem!");
            } else {
                showAlert(Alert.AlertType.ERROR, "Error Database", e.getMessage());
            }
        }
    }

    @FXML
    private void handleUpdate() {
        UserSession session = UserSession.getInstance();
        if (session == null || !session.isAdmin()) {
            showAlert(Alert.AlertType.ERROR, "Akses Ditolak", "Hanya Administrator yang berwenang mengubah data pasien!");
            return;
        }

        if (selectedPasien == null) {
            showAlert(Alert.AlertType.WARNING, "Peringatan", "Pilih data pasien dari tabel yang ingin diperbarui!");
            return;
        }

        String nik = txtNik != null ? txtNik.getText().trim() : "";
        String nama = txtNamaPasien != null ? txtNamaPasien.getText().trim() : "";

        if (nik.isEmpty() || nama.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Validasi Gagal", "NIK dan Nama Pasien wajib diisi!");
            return;
        }

        if (!nik.matches("\\d{16}")) {
            showAlert(Alert.AlertType.WARNING, "Format NIK Salah", "NIK harus terdiri dari 16 digit angka!");
            return;
        }

        selectedPasien.setNik(nik);
        selectedPasien.setNamaPasien(nama);
        selectedPasien.setTanggalLahir(dpTanggalLahir != null ? dpTanggalLahir.getValue() : null);
        selectedPasien.setJenisKelamin(cbJenisKelamin != null ? cbJenisKelamin.getValue() : null);
        selectedPasien.setNoHp(txtNoHp != null ? txtNoHp.getText().trim() : "");
        selectedPasien.setAlamat(txtAlamat != null ? txtAlamat.getText().trim() : "");
        selectedPasien.setAlergiObat(txtAlergiObat != null ? txtAlergiObat.getText().trim() : "-");

        try {
            if (pasienDAO.updatePasien(selectedPasien)) {
                showAlert(Alert.AlertType.INFORMATION, "Sukses", "Data pasien berhasil diperbarui!");
                loadTableData();
                handleClear();
            } else {
                showAlert(Alert.AlertType.ERROR, "Gagal", "Gagal memperbarui data pasien.");
            }
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Error Database", e.getMessage());
        }
    }

    @FXML
    private void handleHapus() {
        UserSession session = UserSession.getInstance();
        if (session == null || !session.isAdmin()) {
            showAlert(Alert.AlertType.ERROR, "Akses Ditolak", "Hanya Administrator yang berwenang menghapus data pasien!");
            return;
        }

        if (selectedPasien == null) {
            showAlert(Alert.AlertType.WARNING, "Peringatan", "Pilih data pasien dari tabel yang ingin dihapus!");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, 
                "Apakah Anda yakin ingin menghapus data pasien " + selectedPasien.getNamaPasien() + " (NIK: " + selectedPasien.getNik() + ")?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Konfirmasi Hapus");
        confirm.setHeaderText(null);
        confirm.showAndWait();

        if (confirm.getResult() == ButtonType.YES) {
            try {
                if (pasienDAO.deletePasien(selectedPasien.getPasienId())) {
                    showAlert(Alert.AlertType.INFORMATION, "Sukses", "Data pasien berhasil dihapus!");
                    loadTableData();
                    handleClear();
                } else {
                    showAlert(Alert.AlertType.ERROR, "Gagal", "Gagal menghapus data pasien.");
                }
            } catch (SQLException e) {
                if (e.getErrorCode() == 1451) {
                    showAlert(Alert.AlertType.ERROR, "Tidak Dapat Dihapus", "Pasien ini sudah memiliki riwayat antrean atau rekam medis sehingga tidak dapat dihapus!");
                } else {
                    showAlert(Alert.AlertType.ERROR, "Error Database", e.getMessage());
                }
            }
        }
    }

    @FXML
    private void handleClear() {
        selectedPasien = null;
        if (txtNik != null) txtNik.clear();
        if (txtNamaPasien != null) txtNamaPasien.clear();
        if (dpTanggalLahir != null) dpTanggalLahir.setValue(null);
        if (cbJenisKelamin != null) cbJenisKelamin.setValue(null);
        if (txtNoHp != null) txtNoHp.clear();
        if (txtAlamat != null) txtAlamat.clear();
        if (txtAlergiObat != null) txtAlergiObat.clear();
        if (tvPasien != null) tvPasien.getSelectionModel().clearSelection();
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
