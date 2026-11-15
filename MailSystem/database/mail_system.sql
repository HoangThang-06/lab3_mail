CREATE DATABASE IF NOT EXISTS mail_system CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE mail_system;

CREATE TABLE IF NOT EXISTS users (
  id INT AUTO_INCREMENT PRIMARY KEY,
  email VARCHAR(100) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL          -- SHA-256 hex
);

CREATE TABLE IF NOT EXISTS emails (
  id INT AUTO_INCREMENT PRIMARY KEY,
  sender VARCHAR(100) NOT NULL,
  receiver VARCHAR(100) NOT NULL,
  subject VARCHAR(255),
  content TEXT,
  sent_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  is_read TINYINT(1) DEFAULT 0,
  INDEX idx_receiver (receiver),
  INDEX idx_sender (sender)
);

-- Mật khẩu mẫu: 123456
INSERT IGNORE INTO users(email,password) VALUES
 ('thang@localhost','8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92'),
 ('tuyen@localhost','8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92');
