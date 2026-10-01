# Smart Inventory Management System

A full inventory management solution with **two interfaces sharing one MySQL
database**: a Java Swing desktop app and a browser-based web app. Both support
product management, stock tracking, barcode scanning, and automatic red
alerts when stock runs low.

---

## Features

### Core (both apps)
- **Secure login** with role-based access (`ADMIN` and `STAFF`)
- **Product management** — add, update, delete, and search products
- **Stock IN / Stock OUT** with full transaction logging
- **Low Stock Alerts** — red banners, badges, and highlighted rows whenever
  an item is at or below its reorder level
- **Barcode scanning** — any USB/Bluetooth scanner (keyboard-emulation mode)
  jumps straight to a product or offers to add it as new
- **Live dashboard** — total products, total stock value, and low stock count

### Desktop app (`SmartInventoryManagementSystem.java`)
- Light, classy Swing interface — navy header, clean cards, rounded buttons
- Blinking red alert banner and color-coded table rows (LOW / OUT badges)
- Transaction History viewer

### Web app (`InventoryWebApp.java`)
- Runs at `http://localhost:8080` — no frameworks, pure Java `HttpServer`
- **Separate Admin / Staff login tabs**, with a role-mismatch warning if you
  pick the wrong one
- **Role-based dashboards:**
  - **Staff:** view, search, add, edit, and delete products; Stock IN/OUT;
    barcode scanning
  - **Admin:** everything Staff has, **plus** Transaction History and the
    Daily Report
- **Daily Stock Movement Report** — every date in the last 30 days is shown,
  including days with zero activity, so gaps are visible rather than hidden
- **S.No. column** that always renumbers 1, 2, 3… after a delete, while the
  real database ID stays stable underneath for data integrity
- Light/Dark theme toggle, "remember username," and show/hide password

---

## Tech Stack

| Layer      | Technology                          |
|------------|--------------------------------------|
| Desktop UI | Java Swing                           |
| Web UI     | Plain HTML/CSS/JS served by `com.sun.net.httpserver` |
| Database   | MySQL 8                              |
| Connector  | JDBC (`mysql-connector-j`)           |
| Language   | Java (each app is a single file)     |

> The web app's Daily Report uses a `WITH RECURSIVE` SQL query, which
> requires **MySQL 8.0.1 or newer**.

---

## Project Files

```
SmartInventoryManagementSystem.java   <- desktop app (all classes)
InventoryWebApp.java                  <- web app (serves http://localhost:8080)
database_setup.sql                    <- run this first to create the database
RunInventoryApp.bat                   <- compiles (if needed) and runs the desktop app
RunInventoryApp.vbs                   <- silent launcher for the desktop app
README.md                             <- this file
```

> `mysql-connector-j-8.0.31.jar` is **not** included in this repository.
> Download it from
> [dev.mysql.com/downloads/connector/j](https://dev.mysql.com/downloads/connector/j/)
> and place it in the project folder before compiling either app.

Each Java file keeps its classes nested for easy grading/submission, while
following a clean layered structure internally:

- **Models:** `Product`, `Category`, `TransactionRecord`
- **DAOs (SQL layer):** `ProductDAO`, `CategoryDAO`, `UserDAO`
- **UI:** `LoginFrame`, `MainDashboard` (desktop) / HTML+JS single page (web)
- **Utility:** `DBConnection` / `getConnection()`

---

## Database Schema

Run `database_setup.sql` in the MySQL command line client (or MySQL
Workbench) before running either application. It creates:

- **`users`** — login credentials and role
  - Default admin: `admin` / `admin123`
  - Default staff: `staff` / `staff123`
- **`categories`** — product categories (Electronics, Groceries, Stationery)
- **`products`** — core inventory data (code, name, quantity, reorder level,
  price, supplier)
- **`stock_transactions`** — a full log of every stock IN/OUT movement

```sql
users              (id, username, password, role)
categories         (id, name)
products           (id, product_code, name, category_id -> categories.id,
                     quantity, reorder_level, unit_price, supplier, last_updated)
stock_transactions (id, product_id -> products.id, transaction_type,
                     quantity, transaction_date, remarks)
```

---

## Setup & Run Instructions

### 1. Create the database
```sql
SOURCE database_setup.sql;
```
(or paste its contents into the MySQL client directly)

### 2. Configure the connection
In **both** `SmartInventoryManagementSystem.java` and `InventoryWebApp.java`,
update these lines to match your MySQL setup:
```java
private static final String DB_URL =
        "jdbc:mysql://localhost:3306/inventory_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
private static final String DB_USER = "root";
private static final String DB_PASSWORD = "your_mysql_password";
```

### 3. Run the desktop app
```cmd
javac -cp mysql-connector-j-8.0.31.jar SmartInventoryManagementSystem.java
java -cp ".;mysql-connector-j-8.0.31.jar" SmartInventoryManagementSystem
```
Or just double-click `RunInventoryApp.bat`.

### 4. Run the web app
```cmd
javac -cp mysql-connector-j-8.0.31.jar InventoryWebApp.java
java -cp ".;mysql-connector-j-8.0.31.jar" InventoryWebApp
```
Then open **http://localhost:8080** in a browser.

*(On Mac/Linux use `:` instead of `;` in the classpath.)*

### 5. Log in
| Role  | Username | Password   |
|-------|----------|------------|
| Admin | `admin`  | `admin123` |
| Staff | `staff`  | `staff123` |

On the web app, pick the matching tab (Admin/Staff) before logging in.

---

## Known Limitations / Future Scope

- **Passwords are stored in plain text.** A production version should hash
  them (e.g. with BCrypt) before storing or comparing.
- **No CSV/PDF export** yet for the product list or reports.
- **No charts** — a bar/pie chart of stock value by category would make the
  dashboard more visual.
- **No HTTPS or sessions** on the web app — it's a lightweight local demo
  server, not meant for deployment on the open internet.
- **Barcode scanning is keyboard-emulation based** — it works with any
  standard USB/Bluetooth scanner, but there's no camera-based scanning yet.

---

## Author

MANCHALA HEMANTH — Engineering Student
