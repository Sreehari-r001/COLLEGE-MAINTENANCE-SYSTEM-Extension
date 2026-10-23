
package ui;

import database.DatabaseConnection;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import service.FundManagementService;
import java.math.BigDecimal;

public class MaintenanceStaffFrame extends JFrame {

    private final String email;
    private int staffId = -1;

    private JTable table;
    private DefaultTableModel model;

    private JLabel totalLabel;
    private JLabel activeLabel;
    private JLabel completedLabel;
    private JLabel availableFundLabel;


    public MaintenanceStaffFrame(String email) {
        this.email = email;

        setTitle("College Maintenance System - Maintenance Staff");
        setSize(950, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        staffId = getStaffId();

        add(createHeader(), BorderLayout.NORTH);
        add(createTablePanel(), BorderLayout.CENTER);
        add(createButtonPanel(), BorderLayout.SOUTH);

        if (staffId != -1) {
            refreshWorkTable();
        } else {
            JOptionPane.showMessageDialog(
                    this,
                    "Could not find a maintenance staff account for:\n"
                            + email,
                    "Staff Account Not Found",
                    JOptionPane.ERROR_MESSAGE
            );
        }

        setVisible(true);
    }

    private JPanel createHeader() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(
                BorderFactory.createEmptyBorder(15, 15, 5, 15)
        );

        JLabel heading = new JLabel("Assigned Maintenance Work");
        heading.setFont(new Font("SansSerif", Font.BOLD, 22));

        JLabel user = new JLabel("Logged in as: " + email);

        JPanel labels = new JPanel(new GridLayout(2, 1, 5, 5));
        labels.add(heading);
        labels.add(user);

        JPanel summary = new JPanel(new FlowLayout(FlowLayout.LEFT));

        totalLabel = new JLabel("Assigned: 0");
activeLabel = new JLabel("In Progress: 0");
completedLabel = new JLabel("Completed: 0");
availableFundLabel = new JLabel("Available Fund: Loading...");

summary.add(totalLabel);
summary.add(activeLabel);
summary.add(completedLabel);
summary.add(availableFundLabel);

        panel.add(labels, BorderLayout.NORTH);
        panel.add(summary, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createTablePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(
                BorderFactory.createEmptyBorder(5, 15, 5, 15)
        );

        String[] columns = {
                "Request ID",
                "Department",
                "Category",
                "Description",
                "Status"
        };

        model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(false);

        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        return panel;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(
                new FlowLayout(FlowLayout.CENTER, 10, 10)
        );

        JButton refreshButton = new JButton("Refresh");
        JButton detailsButton = new JButton("View Details");
        JButton updateButton = new JButton("Update Status");
        JButton dashboardButton = new JButton("Dashboard");
        JButton logoutButton = new JButton("Log Out");

        refreshButton.addActionListener(e -> refreshWorkTable());
        detailsButton.addActionListener(e -> viewDetails());
        updateButton.addActionListener(e -> updateStatus());

        dashboardButton.addActionListener(e -> {
            dispose();
            new RoleDashboard("Maintenance Staff", email);
        });

        logoutButton.addActionListener(e -> {
            dispose();
            new LoginFrame();
        });

        panel.add(refreshButton);
        panel.add(detailsButton);
        panel.add(updateButton);
        panel.add(dashboardButton);
        panel.add(logoutButton);

        return panel;
    }

    private int getStaffId() {
        String sql =
                "SELECT user_id FROM users "
                + "WHERE LOWER(email) = LOWER(?) "
                + "AND UPPER(REPLACE(role, '_', ' ')) = "
                + "'MAINTENANCE STAFF'";

        try {
            Connection connection =
                    DatabaseConnection.getInstance().getConnection();

            try (PreparedStatement statement =
                         connection.prepareStatement(sql)) {

                statement.setString(1, email);

                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        return result.getInt("user_id");
                    }
                }
            }
        } catch (SQLException ex) {
            showDatabaseError(
                    "Could not identify the maintenance staff account.", ex);
        }

