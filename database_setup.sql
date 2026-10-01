-- ============================================================
-- Smart Inventory Management System - Database Setup
-- Run this whole script in the MySQL command line client
-- (or MySQL Workbench) before running the Java application.
-- ============================================================

CREATE DATABASE IF NOT EXISTS inventory_db;
USE inventory_db;

-- ---------------------------------------------------------
-- Users table (login for the app)
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    role VARCHAR(20) DEFAULT 'ADMIN'
);

-- Default login -> username: admin , password: admin123
INSERT INTO users (username, password, role)
SELECT 'admin', 'admin123', 'ADMIN'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');

-- Demo staff login -> username: staff , password: staff123
-- Staff can view/search/add/edit products and do stock IN/OUT,
-- but cannot delete products or view Transaction History / Daily Report.
INSERT INTO users (username, password, role)
SELECT 'staff', 'staff123', 'STAFF'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'staff');

-- ---------------------------------------------------------
-- Categories table
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS categories (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

INSERT INTO categories (name)
SELECT * FROM (SELECT 'Electronics') AS tmp
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Electronics');

INSERT INTO categories (name)
SELECT * FROM (SELECT 'Groceries') AS tmp
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Groceries');

INSERT INTO categories (name)
SELECT * FROM (SELECT 'Stationery') AS tmp
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Stationery');

-- ---------------------------------------------------------
-- Products table (core inventory items)
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS products (
    id INT AUTO_INCREMENT PRIMARY KEY,
    product_code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    category_id INT,
    quantity INT NOT NULL DEFAULT 0,
    reorder_level INT NOT NULL DEFAULT 10,
    unit_price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    supplier VARCHAR(100),
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL
);

-- ---------------------------------------------------------
-- Stock transaction log (every IN/OUT movement)
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS stock_transactions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    product_id INT NOT NULL,
    transaction_type ENUM('IN','OUT') NOT NULL,
    quantity INT NOT NULL,
    transaction_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    remarks VARCHAR(255),
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);

-- ---------------------------------------------------------
-- Sample products (safe to remove or edit before your demo)
-- ---------------------------------------------------------
INSERT INTO products (product_code, name, category_id, quantity, reorder_level, unit_price, supplier)
SELECT 'ELEC-001', 'USB-C Cable', 1, 50, 15, 4.99, 'TechSupply Co'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE product_code = 'ELEC-001');

INSERT INTO products (product_code, name, category_id, quantity, reorder_level, unit_price, supplier)
SELECT 'STAT-001', 'A4 Notebook', 3, 8, 20, 1.50, 'PaperWorks Ltd'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE product_code = 'STAT-001');
