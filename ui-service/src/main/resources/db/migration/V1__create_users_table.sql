CREATE TABLE IF NOT EXISTS users (
    id       VARCHAR(36)  NOT NULL PRIMARY KEY,
    username VARCHAR(50)  NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role     VARCHAR(20)  NOT NULL DEFAULT 'CUSTOMER'
);

CREATE INDEX idx_users_username ON users(username);
