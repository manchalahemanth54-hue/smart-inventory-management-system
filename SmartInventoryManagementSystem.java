import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Smart Inventory Management System - single file edition.
 * Java Swing GUI + MySQL (JDBC) backend.
 *
 * ----------------------------------------------------------------
 * SETUP
 * ----------------------------------------------------------------
 * 1) Run this SQL in MySQL first (Workbench or CLI):
 *
 *    CREATE DATABASE IF NOT EXISTS inventory_db;
 *    USE inventory_db;
 *
 *    CREATE TABLE IF NOT EXISTS users (
 *        id INT AUTO_INCREMENT PRIMARY KEY,
 *        username VARCHAR(50) NOT NULL UNIQUE,
 *        password VARCHAR(100) NOT NULL,
 *        role VARCHAR(20) DEFAULT 'ADMIN'
 *    );
 *    INSERT INTO users (username, password, role)
 *    SELECT 'admin', 'admin123', 'ADMIN'
 *    WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');
 *
 *    CREATE TABLE IF NOT EXISTS categories (
 *        id INT AUTO_INCREMENT PRIMARY KEY,
 *        name VARCHAR(50) NOT NULL UNIQUE
 *    );
 *    INSERT INTO categories (name) SELECT * FROM (SELECT 'Electronics') t
 *        WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name='Electronics');
 *    INSERT INTO categories (name) SELECT * FROM (SELECT 'Groceries') t
 *        WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name='Groceries');
 *    INSERT INTO categories (name) SELECT * FROM (SELECT 'Stationery') t
 *        WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name='Stationery');
 *
 *    CREATE TABLE IF NOT EXISTS products (
 *        id INT AUTO_INCREMENT PRIMARY KEY,
 *        product_code VARCHAR(30) NOT NULL UNIQUE,
 *        name VARCHAR(100) NOT NULL,
 *        category_id INT,
 *        quantity INT NOT NULL DEFAULT 0,
 *        reorder_level INT NOT NULL DEFAULT 10,
 *        unit_price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
 *        supplier VARCHAR(100),
 *        last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 *        FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL
 *    );
 *
 *    CREATE TABLE IF NOT EXISTS stock_transactions (
 *        id INT AUTO_INCREMENT PRIMARY KEY,
 *        product_id INT NOT NULL,
 *        transaction_type ENUM('IN','OUT') NOT NULL,
 *        quantity INT NOT NULL,
 *        transaction_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 *        remarks VARCHAR(255),
 *        FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
 *    );
 *
 * 2) Edit the DB_URL / DB_USER / DB_PASSWORD constants in the
 *    DBConnection class below to match your MySQL setup.
 *
 * 3) Put the MySQL JDBC driver jar (mysql-connector-j-8.x.x.jar) on your
 *    classpath, then compile and run:
 *
 *    javac -cp mysql-connector-j-8.3.0.jar SmartInventoryManagementSystem.java
 *    java  -cp .:mysql-connector-j-8.3.0.jar SmartInventoryManagementSystem
 *    (Windows: use ; instead of : in the classpath)
 *
 * 4) Login with admin / admin123.
 * ----------------------------------------------------------------
 */
