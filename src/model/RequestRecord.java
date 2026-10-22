package model;
public class RequestRecord {
	private int requestId;
	private int submittedBy;      // user_id of the person who complained
	private String department;
	private String room;
	private String category;      // ELECTRICAL, PLUMBING, FURNITURE, EQUIPMENT, OTHER
	private String description;   // can be null
	private String status;        // SUBMITTED, APPROVED, ..
	private int assignedTo;		  // user_id of staff
	private String priority;	// HIGH,MEDIUM,LOW
	private double estimatedAmount;

	public RequestRecord(int submittedBy, String department, String room,
	                     String category, String description) {
		this.submittedBy = submittedBy;
		this.department = department;
		this.room = room;
		this.category = category;
		this.description = description;
		this.status = "SUBMITTED";
		this.assignedTo = 0;
		this.priority = "MEDIUM";
		this.estimatedAmount = 0.0;
	}

	// new constructor
	public RequestRecord(int submittedBy, String department, String room,
	                     String category, String description,
	                     String priority, double estimatedAmount) {
		this.submittedBy = submittedBy;
		this.department = department;
		this.room = room;
		this.category = category;
		this.description = description;
		this.status = "SUBMITTED";
		this.assignedTo = 0;
		setPriority(priority);
		setEstimatedAmount(estimatedAmount);
	}

	// Getters
	public int getRequestId() { return requestId; }
	public int getSubmittedBy() { return submittedBy; }
	public String getDepartment() { return department; }
	public String getRoom() { return room; }
	public String getCategory() { return category; }
	public String getDescription() { return description; }
	public String getStatus() { return status; }
	public int getAssignedTo() { return assignedTo; }
	public String getpriority() { return priority; }
	public double getEstimatedAmount() { return estimatedAmount; }

	// Setters
	public void setRequestId(int requestId) { this.requestId = requestId; }
	public void setStatus(String status) { this.status = status; }
	public void setAssignedTo(int assignedTo) { this.assignedTo = assignedTo; }

	// default priority will be MEDIUM, else uppercase it.
	public void setPriority(String priority) {
		if (priority == null || priority.trim().isEmpty()) {
			this.priority = "MEDIUM";
		} else {
			this.priority = priority.trim().toUpperCase();
		}	
	}

	public void setEstimatedAmount(double estimatedAmount) {
		if (estimatedAmount < 0)
		{
			this.estimatedAmount = 0;
		}
		else
		{
			this.estimatedAmount = estimatedAmount;
		}	
	}
}