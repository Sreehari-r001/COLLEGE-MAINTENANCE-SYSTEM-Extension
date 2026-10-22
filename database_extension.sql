USE college_maintenance;

ALTER TABLE maintenance_requests
    ADD COLUMN estimated_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN priority ENUM('LOW','MEDIUM','HIGH') NOT NULL DEFAULT 'MEDIUM';

CREATE TABLE IF NOT EXISTS hod_fund (
    fund_id INT PRIMARY KEY,
    available_amount DECIMAL(10,2) NOT NULL
);

INSERT IGNORE INTO hod_fund (fund_id, available_amount)
VALUES (1, 10000.00);
