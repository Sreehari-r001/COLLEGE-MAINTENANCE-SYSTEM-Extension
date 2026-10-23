
package ui;

import database.DatabaseConnection;
import dao.MaintenanceRequestDAO;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class HodReviewFrame extends JFrame {

    private final String hodEmail;
    private JTable requestTable;
    private DefaultTableModel model;

    public HodReviewFrame(String email) {
        hodEmail = email;

        setTitle("HOD - Review Maintenance Requests");
        setSize(1200, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        );

        // Heading
        JLabel title = new JLabel("Review Maintenance Requests");
        title.setFont(new Font("Arial", Font.BOLD, 22));

        JLabel emailLabel = new JLabel("Logged in as: " + hodEmail);

        JPanel heading = new JPanel(new BorderLayout());
        heading.add(title, BorderLayout.NORTH);
        heading.add(emailLabel, BorderLayout.SOUTH);

        mainPanel.add(heading, BorderLayout.NORTH);

        // Request table
        String[] columns = {
            "Request ID",
            "Submitted By",
            "Role",
            "Location",
            "Category",
            "Description",
            "Priority",
            "Status"
        };

        model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        requestTable = new JTable(model);
        requestTable.setSelectionMode(
            ListSelectionModel.SINGLE_SELECTION
        );
                requestTable.setAutoCreateRowSorter(false);
        requestTable.setRowHeight(25);

        mainPanel.add(
            new JScrollPane(requestTable),
            BorderLayout.CENTER
        );

        // Buttons
        JButton refreshButton = new JButton("Refresh");
        JButton approveButton = new JButton("Approve Request");
        JButton rejectButton = new JButton("Reject Request");
        JButton priorityButton = new JButton("Set Priority");
        JButton assignButton = new JButton("Assign Staff");
        JButton dashboardButton = new JButton("Dashboard");
        JButton logoutButton = new JButton("Log Out");

        refreshButton.addActionListener(e -> refreshTable());
        approveButton.addActionListener(e -> updateStatus("APPROVED"));
        rejectButton.addActionListener(e -> updateStatus("REJECTED"));
        priorityButton.addActionListener(e -> setPriority());
        assignButton.addActionListener(e -> assignSelectedRequest());

        dashboardButton.addActionListener(e -> goToDashboard());
        logoutButton.addActionListener(e -> logout());

        JPanel buttons = new JPanel(
            new FlowLayout(FlowLayout.CENTER, 8, 5)
        );

        buttons.add(refreshButton);
        buttons.add(approveButton);
        buttons.add(rejectButton);
        buttons.add(priorityButton);
        buttons.add(assignButton);
        buttons.add(dashboardButton);
        buttons.add(logoutButton);

        mainPanel.add(buttons, BorderLayout.SOUTH);

        setContentPane(mainPanel);

        refreshTable();
        setVisible(true);
    }

    // Load requests from MySQL
    private void refreshTable() {
        model.setRowCount(0);

        String sql =
            "SELECT mr.request_id, u.email, u.role, " +
            "mr.department, mr.room, mr.category, " +
            "mr.description, mr.priority, mr.status " +
            "FROM maintenance_requests mr " +
            "JOIN users u ON mr.submitted_by = u.user_id " +
            "ORDER BY CASE mr.priority " +
            "WHEN 'HIGH' THEN 1 " +
            "WHEN 'MEDIUM' THEN 2 " +
            "WHEN 'LOW' THEN 3 " +
            "ELSE 4 END, mr.request_id ASC";

        try (
            Connection con =
                DatabaseConnection.getInstance().getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
            ResultSet rs = ps.executeQuery()
        ) {
            while (rs.next()) {
                String status = rs.getString("status");

                if ("SUBMITTED".equalsIgnoreCase(status)) {
                    status = "PENDING";
                }

                model.addRow(new Object[] {
                    rs.getInt("request_id"),
                    rs.getString("email"),
                    rs.getString("role"),
                    rs.getString("room"),
                    rs.getString("category"),
                    rs.getString("description"),
                    rs.getString("priority"),
                    status
                });
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(
                this,
                "Could not load requests from MySQL.\n" +
                    e.getMessage(),
                "Database Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    // Approve or reject a pending request
    private void updateStatus(String newStatus) {
        int selectedRow = requestTable.getSelectedRow();

        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(
                this, "Please select a request first."
            );
            return;
        }

        int row = requestTable.convertRowIndexToModel(selectedRow);

        int requestId = Integer.parseInt(
            model.getValueAt(row, 0).toString()
        );

        // Status is now column index 7
        String currentStatus = model.getValueAt(row, 7).toString();

        if (!currentStatus.equalsIgnoreCase("PENDING")) {
            JOptionPane.showMessageDialog(
                this,
                "Only pending requests can be approved or rejected."
            );
            return;
        }

        String action = newStatus.equals("APPROVED")
            ? "approve" : "reject";

        int choice = JOptionPane.showConfirmDialog(
            this,
            "Are you sure you want to " + action +
                " request REQ-" + requestId + "?",
            "Confirm Decision",
            JOptionPane.YES_NO_OPTION
        );

        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        String sql =
            "UPDATE maintenance_requests SET status = ? " +
            "WHERE request_id = ? AND status = 'SUBMITTED'";

        try (
            Connection con =
                DatabaseConnection.getInstance().getConnection();
            PreparedStatement ps = con.prepareStatement(sql)
        ) {
            ps.setString(1, newStatus);
            ps.setInt(2, requestId);

            int updated = ps.executeUpdate();

            if (updated == 1) {
                JOptionPane.showMessageDialog(
                    this,
                    "Request REQ-" + requestId +
                        " is now " + newStatus + "."
                );
            } else {
                JOptionPane.showMessageDialog(
                    this,
                    "The request was not updated. Refresh and try again."
                );
            }

            refreshTable();

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(
                this,
                "Could not update request status.\n" +
                    e.getMessage(),
                "Database Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    // Set priority for an approved request
    private void setPriority() {
        int selectedRow = requestTable.getSelectedRow();

        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(
                this, "Please select a request first."
            );
            return;
        }

        int row = requestTable.convertRowIndexToModel(selectedRow);

        int requestId = Integer.parseInt(
            model.getValueAt(row, 0).toString()
        );

        String status = model.getValueAt(row, 7).toString();

        if (!status.equalsIgnoreCase("APPROVED")) {
            JOptionPane.showMessageDialog(
                this,
                "Approve the request before setting its priority."
            );
            return;
        }

        String[] priorities = {"HIGH", "MEDIUM", "LOW"};

        JComboBox<String> priorityBox =
            new JComboBox<>(priorities);

        String currentPriority =
            model.getValueAt(row, 6).toString();

        priorityBox.setSelectedItem(currentPriority);

        int choice = JOptionPane.showConfirmDialog(
            this,
            priorityBox,
            "Set Priority for REQ-" + requestId,
            JOptionPane.OK_CANCEL_OPTION
        );

        if (choice != JOptionPane.OK_OPTION) {
            return;
        }

        String selectedPriority =
            priorityBox.getSelectedItem().toString();

        String sql =
            "UPDATE maintenance_requests SET priority = ? " +
            "WHERE request_id = ? AND status = 'APPROVED'";

        try (
            Connection con =
                DatabaseConnection.getInstance().getConnection();
            PreparedStatement ps = con.prepareStatement(sql)
        ) {
            ps.setString(1, selectedPriority);
            ps.setInt(2, requestId);

            int updated = ps.executeUpdate();

            if (updated == 1) {
                JOptionPane.showMessageDialog(
                    this,
                    "Priority for REQ-" + requestId +
                        " set to " + selectedPriority + "."
                );
            } else {
                JOptionPane.showMessageDialog(
                    this,
                    "Priority was not updated. Refresh and try again."
                );
            }

            refreshTable();

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(
                this,
                "Could not update priority.\n" + e.getMessage(),
                "Database Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    // Assign an approved request to maintenance staff
    private void assignSelectedRequest() {
        int selectedRow = requestTable.getSelectedRow();

        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(
                this, "Please select a request first."
            );
            return;
        }

        int row = requestTable.convertRowIndexToModel(selectedRow);

        int requestId = Integer.parseInt(
            model.getValueAt(row, 0).toString()
        );

        // Status is now column index 7
        String status = model.getValueAt(row, 7).toString();

        if (!status.equalsIgnoreCase("APPROVED")) {
            JOptionPane.showMessageDialog(
                this,
                "Only approved requests can be assigned to staff."
            );
            return;
        }

        String sql =
            "SELECT user_id, name, email FROM users " +
            "WHERE role = 'MAINTENANCE_STAFF' ORDER BY name";

        List<Integer> staffIds = new ArrayList<>();
        List<String> staffLabels = new ArrayList<>();

        try (
            Connection con =
                DatabaseConnection.getInstance().getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
            ResultSet rs = ps.executeQuery()
        ) {
            while (rs.next()) {
                staffIds.add(rs.getInt("user_id"));
                staffLabels.add(
                    rs.getString("name") + " (" +
                    rs.getString("email") + ")"
                );
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(
                this,
                "Could not load maintenance staff.\n" +
                    e.getMessage()
            );
            return;
        }

        if (staffIds.isEmpty()) {
            JOptionPane.showMessageDialog(
                this, "No maintenance staff accounts were found."
            );
            return;
        }

        JComboBox<String> staffBox =
            new JComboBox<>(staffLabels.toArray(new String[0]));

        int choice = JOptionPane.showConfirmDialog(
            this,
            staffBox,
            "Assign Request REQ-" + requestId,
            JOptionPane.OK_CANCEL_OPTION
        );

        if (choice != JOptionPane.OK_OPTION) {
            return;
        }

        int index = staffBox.getSelectedIndex();

        if (index < 0) {
            JOptionPane.showMessageDialog(
                this, "Please select a maintenance staff member."
            );
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
            this,
            "Assign request REQ-" + requestId + " to " +
                staffLabels.get(index) + "?",
            "Confirm Assignment",
            JOptionPane.YES_NO_OPTION
        );

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            MaintenanceRequestDAO dao = new MaintenanceRequestDAO();
            int hodId = dao.getUserIdByEmail(hodEmail);

            if (hodId == -1) {
                JOptionPane.showMessageDialog(
                    this,
                    "Could not identify the logged-in HOD account."
                );
                return;
            }

            dao.assignStaff(
                requestId,
                staffIds.get(index),
                hodId,
                "Assigned by HOD"
            );

            JOptionPane.showMessageDialog(
                this,
                "Request assigned to " + staffLabels.get(index) + "."
            );

            refreshTable();

        } catch (Exception e) {
            JOptionPane.showMessageDialog(
                this,
                "Could not assign the request.\n" + e.getMessage(),
                "Assignment Failed",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void goToDashboard() {
        dispose();
        new RoleDashboard("HOD", hodEmail);
    }

    public static int getTotalRequestCount() {
        return getCount(
            "SELECT COUNT(*) FROM maintenance_requests"
        );
    }

    public static int getPendingRequestCount() {
        return getCount(
            "SELECT COUNT(*) FROM maintenance_requests " +
            "WHERE status = 'SUBMITTED'"
        );
    }

    public static int getApprovedRequestCount() {
        return getCount(
            "SELECT COUNT(*) FROM maintenance_requests " +
            "WHERE status = 'APPROVED'"
        );
    }

    public static int getCompletedRequestCount() {
        return getCount(
            "SELECT COUNT(*) FROM maintenance_requests " +
            "WHERE status = 'COMPLETED'"
        );
    }

    private static int getCount(String sql) {
        try (
            Connection con =
                DatabaseConnection.getInstance().getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
            ResultSet rs = ps.executeQuery()
        ) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.out.println(
                "Could not retrieve request count: " +
                    e.getMessage()
            );
        }

        return 0;
    }

    private void logout() {
        dispose();
        new LoginFrame();
    }
}
