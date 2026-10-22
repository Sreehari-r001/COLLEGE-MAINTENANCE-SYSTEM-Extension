
package dao;

import database.DatabaseConnection;
import model.RequestRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;

public class MaintenanceRequestDAO {

    // ---------- CREATE ----------

    public int insertRequest(RequestRecord r) {
        String sql = "INSERT INTO maintenance_requests "
		   + "(submitted_by, department, room, category, description, "
		   + "priority, estimated_amount) "
		   + "VALUES (?, ?, ?, ?, ?, ?, ?)";

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet keys = null;
        int newId = -1;

        try {
            con = DatabaseConnection.getInstance().getConnection();
            ps = con.prepareStatement(
                    sql, Statement.RETURN_GENERATED_KEYS);

            ps.setInt(1, r.getSubmittedBy());
            ps.setString(2, r.getDepartment());
            ps.setString(3, r.getRoom());
            ps.setString(4, r.getCategory());

            if (r.getDescription() == null
                    || r.getDescription().trim().isEmpty()) {
                ps.setNull(5, Types.VARCHAR);
            } else {
                ps.setString(5, r.getDescription());
            }
            ps.setString(6, r.getpriority());
			ps.setDouble(7, r.getEstimatedAmount());
            ps.executeUpdate();

            keys = ps.getGeneratedKeys();
            if (keys.next()) {
                newId = keys.getInt(1);
                r.setRequestId(newId);
            }

        } catch (SQLException e) {
            System.out.println(
                    "Could not insert request: " + e.getMessage());
        } finally {
            closeAll(con, ps, keys);
        }

        if (newId > 0) {
            writeHistory(newId, null, "SUBMITTED",
                    r.getSubmittedBy(), "Complaint submitted");
        }

        return newId;
    }

    // ---------- READ ----------

