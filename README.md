# Smart Inventory Management System

A desktop inventory management application built with **Java Swing** (GUI) and
**MySQL** (database), connected via **JDBC**. It supports full product
CRUD, category management, stock IN/OUT tracking with a complete transaction
log, low-stock alerts, and a live dashboard summary.

---

## Features

- **Secure login** screen before accessing the system
- **Add / Update / Delete** products
- **Search** products by name or product code
- **Category** assignment via dropdown (linked to a `categories` table)
- **Stock IN / Stock OUT** with automatic quantity adjustment
- **Transaction History** — every stock movement is logged with a timestamp
- **Low Stock Alert** — instantly filter items at/below their reorder level
- **Low-stock row highlighting** — flagged rows are colored directly in the
  main table
- **Live dashboard summary** — Total Products, Total Stock Value, and Low
  Stock count, updated automatically after every action

---

## Tech Stack

| Layer      | Technology                  |
|------------|------------------------------|
| GUI        | Java Swing                   |
| Database   | MySQL 8                      |
| Connector  | JDBC (`mysql-connector-j`)   |
| Language   | Java (single-file project)   |

---

## Project Files

```
SmartInventoryManagementSystem.java   <- entire application (all classes)
mysql-connector-j-8.0.31.jar          <- MySQL JDBC driver
database_setup.sql                    <- run this first to create the database
RunInventoryApp.bat                   <- compiles (if needed) and runs the app
RunInventoryApp.vbs                   <- silent launcher (no console window)
```

All Java classes — models, DAOs (data access layer), and UI screens — are
nested inside the single `SmartInventoryManagementSystem` class for easy
submission and grading, while still following a clean layered structure
internally:

- **Models:** `Product`, `Category`, `TransactionRecord`
- **DAOs (SQL layer):** `ProductDAO`, `CategoryDAO`, `UserDAO`
- **UI:** `LoginFrame`, `MainDashboard`
- **Utility:** `DBConnection`

---

## Database Schema

Run `database_setup.sql` in the MySQL command line client (or MySQL
Workbench) before running the application. It creates:

- `users` — login credentials (default: `admin` / `admin123`)
- `categories` — product categories (Electronics, Groceries, Stationery)
- `products` — core inventory data (code, name, quantity, reorder level,
  price, supplier)
- `stock_transactions` — a full log of every stock IN/OUT movement

The full schema is also included as a comment block at the top of
`SmartInventoryManagementSystem.java` for reference.

---

## Setup & Run Instructions

### 1. Create the database
Open the MySQL command line client and run:
```sql
SOURCE database_setup.sql;
```
(or copy-paste its contents directly into the client)

### 2. Configure the connection
Open `SmartInventoryManagementSystem.java` and edit these lines near the top
(inside the `DBConnection` class) to match your MySQL setup:
```java
private static final String DB_URL =
        "jdbc:mysql://localhost:3306/inventory_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
private static final String DB_USER = "root";
private static final String DB_PASSWORD = "your_mysql_password";
```

### 3. Compile
Make sure `mysql-connector-j-8.0.31.jar` is in the same folder, then run:
```cmd
javac -cp mysql-connector-j-8.0.31.jar SmartInventoryManagementSystem.java
```

### 4. Run
```cmd
java -cp ".;mysql-connector-j-8.0.31.jar" SmartInventoryManagementSystem
```
(On Mac/Linux use `:` instead of `;` in the classpath)

### 5. Log in
```
Username: admin
Password: admin123
```

**Shortcut option:** double-click `RunInventoryApp.bat` to compile (only if
needed) and launch in one step, or use `RunInventoryApp.vbs` for a silent
launch with no console window — useful for a Desktop shortcut.

---

## Known Limitations / Future Scope

- **Passwords are stored in plain text** in the `users` table for simplicity.
  A production version should hash passwords (e.g. with BCrypt) before
  storing or comparing them.
- **Single admin role** — a future version could add role-based access
  (Admin vs Staff) to restrict who can delete products or view reports.
- **No CSV/PDF export** yet — exporting the product list or transaction
  history to a file would be a natural next feature.
- **No charts** — a bar/pie chart of stock value by category would make the
  dashboard more visual.

---
> Download `mysql-connector-j-8.0.31.jar` from https://dev.mysql.com/downloads/connector/j/
> and place it in this folder before compiling.

## Author

MANCHALA HEMANTH — Engineering Student
