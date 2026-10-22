
package service;

import java.util.ArrayList;
import java.util.List;

public class RequestStore {

    public static class RequestItem {
        public String id;
        public String email;
        public String role;
        public String location;
        public String department;
        public String category;
        public String description;
        public String status;

        // Constructor for existing sample-request format
        public RequestItem(
                String id,
                String department,
                String category,
                String description,
                String status
        ) {
            this(
                    id,
                    "-",
                    "-",
                    "-",
                    department,
                    category,
                    description,
                    status
            );
        }

        // Constructor for newly submitted requests
        public RequestItem(
                String id,
                String email,
                String role,
                String location,
                String department,
                String category,
                String description,
                String status
        ) {
            this.id = id;
            this.email = email;
            this.role = role;
            this.location = location;
            this.department = department;
            this.category = category;
            this.description = description;
            this.status = status;
        }
    }

    // Start with an empty request list.
    // No hardcoded sample requests are created.
    private static final ArrayList<RequestItem> requests =
            new ArrayList<>();

    public static List<RequestItem> getRequests() {
        return requests;
    }

    public static void addRequest(RequestItem request) {
        if (request != null) {
            requests.add(request);
        }
    }

    public static RequestItem findById(String id) {
        if (id == null) {
            return null;
        }

        for (RequestItem request : requests) {
            if (id.equals(request.id)) {
                return request;
            }
        }

        return null;
    }

    public static int countStatus(String status) {
        if (status == null) {
            return 0;
        }

        int count = 0;

        for (RequestItem request : requests) {
            if (request.status != null
                    && status.equalsIgnoreCase(request.status)) {
                count++;
            }
        }

        return count;
    }

    public static int getTotalCount() {
        return requests.size();
    }
}