    public RequestRecord getRequestById(int requestId) {
        String sql = "SELECT request_id, submitted_by, department, room, "
                + "category, description, status, assigned_to, priority, estimated_amount "
                + "FROM maintenance_requests WHERE request_id = ?";

        try (Connection con =
                     DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, requestId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return makeRecord(rs);
                }
            }

        } catch (SQLException e) {
            System.out.println(
                    "Could not get request: " + e.getMessage());
        }

        return null;
    }

    public ArrayList<RequestRecord> getAllRequests() {
        String sql = "SELECT request_id, submitted_by, department, room, "
                + "category, description, status, assigned_to, priority, estimated_amount "
                + "FROM maintenance_requests ORDER BY request_id";

        ArrayList<RequestRecord> list = new ArrayList<>();

        try (Connection con =
                     DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(makeRecord(rs));
            }

        } catch (SQLException e) {
            System.out.println(
                    "Could not get all requests: " + e.getMessage());
        }

        return list;
    }

	public ArrayList<RequestRecord> getApprovedAndAssignedByPriority() {

		String sql = "SELECT request_id, submitted_by, department, room, category, "
		           + "description, status, assigned_to, priority, estimated_amount "
		           + "FROM maintenance_requests "
		           + "WHERE status IN ('APPROVED', 'ASSIGNED') "
		           + "ORDER BY CASE priority "
		           + "WHEN 'HIGH' THEN 1 "
		           + "WHEN 'MEDIUM' THEN 2 "
		           + "ELSE 3 END, request_id";

		ArrayList<RequestRecord> list = new ArrayList<RequestRecord>();
		Connection con = null;
		PreparedStatement ps = null;
		ResultSet rs = null;

		try {
			con = DatabaseConnection.getInstance().getConnection();
			ps = con.prepareStatement(sql);
			rs = ps.executeQuery();

			while (rs.next()) {
				list.add(makeRecord(rs));
			}

		} catch (SQLException e) {
			System.out.println("Could not get requests by priority: " + e.getMessage());
		} finally {
			closeAll(con, ps, rs);
		}
		return list;
	}

    // Gets requests for a specific submitter and checks the actual
    // maintenance_requests count on the same database connection.
    public ArrayList<RequestRecord> getRequestsBySubmitter(
            int submitterId) {

        String sql = "SELECT request_id, submitted_by, department, room, "
                + "category, description, status, assigned_to, priority, estimated_amount "
                + "FROM maintenance_requests WHERE submitted_by = ? "
                + "ORDER BY request_id";

        ArrayList<RequestRecord> list = new ArrayList<>();

        try (Connection con =
                     DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            System.out.println("DEBUG: Database = " + con.getCatalog());
            System.out.println("DEBUG: JDBC URL = "
                    + con.getMetaData().getURL());
            System.out.println("DEBUG: Student user ID = " + submitterId);

            // Diagnostic: count all requests using this same connection.
            try (Statement check = con.createStatement();
                 ResultSet count = check.executeQuery(
                         "SELECT COUNT(*) FROM maintenance_requests")) {

                if (count.next()) {
                    System.out.println(
                            "DEBUG: TOTAL REQUESTS IN DATABASE = "
                                    + count.getInt(1));
                }
            }

            ps.setInt(1, submitterId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RequestRecord request = makeRecord(rs);
                    list.add(request);

                    System.out.println(
                            "DEBUG: Request ID = "
                                    + request.getRequestId()
                                    + ", Status = "
                                    + request.getStatus());
                }
            }

            System.out.println(
                    "DEBUG: Requests returned = " + list.size());

        } catch (SQLException e) {
            System.out.println(
                    "Could not get requests by submitter: "
                            + e.getMessage());
            e.printStackTrace();
        }

        return list;
    }

    public ArrayList<RequestRecord> getRequestsByStatus(String status) {
        String sql = "SELECT request_id, submitted_by, department, room, "
                + "category, description, status, assigned_to, priority, estimated_amount "
                + "FROM maintenance_requests WHERE status = ? "
                + "ORDER BY request_id";

        ArrayList<RequestRecord> list = new ArrayList<>();

        try (Connection con =
                     DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, status);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(makeRecord(rs));
                }
            }

        } catch (SQLException e) {
            System.out.println(
                    "Could not get requests by status: " + e.getMessage());
        }

        return list;
    }

    // ---------- UPDATE ----------

    public boolean updateStatus(
            int requestId, String newStatus,
            int changedBy, String remarks) {

        if (!userExists(changedBy)) {
            System.out.println("No user with id " + changedBy);
            return false;
        }

        String oldStatus = readStatus(requestId);

        if (oldStatus == null) {
            System.out.println("No complaint with id " + requestId);
            return false;
        }

        String sql = "UPDATE maintenance_requests "
                + "SET status = ? WHERE request_id = ?";

        boolean done = false;

        try (Connection con =
                     DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, newStatus);
            ps.setInt(2, requestId);
            done = ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.out.println(
                    "Could not update status: " + e.getMessage());
        }

        if (done) {
            done = writeHistory(
                    requestId, oldStatus, newStatus, changedBy, remarks);
        }

        return done;
    }

    public boolean assignStaff(
            int requestId, int staffId,
            int changedBy, String remarks) {

        if (!userExists(changedBy)) {
            System.out.println("No user with id " + changedBy);
            return false;
        }

        String oldStatus = readStatus(requestId);

        if (oldStatus == null) {
            System.out.println("No complaint with id " + requestId);
            return false;
        }

        String sql = "UPDATE maintenance_requests "
                + "SET assigned_to = ?, status = 'ASSIGNED' "
                + "WHERE request_id = ?";

        boolean done = false;

        try (Connection con =
                     DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, staffId);
            ps.setInt(2, requestId);
            done = ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.out.println(
                    "Could not assign staff: " + e.getMessage());
        }

        if (done) {
            done = writeHistory(
                    requestId, oldStatus, "ASSIGNED", changedBy, remarks);
        }

        return done;
    }

    // ---------- HISTORY ----------

    public ArrayList<String> getHistoryForRequest(int requestId) {
        String sql = "SELECT old_status, new_status, changed_by, "
                + "changed_at, remarks FROM request_history "
                + "WHERE request_id = ? ORDER BY history_id";

        ArrayList<String> list = new ArrayList<>();

        try (Connection con =
                     DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, requestId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String oldStatus = rs.getString("old_status");
                    if (oldStatus == null) {
                        oldStatus = "(none)";
                    }

                    String remarks = rs.getString("remarks");
                    if (remarks == null) {
                        remarks = "";
                    }

                    list.add(oldStatus + " -> "
                            + rs.getString("new_status")
                            + " | by user " + rs.getInt("changed_by")
                            + " | " + rs.getTimestamp("changed_at")
                            + " | " + remarks);
                }
            }

        } catch (SQLException e) {
            System.out.println(
                    "Could not get history: " + e.getMessage());
        }

        return list;
    }

    // ---------- ASSIGNED STAFF ----------

    public ArrayList<RequestRecord> getRequestsByAssignedStaff(
            int staffId) {

        String sql = "SELECT request_id, submitted_by, department, room, "
                + "category, description, status, assigned_to, priority, estimated_amount "
                + "FROM maintenance_requests WHERE assigned_to = ? "
                + "ORDER BY request_id";

        ArrayList<RequestRecord> list = new ArrayList<>();

        try (Connection con =
                     DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, staffId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(makeRecord(rs));
                }
            }

        } catch (SQLException e) {
            System.out.println(
                    "Could not get requests by assigned staff: "
                            + e.getMessage());
        }

        return list;
    }

    // ---------- USER LOOKUP ----------

    public int getUserIdByEmail(String email) {
        String sql = "SELECT user_id FROM users WHERE email = ?";

        try (Connection con =
                     DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, email);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("user_id");
                }
            }

        } catch (SQLException e) {
            System.out.println(
                    "Could not find user by email: " + e.getMessage());
        }

        return -1;
    }

    // ---------- PRIVATE HELPERS ----------

    private boolean writeHistory(
            int requestId, String oldStatus, String newStatus,
            int changedBy, String remarks) {

        String sql = "INSERT INTO request_history "
                + "(request_id, old_status, new_status, changed_by, remarks) "
                + "VALUES (?, ?, ?, ?, ?)";

        try (Connection con =
                     DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, requestId);

            if (oldStatus == null) {
                ps.setNull(2, Types.VARCHAR);
            } else {
                ps.setString(2, oldStatus);
            }

            ps.setString(3, newStatus);
            ps.setInt(4, changedBy);

            if (remarks == null || remarks.trim().isEmpty()) {
                ps.setNull(5, Types.VARCHAR);
            } else {
                ps.setString(5, remarks);
            }

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.out.println(
                    "Could not write history: " + e.getMessage());
            return false;
        }
    }

    private String readStatus(int requestId) {
        String sql = "SELECT status FROM maintenance_requests "
                + "WHERE request_id = ?";

        try (Connection con =
                     DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, requestId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("status");
                }
            }

        } catch (SQLException e) {
            System.out.println(
                    "Could not read status: " + e.getMessage());
        }

        return null;
    }

    private boolean userExists(int userId) {
        String sql = "SELECT user_id FROM users WHERE user_id = ?";

        try (Connection con =
                     DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            System.out.println(
                    "Could not check user: " + e.getMessage());
            return false;
        }
    }

    private RequestRecord makeRecord(ResultSet rs) throws SQLException {
        RequestRecord request = new RequestRecord(
                rs.getInt("submitted_by"),
                rs.getString("department"),
                rs.getString("room"),
                rs.getString("category"),
                rs.getString("description")
        );

        request.setRequestId(rs.getInt("request_id"));
        request.setStatus(rs.getString("status"));
        request.setAssignedTo(rs.getInt("assigned_to"));
        request.setPriority(rs.getString("priority"));
		request.setEstimatedAmount(rs.getDouble("estimated_amount"));
        return request;
    }

    private void closeAll(
            Connection con, PreparedStatement ps, ResultSet rs) {

        try {
            if (rs != null) {
                rs.close();
            }
        } catch (SQLException e) {
            System.out.println(
                    "Could not close ResultSet: " + e.getMessage());
        }

        try {
            if (ps != null) {
                ps.close();
            }
        } catch (SQLException e) {
            System.out.println(
                    "Could not close statement: " + e.getMessage());
        }

        try {
            if (con != null) {
                con.close();
            }
        } catch (SQLException e) {
            System.out.println(
                    "Could not close connection: " + e.getMessage());
        }
    }
}