public class SmartInventoryManagementSystem {

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        Theme.install();
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }

    // =====================================================================
    //  DB CONNECTION
    // =====================================================================
    static class DBConnection {
        private static final String DB_URL =
                "jdbc:mysql://localhost:3306/inventory_db?useSSL=false&serverTimezone=UTC";
        private static final String DB_USER = "root";
        private static final String DB_PASSWORD = "mysql123"; // <-- change this

        private static Connection connection;

        static Connection getConnection() {
            try {
                if (connection == null || connection.isClosed()) {
                    Class.forName("com.mysql.cj.jdbc.Driver");
                    connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                }
            } catch (ClassNotFoundException e) {
                System.err.println("MySQL JDBC Driver not found. Add mysql-connector-j to your classpath.");
                e.printStackTrace();
            } catch (SQLException e) {
                System.err.println("Failed to connect to database: " + e.getMessage());
                e.printStackTrace();
            }
            return connection;
        }
    }

    // =====================================================================
    //  MODELS
    // =====================================================================
    static class Product {
        int id;
        String productCode;
        String name;
        int categoryId;
        String categoryName;
        int quantity;
        int reorderLevel;
        BigDecimal unitPrice;
        String supplier;

        boolean isLowStock() {
            return quantity <= reorderLevel;
        }
    }

    static class Category {
        int id;
        String name;

        Category(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name; // shows nicely in JComboBox
        }
    }

    static class TransactionRecord {
        int id;
        java.sql.Timestamp transactionDate;
        String productCode;
        String productName;
        String type;
        int quantity;
        String remarks;
    }

    // =====================================================================
    //  DAO: PRODUCTS
    // =====================================================================
    static class ProductDAO {

        boolean addProduct(Product p) {
            String sql = "INSERT INTO products (product_code, name, category_id, quantity, reorder_level, unit_price, supplier) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, p.productCode);
                ps.setString(2, p.name);
                ps.setInt(3, p.categoryId);
                ps.setInt(4, p.quantity);
                ps.setInt(5, p.reorderLevel);
                ps.setBigDecimal(6, p.unitPrice);
                ps.setString(7, p.supplier);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        List<Product> getAllProducts() {
            return runProductQuery(
                    "SELECT p.*, c.name AS category_name FROM products p " +
                    "LEFT JOIN categories c ON p.category_id = c.id ORDER BY p.id", null);
        }

        List<Product> searchProducts(String keyword) {
            String like = "%" + keyword + "%";
            return runProductQuery(
                    "SELECT p.*, c.name AS category_name FROM products p " +
                    "LEFT JOIN categories c ON p.category_id = c.id " +
                    "WHERE p.name LIKE ? OR p.product_code LIKE ? ORDER BY p.id",
                    new Object[]{like, like});
        }

        Product getProductById(int id) {
            List<Product> list = runProductQuery(
                    "SELECT p.*, c.name AS category_name FROM products p " +
                    "LEFT JOIN categories c ON p.category_id = c.id WHERE p.id = ?",
                    new Object[]{id});
            return list.isEmpty() ? null : list.get(0);
        }

        List<Product> getLowStockProducts() {
            return runProductQuery(
                    "SELECT p.*, c.name AS category_name FROM products p " +
                    "LEFT JOIN categories c ON p.category_id = c.id " +
                    "WHERE p.quantity <= p.reorder_level ORDER BY p.quantity ASC", null);
        }

        private List<Product> runProductQuery(String sql, Object[] params) {
            List<Product> list = new ArrayList<>();
            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                if (params != null) {
                    for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapRow(rs));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        boolean updateProduct(Product p) {
            String sql = "UPDATE products SET product_code=?, name=?, category_id=?, quantity=?, " +
                         "reorder_level=?, unit_price=?, supplier=? WHERE id=?";
            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, p.productCode);
                ps.setString(2, p.name);
                ps.setInt(3, p.categoryId);
                ps.setInt(4, p.quantity);
                ps.setInt(5, p.reorderLevel);
                ps.setBigDecimal(6, p.unitPrice);
                ps.setString(7, p.supplier);
                ps.setInt(8, p.id);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        boolean deleteProduct(int id) {
            String sql = "DELETE FROM products WHERE id = ?";
            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, id);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        boolean adjustStock(int productId, int quantityChange, String type, String remarks) {
            String updateSql = type.equals("IN")
                    ? "UPDATE products SET quantity = quantity + ? WHERE id = ?"
                    : "UPDATE products SET quantity = quantity - ? WHERE id = ? AND quantity >= ?";
            String logSql = "INSERT INTO stock_transactions (product_id, transaction_type, quantity, remarks) " +
                             "VALUES (?, ?, ?, ?)";

            Connection con = null;
            try {
                con = DBConnection.getConnection();
                con.setAutoCommit(false);

                try (PreparedStatement ps = con.prepareStatement(updateSql)) {
                    ps.setInt(1, quantityChange);
                    ps.setInt(2, productId);
                    if (type.equals("OUT")) ps.setInt(3, quantityChange);
                    if (ps.executeUpdate() == 0) {
                        con.rollback();
                        return false;
                    }
                }
                try (PreparedStatement ps = con.prepareStatement(logSql)) {
                    ps.setInt(1, productId);
                    ps.setString(2, type);
                    ps.setInt(3, quantityChange);
                    ps.setString(4, remarks);
                    ps.executeUpdate();
                }
                con.commit();
                return true;
            } catch (SQLException e) {
                e.printStackTrace();
                try { if (con != null) con.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
                return false;
            } finally {
                try { if (con != null) con.setAutoCommit(true); } catch (SQLException e) { e.printStackTrace(); }
            }
        }

        int getTotalProductCount() { return countQuery("SELECT COUNT(*) FROM products"); }

        int getLowStockCount() { return countQuery("SELECT COUNT(*) FROM products WHERE quantity <= reorder_level"); }

        BigDecimal getTotalStockValue() {
            String sql = "SELECT COALESCE(SUM(quantity * unit_price), 0) AS total_value FROM products";
            try (Connection con = DBConnection.getConnection();
                 Statement st = con.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                if (rs.next()) return rs.getBigDecimal("total_value");
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return BigDecimal.ZERO;
        }

        List<TransactionRecord> getTransactionHistory() {
            List<TransactionRecord> list = new ArrayList<>();
            String sql = "SELECT t.id, t.transaction_date, p.product_code, p.name, " +
                         "t.transaction_type, t.quantity, t.remarks " +
                         "FROM stock_transactions t " +
                         "JOIN products p ON t.product_id = p.id " +
                         "ORDER BY t.transaction_date DESC";
            try (Connection con = DBConnection.getConnection();
                 Statement st = con.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    TransactionRecord t = new TransactionRecord();
                    t.id = rs.getInt("id");
                    t.transactionDate = rs.getTimestamp("transaction_date");
                    t.productCode = rs.getString("product_code");
                    t.productName = rs.getString("name");
                    t.type = rs.getString("transaction_type");
                    t.quantity = rs.getInt("quantity");
                    t.remarks = rs.getString("remarks");
                    list.add(t);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        private int countQuery(String sql) {
            try (Connection con = DBConnection.getConnection();
                 Statement st = con.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                if (rs.next()) return rs.getInt(1);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return 0;
        }

        private Product mapRow(ResultSet rs) throws SQLException {
            Product p = new Product();
            p.id = rs.getInt("id");
            p.productCode = rs.getString("product_code");
            p.name = rs.getString("name");
            p.categoryId = rs.getInt("category_id");
            p.categoryName = rs.getString("category_name");
            p.quantity = rs.getInt("quantity");
            p.reorderLevel = rs.getInt("reorder_level");
            p.unitPrice = rs.getBigDecimal("unit_price");
            p.supplier = rs.getString("supplier");
            return p;
        }
    }

    // =====================================================================
    //  DAO: CATEGORIES
    // =====================================================================
    static class CategoryDAO {
        List<Category> getAllCategories() {
            List<Category> list = new ArrayList<>();
            String sql = "SELECT * FROM categories ORDER BY name";
            try (Connection con = DBConnection.getConnection();
                 Statement st = con.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) list.add(new Category(rs.getInt("id"), rs.getString("name")));
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }
    }

    // =====================================================================
    //  DAO: USERS (login)
    // =====================================================================
    static class UserDAO {
        boolean validateLogin(String username, String password) {
            String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
            Connection con = DBConnection.getConnection();
            if (con == null) {
                System.err.println("Cannot validate login: no database connection.");
                return false;
            }
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, password);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }
    }

    // =====================================================================
    //  THEME  (light & classy palette + reusable styling helpers)
    // =====================================================================
    static class Theme {
        // --- neutrals (light theme) ---
        static final Color BG           = new Color(245, 247, 250);
        static final Color CARD         = Color.WHITE;
        static final Color BORDER       = new Color(219, 225, 234);
        static final Color TEXT         = new Color(30, 41, 59);
        static final Color MUTED        = new Color(100, 116, 139);
        static final Color ROW_ZEBRA    = new Color(249, 250, 252);
        static final Color ROW_SELECTED = new Color(219, 232, 252);

        // --- brand / action colours ---
        static final Color NAVY   = new Color(30, 58, 110);
        static final Color BLUE   = new Color(59, 100, 190);
        static final Color SLATE  = new Color(100, 116, 139);
        static final Color GREEN  = new Color(30, 142, 96);
        static final Color ORANGE = new Color(217, 119, 6);

        // --- RED ALERT palette (used for every low-stock warning) ---
        static final Color RED        = new Color(200, 30, 30);
        static final Color RED_DARK   = new Color(127, 17, 17);
        static final Color RED_BG     = new Color(254, 226, 226);
        static final Color RED_SELECT = new Color(252, 190, 190);

        // --- fonts ---
        static final Font FONT       = new Font("Segoe UI", Font.PLAIN, 13);
        static final Font FONT_BOLD  = new Font("Segoe UI", Font.BOLD, 13);
        static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 12);
        static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 22);

        /** Sets global Swing defaults so dialogs and plain components match the theme. */
        static void install() {
            UIManager.put("Panel.background", BG);
            UIManager.put("OptionPane.background", BG);
            UIManager.put("OptionPane.messageForeground", TEXT);
            UIManager.put("Label.foreground", TEXT);
            UIManager.put("Label.font", FONT);
            UIManager.put("Button.font", FONT);
            UIManager.put("TextField.font", FONT);
            UIManager.put("PasswordField.font", FONT);
            UIManager.put("ComboBox.font", FONT);
            UIManager.put("OptionPane.messageFont", FONT);
            UIManager.put("OptionPane.buttonFont", FONT);
        }

        static Color blend(Color a, Color b, float t) {
            return new Color(
                    Math.round(a.getRed()   * (1 - t) + b.getRed()   * t),
                    Math.round(a.getGreen() * (1 - t) + b.getGreen() * t),
                    Math.round(a.getBlue()  * (1 - t) + b.getBlue()  * t));
        }

        static void styleField(JTextField f) {
            f.setFont(FONT);
            f.setForeground(TEXT);
            f.setBackground(Color.WHITE);
            f.setCaretColor(NAVY);
            f.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER),
                    BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        }

        static void styleCombo(JComboBox<?> c) {
            c.setFont(FONT);
            c.setBackground(Color.WHITE);
            c.setForeground(TEXT);
        }

        /** Light table look: navy header, thin row lines, roomy rows. */
        static void styleTable(JTable t) {
            t.setFont(FONT);
            t.setRowHeight(28);
            t.setShowGrid(false);
            t.setShowHorizontalLines(true);
            t.setGridColor(new Color(235, 239, 245));
            t.setIntercellSpacing(new Dimension(0, 1));
            t.setSelectionBackground(ROW_SELECTED);
            t.setSelectionForeground(TEXT);
            t.setFillsViewportHeight(true);
            t.setBackground(Color.WHITE);

            JTableHeader h = t.getTableHeader();
            h.setReorderingAllowed(false);
            h.setPreferredSize(new Dimension(0, 34));
            h.setDefaultRenderer(new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected,
                                                               boolean hasFocus, int row, int column) {
                    JLabel l = (JLabel) super.getTableCellRendererComponent(tbl, value, false, false, row, column);
                    l.setOpaque(true);
                    l.setBackground(NAVY);
                    l.setForeground(Color.WHITE);
                    l.setFont(FONT_BOLD);
                    l.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                    l.setHorizontalAlignment(LEFT);
                    return l;
                }
            });
        }

        static JScrollPane styledScroll(JComponent view) {
            JScrollPane sp = new JScrollPane(view);
            sp.setBorder(BorderFactory.createLineBorder(BORDER));
            sp.getViewport().setBackground(Color.WHITE);
            return sp;
        }
    }

    /** Flat rounded button that keeps its colour on every look-and-feel (Windows, Mac, Linux). */
    static class RoundedButton extends JButton {
        private final Color base;
        private boolean hovering;

        RoundedButton(String text, Color base, Color foreground) {
            super(text);
            this.base = base;
            setForeground(foreground);
            setFont(Theme.FONT_BOLD);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hovering = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hovering = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean light = base.getRed() + base.getGreen() + base.getBlue() > 660;
            Color fill = base;
            if (getModel().isPressed()) {
                fill = Theme.blend(base, Color.BLACK, 0.18f);
            } else if (hovering) {
                fill = Theme.blend(base, light ? new Color(225, 225, 225) : Color.WHITE, 0.18f);
            }
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // =====================================================================
    //  UI: LOGIN
    // =====================================================================
    static class LoginFrame extends JFrame {
        private final UserDAO userDAO = new UserDAO();

        LoginFrame() {
            setTitle("Smart Inventory Management System - Login");
            setSize(420, 380);
            setLocationRelativeTo(null);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setResizable(false);

            JPanel panel = new JPanel(null);
            panel.setBackground(Theme.BG);

            // ---- navy header band ----
            JPanel band = new JPanel(null);
            band.setBackground(Theme.NAVY);
            band.setBounds(0, 0, 420, 96);
            JLabel title = new JLabel("Smart Inventory", SwingConstants.CENTER);
            title.setFont(Theme.FONT_TITLE);
            title.setForeground(Color.WHITE);
            title.setBounds(0, 20, 420, 32);
            band.add(title);
            JLabel subtitle = new JLabel("Management System", SwingConstants.CENTER);
            subtitle.setFont(Theme.FONT_SMALL);
            subtitle.setForeground(new Color(196, 210, 238));
            subtitle.setBounds(0, 54, 420, 20);
            band.add(subtitle);
            panel.add(band);

            // ---- white login card ----
            JPanel card = new JPanel(null);
            card.setBackground(Theme.CARD);
            card.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
            card.setBounds(30, 118, 344, 178);
            panel.add(card);

            JLabel lblUser = new JLabel("Username");
            lblUser.setFont(Theme.FONT_BOLD);
            lblUser.setForeground(Theme.MUTED);
            lblUser.setBounds(20, 16, 200, 20);
            card.add(lblUser);

            JTextField txtUsername = new JTextField();
            Theme.styleField(txtUsername);
            txtUsername.setBounds(20, 38, 304, 30);
            card.add(txtUsername);

            JLabel lblPass = new JLabel("Password");
            lblPass.setFont(Theme.FONT_BOLD);
            lblPass.setForeground(Theme.MUTED);
            lblPass.setBounds(20, 76, 200, 20);
            card.add(lblPass);

            JPasswordField txtPassword = new JPasswordField();
            Theme.styleField(txtPassword);
            txtPassword.setBounds(20, 98, 304, 30);
            card.add(txtPassword);

            JButton btnLogin = new RoundedButton("Login", Theme.NAVY, Color.WHITE);
            btnLogin.setBounds(20, 138, 304, 34);
            card.add(btnLogin);

            JLabel hint = new JLabel("Default: admin / admin123", SwingConstants.CENTER);
            hint.setForeground(Theme.MUTED);
            hint.setFont(new Font("Segoe UI", Font.ITALIC, 11));
            hint.setBounds(30, 304, 344, 20);
            panel.add(hint);

            add(panel);
            getRootPane().setDefaultButton(btnLogin);

            Runnable attemptLogin = () -> {
                String username = txtUsername.getText().trim();
                String password = new String(txtPassword.getPassword());
                if (username.isEmpty() || password.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Please enter both username and password.",
                            "Missing Fields", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (userDAO.validateLogin(username, password)) {
                    dispose();
                    SwingUtilities.invokeLater(() -> new MainDashboard(username).setVisible(true));
                } else {
                    JOptionPane.showMessageDialog(this, "Invalid username or password.",
                            "Login Failed", JOptionPane.ERROR_MESSAGE);
                }
            };

            btnLogin.addActionListener(e -> attemptLogin.run());
            txtPassword.addActionListener(e -> attemptLogin.run());
        }
    }

    // =====================================================================
    //  UI: MAIN DASHBOARD
    // =====================================================================
    static class MainDashboard extends JFrame {
        private static final int FORM_W = 258;

        private final ProductDAO productDAO = new ProductDAO();
        private final CategoryDAO categoryDAO = new CategoryDAO();

        private JTable table;
        private DefaultTableModel tableModel;
        private JTextField txtCode, txtName, txtQty, txtReorder, txtPrice, txtSupplier, txtSearch;
        private JComboBox<Category> comboCategory;
        private JLabel lblStatus;
        private JLabel lblTotalProducts, lblTotalValue, lblLowStockSummary, lblLowTitle;
        private JPanel lowStockCard, alertWrap, alertBanner;
        private JLabel lblAlertText;
        private javax.swing.Timer blinkTimer;
        private boolean blinkPhase;
        private int selectedProductId = -1;
        private final String loggedInUser;

        MainDashboard(String loggedInUser) {
            this.loggedInUser = loggedInUser;
            setTitle("Smart Inventory Management System  |  Logged in as: " + loggedInUser);
            setSize(1100, 700);
            setLocationRelativeTo(null);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            initComponents();
            loadCategories();
            refreshTable();
            refreshStatus();
        }

        private void initComponents() {
            JPanel content = new JPanel(new BorderLayout());
            content.setBackground(Theme.BG);
            content.add(buildHeader(), BorderLayout.NORTH);

            JPanel root = new JPanel(new BorderLayout(12, 12));
            root.setBackground(Theme.BG);
            root.setBorder(BorderFactory.createEmptyBorder(12, 14, 8, 14));
            content.add(root, BorderLayout.CENTER);

            // ---- RED ALERT banner (hidden until something is low on stock) ----
            alertBanner = new JPanel(new BorderLayout(10, 0));
            alertBanner.setBackground(Theme.RED);
            alertBanner.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 10));
            lblAlertText = new JLabel();
            lblAlertText.setFont(new Font("Segoe UI", Font.BOLD, 14));
            lblAlertText.setForeground(Color.WHITE);
            JButton btnViewAlert = new RoundedButton("View items", Color.WHITE, Theme.RED);
            alertBanner.add(lblAlertText, BorderLayout.CENTER);
            alertBanner.add(btnViewAlert, BorderLayout.EAST);

            alertWrap = new JPanel(new BorderLayout());
            alertWrap.setOpaque(false);
            alertWrap.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
            alertWrap.add(alertBanner, BorderLayout.CENTER);
            alertWrap.setVisible(false);

            blinkTimer = new javax.swing.Timer(700, e -> {
                blinkPhase = !blinkPhase;
                alertBanner.setBackground(blinkPhase ? Theme.RED_DARK : Theme.RED);
            });

            // ---- summary dashboard strip ----
            JPanel summaryPanel = new JPanel(new GridLayout(1, 3, 12, 0));
            summaryPanel.setOpaque(false);
            summaryPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

            lblTotalProducts = new JLabel("-");
            lblTotalValue = new JLabel("-");
            lblLowStockSummary = new JLabel("-");
            summaryPanel.add(createSummaryCard("Total Products", Theme.BLUE, lblTotalProducts));
            summaryPanel.add(createSummaryCard("Total Stock Value", Theme.GREEN, lblTotalValue));
            lowStockCard = createSummaryCard("Low Stock Items", Theme.GREEN, lblLowStockSummary);
            lblLowTitle = (JLabel) lowStockCard.getClientProperty("title");
            summaryPanel.add(lowStockCard);

            // ---- top search bar ----
            JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            topPanel.setOpaque(false);
            txtSearch = new JTextField(20);
            Theme.styleField(txtSearch);
            JButton btnSearch = new RoundedButton("Search", Theme.NAVY, Color.WHITE);
            JButton btnShowAll = new RoundedButton("Show All", Theme.SLATE, Color.WHITE);
            JButton btnLowStock = new RoundedButton("\u26A0 Low Stock Alert", Theme.RED, Color.WHITE);
            JButton btnHistory = new RoundedButton("Transaction History", Theme.BLUE, Color.WHITE);
            JLabel searchLbl = new JLabel("Search (name/code):");
            searchLbl.setForeground(Theme.MUTED);
            topPanel.add(searchLbl);
            topPanel.add(txtSearch);
            topPanel.add(btnSearch);
            topPanel.add(btnShowAll);
            topPanel.add(btnLowStock);
            topPanel.add(btnHistory);

            JPanel northPanel = new JPanel();
            northPanel.setLayout(new BoxLayout(northPanel, BoxLayout.Y_AXIS));
            northPanel.setOpaque(false);
            for (JComponent c : new JComponent[]{alertWrap, summaryPanel, topPanel}) {
                c.setAlignmentX(Component.LEFT_ALIGNMENT);
                northPanel.add(c);
            }
            root.add(northPanel, BorderLayout.NORTH);

            // ---- table ----
            String[] columns = {"ID", "Code", "Name", "Category", "Qty", "Reorder Lvl", "Unit Price", "Supplier"};
            tableModel = new DefaultTableModel(columns, 0) {
                @Override public boolean isCellEditable(int row, int column) { return false; }
            };
            table = new JTable(tableModel);
            Theme.styleTable(table);
            table.getSelectionModel().addListSelectionListener(e -> populateFormFromSelection());
            table.setDefaultRenderer(Object.class, new LowStockRowRenderer());
            table.getColumnModel().getColumn(0).setMaxWidth(60);
            root.add(Theme.styledScroll(table), BorderLayout.CENTER);

            // ---- form ----
            JPanel formPanel = new JPanel();
            formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
            formPanel.setBackground(Theme.CARD);
            javax.swing.border.TitledBorder tb = BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(Theme.BORDER), " Product Details ");
            tb.setTitleFont(Theme.FONT_BOLD);
            tb.setTitleColor(Theme.NAVY);
            formPanel.setBorder(BorderFactory.createCompoundBorder(tb, BorderFactory.createEmptyBorder(6, 10, 10, 10)));
            formPanel.setPreferredSize(new Dimension(300, 0));

            txtCode = addFormField(formPanel, "Product Code:");
            txtName = addFormField(formPanel, "Name:");

            formPanel.add(formLabel("Category:"));
            comboCategory = new JComboBox<>();
            Theme.styleCombo(comboCategory);
            comboCategory.setMaximumSize(new Dimension(FORM_W, 30));
            comboCategory.setAlignmentX(Component.LEFT_ALIGNMENT);
            formPanel.add(comboCategory);
            formPanel.add(Box.createVerticalStrut(8));

            txtQty = addFormField(formPanel, "Quantity:");
            txtReorder = addFormField(formPanel, "Reorder Level:");
            txtPrice = addFormField(formPanel, "Unit Price:");
            txtSupplier = addFormField(formPanel, "Supplier:");

            formPanel.add(Box.createVerticalStrut(6));
            JButton btnAdd = new RoundedButton("Add Product", Theme.GREEN, Color.WHITE);
            JButton btnUpdate = new RoundedButton("Update Selected", Theme.NAVY, Color.WHITE);
            JButton btnDelete = new RoundedButton("Delete Selected", Theme.RED, Color.WHITE);
            JButton btnClear = new RoundedButton("Clear Form", Theme.SLATE, Color.WHITE);
            for (JButton b : new JButton[]{btnAdd, btnUpdate, btnDelete, btnClear}) {
                b.setAlignmentX(Component.LEFT_ALIGNMENT);
                b.setMaximumSize(new Dimension(FORM_W, 34));
                formPanel.add(Box.createVerticalStrut(6));
                formPanel.add(b);
            }

            formPanel.add(Box.createVerticalStrut(14));
            JSeparator sep = new JSeparator();
            sep.setForeground(Theme.BORDER);
            sep.setMaximumSize(new Dimension(FORM_W, 2));
            sep.setAlignmentX(Component.LEFT_ALIGNMENT);
            formPanel.add(sep);
            formPanel.add(Box.createVerticalStrut(10));

            formPanel.add(formLabel("Stock In / Out (selected item):"));
            formPanel.add(Box.createVerticalStrut(2));

            JTextField txtStockAmount = new JTextField();
            Theme.styleField(txtStockAmount);
            txtStockAmount.setMaximumSize(new Dimension(FORM_W, 30));
            txtStockAmount.setAlignmentX(Component.LEFT_ALIGNMENT);
            formPanel.add(txtStockAmount);
            formPanel.add(Box.createVerticalStrut(8));

            JPanel stockBtnPanel = new JPanel(new GridLayout(1, 2, 8, 0));
            stockBtnPanel.setOpaque(false);
            JButton btnStockIn = new RoundedButton("Stock IN", Theme.GREEN, Color.WHITE);
            JButton btnStockOut = new RoundedButton("Stock OUT", Theme.ORANGE, Color.WHITE);
            stockBtnPanel.add(btnStockIn);
            stockBtnPanel.add(btnStockOut);
            stockBtnPanel.setMaximumSize(new Dimension(FORM_W, 34));
            stockBtnPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            formPanel.add(stockBtnPanel);
            formPanel.add(Box.createVerticalGlue());

            root.add(formPanel, BorderLayout.EAST);

            // ---- status bar (message on the left, colour legend on the right) ----
            JPanel statusBar = new JPanel(new BorderLayout());
            statusBar.setBackground(Color.WHITE);
            statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER));
            lblStatus = new JLabel("Ready.");
            lblStatus.setForeground(Theme.MUTED);
            lblStatus.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 10));
            JLabel legend = new JLabel("<html><span style='color:#FCA5A5'>\u25A0</span> Low stock &nbsp;&nbsp;"
                    + "<span style='color:#C81E1E'>\u25A0</span> Out of stock</html>");
            legend.setFont(Theme.FONT_SMALL);
            legend.setForeground(Theme.MUTED);
            legend.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 14));
            statusBar.add(lblStatus, BorderLayout.CENTER);
            statusBar.add(legend, BorderLayout.EAST);
            content.add(statusBar, BorderLayout.SOUTH);

            setContentPane(content);

            txtSearch.addActionListener(e -> searchProducts());
            btnSearch.addActionListener(e -> searchProducts());
            btnShowAll.addActionListener(e -> { txtSearch.setText(""); refreshTable(); });
            btnLowStock.addActionListener(e -> showLowStock());
            btnViewAlert.addActionListener(e -> showLowStock());
            btnAdd.addActionListener(e -> addProduct());
            btnUpdate.addActionListener(e -> updateProduct());
            btnDelete.addActionListener(e -> deleteProduct());
            btnClear.addActionListener(e -> clearForm());
            btnStockIn.addActionListener(e -> adjustStock(txtStockAmount, "IN"));
            btnStockOut.addActionListener(e -> adjustStock(txtStockAmount, "OUT"));
            btnHistory.addActionListener(e -> showTransactionHistory());
        }

        private JPanel buildHeader() {
            JPanel header = new JPanel(new BorderLayout());
            header.setBackground(Theme.NAVY);
            header.setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 18));
            JLabel appName = new JLabel("Smart Inventory Management System");
            appName.setFont(new Font("Segoe UI", Font.BOLD, 18));
            appName.setForeground(Color.WHITE);
            JLabel user = new JLabel("Logged in as: " + loggedInUser);
            user.setFont(Theme.FONT_SMALL);
            user.setForeground(new Color(196, 210, 238));
            header.add(appName, BorderLayout.WEST);
            header.add(user, BorderLayout.EAST);
            return header;
        }

        /** Builds one summary card (title + big value). The title label is stored in the card's "title" client property. */
        private JPanel createSummaryCard(String title, Color accent, JLabel valueLbl) {
            JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            styleCard(card, accent, Theme.CARD, 1, Theme.BORDER);

            JLabel titleLbl = new JLabel(title);
            titleLbl.setFont(Theme.FONT_BOLD);
            titleLbl.setForeground(Theme.MUTED);
            titleLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

            valueLbl.setFont(new Font("Segoe UI", Font.BOLD, 24));
            valueLbl.setForeground(accent);
            valueLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

            card.add(titleLbl);
            card.add(Box.createVerticalStrut(2));
            card.add(valueLbl);
            card.putClientProperty("title", titleLbl);
            return card;
        }

        private void styleCard(JPanel card, Color accent, Color bg, int lineThickness, Color line) {
            card.setBackground(bg);
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(line, lineThickness),
                    BorderFactory.createCompoundBorder(
                            BorderFactory.createMatteBorder(0, 5, 0, 0, accent),
                            BorderFactory.createEmptyBorder(10, 12, 10, 12))));
        }

        /**
         * Row colouring for stock alerts:
         *  - out of stock (qty 0)        : solid RED row, white bold text, "OUT" badge
         *  - at/below reorder level      : light RED row, dark-red bold text, "LOW" badge
         *  - healthy                     : white / soft zebra rows
         * (column 4 = Qty, column 5 = Reorder Lvl)
         */
        private class LowStockRowRenderer extends DefaultTableCellRenderer {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(tbl, value, false, false, row, column);
                int qty = 1, reorder = 0;
                try {
                    qty = Integer.parseInt(tbl.getValueAt(row, 4).toString());
                    reorder = Integer.parseInt(tbl.getValueAt(row, 5).toString());
                } catch (Exception ignored) { }
                boolean out = qty <= 0;
                boolean low = out || qty <= reorder;

                l.setOpaque(true);
                l.setFont(Theme.FONT);
                l.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));

                Color bg, fg;
                if (out) {
                    bg = isSelected ? Theme.RED_DARK : Theme.RED;
                    fg = Color.WHITE;
                    l.setFont(Theme.FONT_BOLD);
                } else if (low) {
                    bg = isSelected ? Theme.RED_SELECT : Theme.RED_BG;
                    fg = Theme.RED_DARK;
                    if (column == 2 || column == 4) l.setFont(Theme.FONT_BOLD);
                } else {
                    bg = isSelected ? Theme.ROW_SELECTED : (row % 2 == 0 ? Color.WHITE : Theme.ROW_ZEBRA);
                    fg = Theme.TEXT;
                }
                l.setBackground(bg);
                l.setForeground(fg);

                if (column == 4 && low) l.setText(value + "   " + (out ? "OUT" : "LOW"));

                if (column == 0 || column == 4 || column == 5) l.setHorizontalAlignment(CENTER);
                else if (column == 6) l.setHorizontalAlignment(RIGHT);
                else l.setHorizontalAlignment(LEFT);
                return l;
            }
        }

        private void showTransactionHistory() {
            List<TransactionRecord> history = productDAO.getTransactionHistory();

            String[] columns = {"Date/Time", "Product Code", "Product Name", "Type", "Qty", "Remarks"};
            DefaultTableModel historyModel = new DefaultTableModel(columns, 0) {
                @Override public boolean isCellEditable(int row, int column) { return false; }
            };
            for (TransactionRecord t : history) {
                historyModel.addRow(new Object[]{
                        t.transactionDate, t.productCode, t.productName, t.type, t.quantity,
                        t.remarks == null ? "" : t.remarks
                });
            }

            JTable historyTable = new JTable(historyModel);
            Theme.styleTable(historyTable);
            historyTable.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected,
                                                               boolean hasFocus, int row, int column) {
                    JLabel l = (JLabel) super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, column);
                    l.setHorizontalAlignment(CENTER);
                    l.setFont(Theme.FONT_BOLD);
                    if (!isSelected) l.setForeground("IN".equals(String.valueOf(value)) ? Theme.GREEN : Theme.RED);
                    return l;
                }
            });
            JScrollPane scrollPane = Theme.styledScroll(historyTable);
            scrollPane.setPreferredSize(new Dimension(760, 400));

            JOptionPane.showMessageDialog(this, scrollPane,
                    "Transaction History (" + history.size() + " record" + (history.size() == 1 ? "" : "s") + ")",
                    JOptionPane.PLAIN_MESSAGE);
        }

        private JLabel formLabel(String text) {
            JLabel lbl = new JLabel(text);
            lbl.setFont(Theme.FONT_SMALL);
            lbl.setForeground(Theme.MUTED);
            lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
            lbl.setBorder(BorderFactory.createEmptyBorder(0, 0, 3, 0));
            return lbl;
        }

        private JTextField addFormField(JPanel parent, String label) {
            parent.add(formLabel(label));
            JTextField field = new JTextField();
            Theme.styleField(field);
            field.setMaximumSize(new Dimension(FORM_W, 30));
            field.setAlignmentX(Component.LEFT_ALIGNMENT);
            parent.add(field);
            parent.add(Box.createVerticalStrut(8));
            return field;
        }

        private void loadCategories() {
            comboCategory.removeAllItems();
            for (Category c : categoryDAO.getAllCategories()) comboCategory.addItem(c);
        }

        private void refreshTable() { loadIntoTable(productDAO.getAllProducts()); }

        private void searchProducts() {
            String keyword = txtSearch.getText().trim();
            if (keyword.isEmpty()) { refreshTable(); return; }
            loadIntoTable(productDAO.searchProducts(keyword));
        }

        private void showLowStock() {
            List<Product> lowStock = productDAO.getLowStockProducts();
            loadIntoTable(lowStock);
            if (lowStock.isEmpty()) setStatus("No low-stock items. Everything is well stocked.");
            else setAlertStatus("\u26A0 " + lowStock.size() + " item(s) at or below their reorder level.");
        }

        private void loadIntoTable(List<Product> products) {
            tableModel.setRowCount(0);
            for (Product p : products) {
                tableModel.addRow(new Object[]{
                        p.id, p.productCode, p.name,
                        p.categoryName == null ? "-" : p.categoryName,
                        p.quantity, p.reorderLevel, p.unitPrice, p.supplier
                });
            }
        }

        private void populateFormFromSelection() {
            int row = table.getSelectedRow();
            if (row < 0) return;
            selectedProductId = (int) tableModel.getValueAt(row, 0);
            txtCode.setText(String.valueOf(tableModel.getValueAt(row, 1)));
            txtName.setText(String.valueOf(tableModel.getValueAt(row, 2)));

            String categoryName = String.valueOf(tableModel.getValueAt(row, 3));
            for (int i = 0; i < comboCategory.getItemCount(); i++) {
                if (comboCategory.getItemAt(i).name.equals(categoryName)) {
                    comboCategory.setSelectedIndex(i);
                    break;
                }
            }
            txtQty.setText(String.valueOf(tableModel.getValueAt(row, 4)));
            txtReorder.setText(String.valueOf(tableModel.getValueAt(row, 5)));
            txtPrice.setText(String.valueOf(tableModel.getValueAt(row, 6)));
            txtSupplier.setText(String.valueOf(tableModel.getValueAt(row, 7)));
        }

        private Product buildProductFromForm() {
            Product p = new Product();
            p.id = selectedProductId;
            p.productCode = txtCode.getText().trim();
            p.name = txtName.getText().trim();
            Category selected = (Category) comboCategory.getSelectedItem();
            p.categoryId = selected == null ? 0 : selected.id;
            p.quantity = Integer.parseInt(txtQty.getText().trim());
            p.reorderLevel = Integer.parseInt(txtReorder.getText().trim());
            p.unitPrice = new BigDecimal(txtPrice.getText().trim());
            p.supplier = txtSupplier.getText().trim();
            return p;
        }

        private void addProduct() {
            try {
                if (txtCode.getText().trim().isEmpty() || txtName.getText().trim().isEmpty()) {
                    setStatus("Product code and name are required.");
                    return;
                }
                boolean ok = productDAO.addProduct(buildProductFromForm());
                setStatus(ok ? "Product added successfully." : "Failed to add product (duplicate code?).");
                if (ok) { refreshTable(); clearForm(); refreshStatus(); }
            } catch (NumberFormatException ex) {
                setStatus("Quantity, reorder level, and price must be valid numbers.");
            }
        }

        private void updateProduct() {
            if (selectedProductId < 0) { setStatus("Select a product in the table first."); return; }
            try {
                boolean ok = productDAO.updateProduct(buildProductFromForm());
                setStatus(ok ? "Product updated." : "Update failed.");
                if (ok) { refreshTable(); refreshStatus(); }
            } catch (NumberFormatException ex) {
                setStatus("Quantity, reorder level, and price must be valid numbers.");
            }
        }

        private void deleteProduct() {
            if (selectedProductId < 0) { setStatus("Select a product in the table first."); return; }
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Delete the selected product? This cannot be undone.",
                    "Confirm Delete", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;
            boolean ok = productDAO.deleteProduct(selectedProductId);
            setStatus(ok ? "Product deleted." : "Delete failed.");
            if (ok) { refreshTable(); clearForm(); refreshStatus(); }
        }

        private void adjustStock(JTextField txtStockAmount, String type) {
            if (selectedProductId < 0) { setStatus("Select a product first."); return; }
            try {
                int amount = Integer.parseInt(txtStockAmount.getText().trim());
                if (amount <= 0) { setStatus("Enter a positive quantity."); return; }
                boolean ok = productDAO.adjustStock(selectedProductId, amount, type,
                        type.equals("IN") ? "Manual stock-in" : "Manual stock-out");
                if (!ok) {
                    setStatus("Stock " + type + " failed" + (type.equals("OUT") ? " (insufficient quantity?)." : "."));
                    return;
                }
                setStatus("Stock " + type + " recorded.");
                refreshTable();
                refreshStatus();
                txtStockAmount.setText("");

                // RED ALERT popup if this stock-out pushed the item to/below its reorder level
                if (type.equals("OUT")) {
                    Product p = productDAO.getProductById(selectedProductId);
                    if (p != null && p.isLowStock()) showLowStockPopup(p);
                }
            } catch (NumberFormatException ex) {
                setStatus("Enter a valid whole number for stock adjustment.");
            }
        }

        private void showLowStockPopup(Product p) {
            boolean out = p.quantity <= 0;
            JPanel panel = new JPanel(new BorderLayout(0, 8));
            panel.setBackground(Theme.RED_BG);
            panel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.RED, 2),
                    BorderFactory.createEmptyBorder(14, 18, 14, 18)));

            JLabel head = new JLabel(out ? "\u26A0  OUT OF STOCK" : "\u26A0  LOW STOCK ALERT");
            head.setFont(new Font("Segoe UI", Font.BOLD, 17));
            head.setForeground(Theme.RED);

            String name = p.name.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            JLabel body = new JLabel("<html><b>" + name + "</b> (" + p.productCode + ")<br>"
                    + "Current stock: <b>" + p.quantity + "</b> &nbsp;|&nbsp; Reorder level: <b>" + p.reorderLevel + "</b><br>"
                    + "Please restock this item soon.</html>");
            body.setFont(Theme.FONT);
            body.setForeground(Theme.RED_DARK);

            panel.add(head, BorderLayout.NORTH);
            panel.add(body, BorderLayout.CENTER);
            JOptionPane.showMessageDialog(this, panel, "Stock Alert", JOptionPane.PLAIN_MESSAGE);
        }

        private void clearForm() {
            selectedProductId = -1;
            txtCode.setText(""); txtName.setText(""); txtQty.setText("");
            txtReorder.setText(""); txtPrice.setText(""); txtSupplier.setText("");
            table.clearSelection();
        }

        private void refreshStatus() {
            int total = productDAO.getTotalProductCount();
            int low = productDAO.getLowStockCount();
            BigDecimal totalValue = productDAO.getTotalStockValue();

            setTitle("Smart Inventory Management System  |  Products: " + total + "  |  Low Stock: " + low);

            lblTotalProducts.setText(String.valueOf(total));
            lblTotalValue.setText("Rs. " + totalValue.setScale(2, java.math.RoundingMode.HALF_UP));
            lblLowStockSummary.setText(String.valueOf(low));
            updateAlertState(low);
        }

        /** Turns the red alert banner, blinking and red summary card on/off depending on the low-stock count. */
        private void updateAlertState(int low) {
            if (low > 0) {
                styleCard(lowStockCard, Theme.RED, Theme.RED_BG, 2, Theme.RED);
                lblLowTitle.setText("\u26A0 Low Stock Items");
                lblLowTitle.setForeground(Theme.RED_DARK);
                lblLowStockSummary.setForeground(Theme.RED);
                lblAlertText.setText("\u26A0  LOW STOCK ALERT \u2014 " + low + " item" + (low == 1 ? "" : "s")
                        + " at or below reorder level. Restock needed!");
                alertWrap.setVisible(true);
                if (!blinkTimer.isRunning()) blinkTimer.start();
            } else {
                styleCard(lowStockCard, Theme.GREEN, Theme.CARD, 1, Theme.BORDER);
                lblLowTitle.setText("Low Stock Items");
                lblLowTitle.setForeground(Theme.MUTED);
                lblLowStockSummary.setForeground(Theme.GREEN);
                alertWrap.setVisible(false);
                blinkTimer.stop();
                alertBanner.setBackground(Theme.RED);
            }
            getContentPane().revalidate();
            getContentPane().repaint();
        }

        private void setStatus(String msg) {
            lblStatus.setForeground(Theme.MUTED);
            lblStatus.setFont(Theme.FONT);
            lblStatus.setText(msg);
        }

        private void setAlertStatus(String msg) {
            lblStatus.setForeground(Theme.RED);
            lblStatus.setFont(Theme.FONT_BOLD);
            lblStatus.setText(msg);
        }
    }
}
