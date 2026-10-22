
package service;

import database.DatabaseConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class FundManagementService {

    public String estimateCostAndStartWork(
            int requestId, int staffId, BigDecimal estimatedAmount)
            throws SQLException {

        if (estimatedAmount == null
                || estimatedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return "Estimated amount must be greater than zero.";
        }

        try (Connection con =
                DatabaseConnection.getInstance().getConnection()) {

            boolean originalAutoCommit = con.getAutoCommit();
            con.setAutoCommit(false);

            try {
                String requestSql =
                        "SELECT status FROM maintenance_requests "
                        + "WHERE request_id = ? AND assigned_to = ? "
                        + "FOR UPDATE";

                String status;

                try (PreparedStatement ps =
                             con.prepareStatement(requestSql)) {

                    ps.setInt(1, requestId);
                    ps.setInt(2, staffId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            con.rollback();
                            return "Request not found or not assigned to you.";
                        }

                        status = rs.getString("status");
                    }
                }

                if (!"ASSIGNED".equalsIgnoreCase(status)) {
                    con.rollback();
                    return "Only assigned requests can start work.";
                }

                String fundSql =
                        "SELECT available_amount FROM hod_fund "
                        + "WHERE fund_id = 1 FOR UPDATE";

                BigDecimal availableAmount;

                try (PreparedStatement ps =
                             con.prepareStatement(fundSql);
                     ResultSet rs = ps.executeQuery()) {

                    if (!rs.next()) {
                        throw new SQLException(
                                "HOD fund record not found."
                        );
                    }

                    availableAmount =
                            rs.getBigDecimal("available_amount");
                }

                String estimateSql =
                        "UPDATE maintenance_requests "
                        + "SET estimated_amount = ? "
                        + "WHERE request_id = ? "
                        + "AND assigned_to = ? "
                        + "AND status = 'ASSIGNED'";

                try (PreparedStatement ps =
                             con.prepareStatement(estimateSql)) {

                    ps.setBigDecimal(1, estimatedAmount);
                    ps.setInt(2, requestId);
                    ps.setInt(3, staffId);

                    if (ps.executeUpdate() != 1) {
                        throw new SQLException(
                                "Could not save the cost estimate."
                        );
                    }
                }

                if (availableAmount.compareTo(estimatedAmount) <= 0) {
                    con.commit();

                    return String.format(
                            "Fund not available. Available: ₹%.2f, "
                            + "estimated cost: ₹%.2f. Work has not started.",
                            availableAmount.doubleValue(),
                            estimatedAmount.doubleValue()
                    );
                }

                String deductSql =
                        "UPDATE hod_fund "
                        + "SET available_amount = available_amount - ? "
                        + "WHERE fund_id = 1 AND available_amount > ?";

                try (PreparedStatement ps =
                             con.prepareStatement(deductSql)) {

                    ps.setBigDecimal(1, estimatedAmount);
                    ps.setBigDecimal(2, estimatedAmount);

                    if (ps.executeUpdate() != 1) {
                        throw new SQLException(
                                "Fund deduction failed."
                        );
                    }
                }

                String startSql =
                        "UPDATE maintenance_requests "
                        + "SET status = 'IN_PROGRESS' "
                        + "WHERE request_id = ? "
                        + "AND assigned_to = ? "
                        + "AND status = 'ASSIGNED'";

                try (PreparedStatement ps =
                             con.prepareStatement(startSql)) {

                    ps.setInt(1, requestId);
                    ps.setInt(2, staffId);

                    if (ps.executeUpdate() != 1) {
                        throw new SQLException(
                                "Could not start maintenance work."
                        );
                    }
                }

                String historySql =
                        "INSERT INTO request_history "
                        + "(request_id, old_status, new_status, "
                        + "changed_by, remarks) "
                        + "VALUES (?, ?, ?, ?, ?)";

                try (PreparedStatement ps =
                             con.prepareStatement(historySql)) {

                    ps.setInt(1, requestId);
                    ps.setString(2, "ASSIGNED");
                    ps.setString(3, "IN_PROGRESS");
                    ps.setInt(4, staffId);
                    ps.setString(
                            5, "Cost accepted and work started"
                    );

                    ps.executeUpdate();
                }

                con.commit();

                return String.format(
                        "Estimate accepted. Work started. "
                        + "Remaining fund: ₹%.2f",
                        availableAmount.subtract(estimatedAmount)
                                .doubleValue()
                );

            } catch (SQLException | RuntimeException e) {
                try {
                    con.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }

                throw e;

            } finally {
                try {
                    con.setAutoCommit(originalAutoCommit);
                } catch (SQLException e) {
                    System.err.println(
                            "Could not restore auto-commit: "
                            + e.getMessage()
                    );
                }
            }
        }
    }
  public BigDecimal getAvailableFund() throws SQLException {
    String sql =
            "SELECT available_amount FROM hod_fund WHERE fund_id = 1";

    try (Connection con =
                 DatabaseConnection.getInstance().getConnection();
         PreparedStatement ps = con.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {

        if (rs.next()) {
            return rs.getBigDecimal("available_amount");
        }

        throw new SQLException("HOD fund record not found.");
    }
}  
}