        return -1;
    }

    private void refreshWorkTable() {
        if (staffId == -1) {
            return;
        }

        model.setRowCount(0);

        String sql =
        "SELECT request_id, department, category, "
        + "description, status, priority "
        + "FROM maintenance_requests "
        + "WHERE assigned_to = ? "
        + "ORDER BY CASE priority "
        + "WHEN 'HIGH' THEN 1 "
        + "WHEN 'MEDIUM' THEN 2 "
        + "WHEN 'LOW' THEN 3 "
        + "ELSE 4 END, request_id ASC";

        try {
            Connection connection =
                    DatabaseConnection.getInstance().getConnection();

            try (PreparedStatement statement =
                         connection.prepareStatement(sql)) {

                statement.setInt(1, staffId);

                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                                model.addRow(new Object[]{
                                result.getInt("request_id"),
                                result.getString("department"),
                                result.getString("category"),
                                result.getString("description"),
                                formatStatus(result.getString("status")),
                                result.getString("priority")
                        });
                    }
                }
            }

            updateSummaryCards();
            refreshAvailableFund();

        } catch (SQLException ex) {
            showDatabaseError("Could not load assigned work.", ex);
        }
    }

    private void updateSummaryCards() {
        String sql =
                "SELECT "
                + "COUNT(*) AS total, "
                + "SUM(CASE WHEN status = 'IN_PROGRESS' "
                + "THEN 1 ELSE 0 END) AS in_progress, "
                + "SUM(CASE WHEN status = 'COMPLETED' "
                + "THEN 1 ELSE 0 END) AS completed "
                + "FROM maintenance_requests "
                + "WHERE assigned_to = ?";

        try {
            Connection connection =
                    DatabaseConnection.getInstance().getConnection();

            try (PreparedStatement statement =
                         connection.prepareStatement(sql)) {

                statement.setInt(1, staffId);

                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        totalLabel.setText(
                                "Assigned: " + result.getInt("total"));
                        activeLabel.setText(
                                "In Progress: "
                                        + result.getInt("in_progress"));
                        completedLabel.setText(
                                "Completed: " + result.getInt("completed"));
                    }
                }
            }
        } catch (SQLException ex) {
            showDatabaseError("Could not load work statistics.", ex);
        }
    }
    private void refreshAvailableFund() {
    try {
        FundManagementService service = new FundManagementService();
        BigDecimal amount = service.getAvailableFund();

        availableFundLabel.setText(
                "Available Fund: \u20B9"
                + amount.setScale(2, java.math.RoundingMode.HALF_UP)
        );

    } catch (SQLException ex) {
        availableFundLabel.setText("Available Fund: Unavailable");
        System.err.println(
                "Could not load available fund: " + ex.getMessage()
        );
    }
}

    private void viewDetails() {
        int selectedRow = table.getSelectedRow();

        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(
                    this,
                    "Please select a request first."
            );
            return;
        }

        int row = table.convertRowIndexToModel(selectedRow);

        String details =
                "Request ID: " + model.getValueAt(row, 0)
                + "\nDepartment: " + model.getValueAt(row, 1)
                + "\nCategory: " + model.getValueAt(row, 2)
                + "\nDescription: " + model.getValueAt(row, 3)
                + "\nStatus: " + model.getValueAt(row, 4);

        JOptionPane.showMessageDialog(
                this,
                details,
                "Maintenance Request Details",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    
private void updateStatus() {
    int selectedRow = table.getSelectedRow();

    if (selectedRow == -1) {
        JOptionPane.showMessageDialog(
                this, "Please select a request first."
        );
        return;
    }

    int row = table.convertRowIndexToModel(selectedRow);

    int requestId = Integer.parseInt(
            model.getValueAt(row, 0).toString()
    );

    String currentStatus = model.getValueAt(row, 4)
            .toString().toUpperCase().replace(' ', '_');

    if ("ASSIGNED".equals(currentStatus)) {
        String amountText = JOptionPane.showInputDialog(
                this,
                "Enter the estimated maintenance cost (₹):"
        );

        if (amountText == null) {
            return;
        }

        try {
            BigDecimal amount = new BigDecimal(amountText.trim());

            FundManagementService service =
                    new FundManagementService();

            String result = service.estimateCostAndStartWork(
                    requestId, staffId, amount
            );

            JOptionPane.showMessageDialog(this, result);
            refreshWorkTable();

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Enter a valid positive amount, such as 1500.00."
            );
        } catch (SQLException ex) {
            showDatabaseError(
                    "Could not process the maintenance estimate.", ex
            );
        }

        return;
    }

    if (!"IN_PROGRESS".equals(currentStatus)) {
        JOptionPane.showMessageDialog(
                this,
                "Only assigned requests can be started, and "
                        + "in-progress requests can be completed."
        );
        return;
    }

    int confirm = JOptionPane.showConfirmDialog(
            this,
            "Mark request " + requestId + " as COMPLETED?",
            "Confirm Status Update",
            JOptionPane.YES_NO_OPTION
    );

    if (confirm != JOptionPane.YES_OPTION) {
        return;
    }

    String updateSql =
            "UPDATE maintenance_requests SET status = 'COMPLETED' "
            + "WHERE request_id = ? AND assigned_to = ? "
            + "AND status = 'IN_PROGRESS'";

    String historySql =
            "INSERT INTO request_history "
            + "(request_id, old_status, new_status, changed_by, remarks) "
            + "VALUES (?, ?, ?, ?, ?)";

    try (Connection connection =
                 DatabaseConnection.getInstance().getConnection()) {

        connection.setAutoCommit(false);

        try {
            try (PreparedStatement statement =
                         connection.prepareStatement(updateSql)) {
                statement.setInt(1, requestId);
                statement.setInt(2, staffId);

                if (statement.executeUpdate() != 1) {
                    throw new SQLException(
                            "Request could not be completed. Refresh and retry."
                    );
                }
            }

            try (PreparedStatement statement =
                         connection.prepareStatement(historySql)) {
                statement.setInt(1, requestId);
                statement.setString(2, "IN_PROGRESS");
                statement.setString(3, "COMPLETED");
                statement.setInt(4, staffId);
                statement.setString(5, "Completed by maintenance staff");
                statement.executeUpdate();
            }

            connection.commit();

        } catch (SQLException | RuntimeException ex) {
            connection.rollback();
            throw ex;

        } finally {
            connection.setAutoCommit(true);
        }

        refreshWorkTable();

        JOptionPane.showMessageDialog(
                this, "Request " + requestId + " is now COMPLETED."
        );

    } catch (SQLException ex) {
        showDatabaseError(
                "Could not update the request status.", ex
        );
    }
}

    private String formatStatus(String status) {
        if (status == null) {
            return "UNKNOWN";
        }

        return status.replace('_', ' ');
    }

    private void showDatabaseError(String message, SQLException ex) {
        ex.printStackTrace();

        JOptionPane.showMessageDialog(
                this,
                message + "\n" + ex.getMessage(),
                "Database Error",
                JOptionPane.ERROR_MESSAGE
        );
    }
}