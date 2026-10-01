import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * InventoryWebApp
 * ---------------------------------------------------------------
 * A full web version of the Smart Inventory Management System -
 * same features as the Swing app (login, add/update/delete products,
 * search, stock IN/OUT, low stock alert, transaction history,
 * summary dashboard), running in a browser instead of a desktop
 * window. Same MySQL database as the Swing app - run both side by
 * side if you like, they share the same data.
 *
 * No Node.js / PHP / frameworks needed - plain Java only.
 *
 * SETUP
 * ---------------------------------------------------------------
 * 1) Update DB_URL / DB_USER / DB_PASSWORD below to match your
 *    SmartInventoryManagementSystem.java settings.
 *
 * 2) Compile:
 *      javac -cp mysql-connector-j-8.0.31.jar InventoryWebApp.java
 *
 * 3) Run:
 *      java -cp ".;mysql-connector-j-8.0.31.jar" InventoryWebApp
 *
 * 4) Open a browser and go to:
 *      http://localhost:8080
 *
 *    Log in with admin / admin123 (same as the Swing app).
 *
 * 5) To stop the server, close the terminal or press Ctrl+C.
 *
 * NOTE: this is a lightweight demo server (no HTTPS, no sessions,
 * no password hashing) - fine for a local college project demo,
 * not meant for deployment on the internet.
 * ---------------------------------------------------------------
 */
public class InventoryWebApp {

    private static final String DB_URL =
            "jdbc:mysql://localhost:3306/inventory_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "mysql123"; // <-- match your main app

    private static final int PORT = 8080;

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        server.createContext("/", InventoryWebApp::servePage);
        server.createContext("/api/login", InventoryWebApp::handleLogin);
        server.createContext("/api/categories", InventoryWebApp::handleCategories);
        server.createContext("/api/summary", InventoryWebApp::handleSummary);
        server.createContext("/api/products", InventoryWebApp::handleProducts);
        server.createContext("/api/products/add", InventoryWebApp::handleAddProduct);
        server.createContext("/api/products/update", InventoryWebApp::handleUpdateProduct);
        server.createContext("/api/products/delete", InventoryWebApp::handleDeleteProduct);
        server.createContext("/api/stock", InventoryWebApp::handleStockAdjust);
        server.createContext("/api/transactions", InventoryWebApp::handleTransactions);
        server.createContext("/api/reports/daily", InventoryWebApp::handleDailyReport);

        server.setExecutor(null);
        server.start();

        System.out.println("=================================================");
        System.out.println(" Inventory web app is running.");
        System.out.println(" Open this in your browser:  http://localhost:" + PORT);
        System.out.println(" Press Ctrl+C to stop.");
        System.out.println("=================================================");
    }

    // =====================================================================
    //  DB helper
    // =====================================================================
    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    // =====================================================================
    //  Request helpers
    // =====================================================================
    private static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null) return map;
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx < 0) continue;
            try {
                String key = URLDecoder.decode(pair.substring(0, idx), "UTF-8");
                String value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                map.put(key, value);
            } catch (Exception ignored) {
            }
        }
        return map;
    }

    private static Map<String, String> readFormBody(HttpExchange exchange) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InputStream is = exchange.getRequestBody();
        byte[] buf = new byte[1024];
        int n;
        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
        String body = baos.toString("UTF-8");
        return parseQuery(body);
    }

    private static void sendJson(HttpExchange exchange, String json) throws IOException {
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private static void sendHtml(HttpExchange exchange, String html) throws IOException {
        byte[] response = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String nz(String s) {
        return s == null ? "-" : s;
    }

    // =====================================================================
    //  Handlers
    // =====================================================================
    private static void servePage(HttpExchange exchange) throws IOException {
        sendHtml(exchange, PAGE_HTML);
    }

    private static void handleLogin(HttpExchange exchange) throws IOException {
        Map<String, String> form = readFormBody(exchange);
        String username = form.getOrDefault("username", "");
        String password = form.getOrDefault("password", "");

        String sql = "SELECT role FROM users WHERE username = ? AND password = ?";
        boolean ok = false;
        String role = "";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);
            try (ResultSet rs = ps.executeQuery()) {
                ok = rs.next();
                if (ok) role = rs.getString("role");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            sendJson(exchange, "{\"success\":false,\"error\":\"" + escape(e.getMessage()) + "\"}");
            return;
        }
        sendJson(exchange, "{\"success\":" + ok + ",\"role\":\"" + escape(role) + "\"}");
    }

    /** Re-checks a username's role directly from the database - never trusts a role claimed by the client. */
    private static boolean isAdmin(String username) {
        String sql = "SELECT role FROM users WHERE username = ?";
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && "ADMIN".equalsIgnoreCase(rs.getString("role"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /** True for any real, logged-in account (ADMIN or STAFF) - used for actions both roles may now perform. */
    private static boolean isValidUser(String username) {
        if (username == null || username.isEmpty()) return false;
        String sql = "SELECT 1 FROM users WHERE username = ?";
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void sendForbidden(HttpExchange exchange) throws IOException {
        sendJson(exchange, "{\"success\":false,\"error\":\"Admin access required for this action.\"}");
    }

    private static void handleCategories(HttpExchange exchange) throws IOException {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        try (Connection con = getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM categories ORDER BY name")) {
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;
                json.append("{\"id\":").append(rs.getInt("id"))
                    .append(",\"name\":\"").append(escape(rs.getString("name"))).append("\"}");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        json.append("]");
        sendJson(exchange, json.toString());
    }

    private static void handleSummary(HttpExchange exchange) throws IOException {
        int count = 0, low = 0;
        java.math.BigDecimal value = java.math.BigDecimal.ZERO;
        try (Connection con = getConnection(); Statement st = con.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) c FROM products")) {
                if (rs.next()) count = rs.getInt("c");
            }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) c FROM products WHERE quantity <= reorder_level")) {
                if (rs.next()) low = rs.getInt("c");
            }
            try (ResultSet rs = st.executeQuery("SELECT COALESCE(SUM(quantity*unit_price),0) v FROM products")) {
                if (rs.next()) value = rs.getBigDecimal("v");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        sendJson(exchange, "{\"count\":" + count + ",\"low\":" + low + ",\"value\":" + value + "}");
    }

    private static void handleProducts(HttpExchange exchange) throws IOException {
        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        String search = params.get("search");
        boolean lowOnly = "true".equals(params.get("lowstock"));

        StringBuilder sql = new StringBuilder(
                "SELECT p.id, p.product_code, p.name, p.category_id, c.name AS category_name, " +
                "p.quantity, p.reorder_level, p.unit_price, p.supplier " +
                "FROM products p LEFT JOIN categories c ON p.category_id = c.id WHERE 1=1");
        if (search != null && !search.isEmpty()) {
            sql.append(" AND (p.name LIKE ? OR p.product_code LIKE ?)");
        }
        if (lowOnly) {
            sql.append(" AND p.quantity <= p.reorder_level");
        }
        sql.append(" ORDER BY p.id");

        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            int idx = 1;
            if (search != null && !search.isEmpty()) {
                String like = "%" + search + "%";
                ps.setString(idx++, like);
                ps.setString(idx++, like);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (!first) json.append(",");
                    first = false;
                    json.append("{")
                        .append("\"id\":").append(rs.getInt("id")).append(",")
                        .append("\"code\":\"").append(escape(rs.getString("product_code"))).append("\",")
                        .append("\"name\":\"").append(escape(rs.getString("name"))).append("\",")
                        .append("\"categoryId\":").append(rs.getInt("category_id")).append(",")
                        .append("\"category\":\"").append(escape(nz(rs.getString("category_name")))).append("\",")
                        .append("\"qty\":").append(rs.getInt("quantity")).append(",")
                        .append("\"reorder\":").append(rs.getInt("reorder_level")).append(",")
                        .append("\"price\":").append(rs.getBigDecimal("unit_price")).append(",")
                        .append("\"supplier\":\"").append(escape(nz(rs.getString("supplier")))).append("\"")
                        .append("}");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        json.append("]");
        sendJson(exchange, json.toString());
    }

    private static void handleAddProduct(HttpExchange exchange) throws IOException {
        Map<String, String> f = readFormBody(exchange);
        String sql = "INSERT INTO products (product_code, name, category_id, quantity, reorder_level, unit_price, supplier) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        boolean ok = false;
        String error = null;
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, f.get("code"));
            ps.setString(2, f.get("name"));
            ps.setInt(3, Integer.parseInt(f.getOrDefault("categoryId", "0")));
            ps.setInt(4, Integer.parseInt(f.getOrDefault("qty", "0")));
            ps.setInt(5, Integer.parseInt(f.getOrDefault("reorder", "0")));
            ps.setBigDecimal(6, new java.math.BigDecimal(f.getOrDefault("price", "0")));
            ps.setString(7, f.get("supplier"));
            ok = ps.executeUpdate() > 0;
        } catch (Exception e) {
            error = e.getMessage();
        }
        sendJson(exchange, "{\"success\":" + ok + (error != null ? ",\"error\":\"" + escape(error) + "\"" : "") + "}");
    }

    private static void handleUpdateProduct(HttpExchange exchange) throws IOException {
        Map<String, String> f = readFormBody(exchange);
        String sql = "UPDATE products SET product_code=?, name=?, category_id=?, quantity=?, " +
                     "reorder_level=?, unit_price=?, supplier=? WHERE id=?";
        boolean ok = false;
        String error = null;
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, f.get("code"));
            ps.setString(2, f.get("name"));
            ps.setInt(3, Integer.parseInt(f.getOrDefault("categoryId", "0")));
            ps.setInt(4, Integer.parseInt(f.getOrDefault("qty", "0")));
            ps.setInt(5, Integer.parseInt(f.getOrDefault("reorder", "0")));
            ps.setBigDecimal(6, new java.math.BigDecimal(f.getOrDefault("price", "0")));
            ps.setString(7, f.get("supplier"));
            ps.setInt(8, Integer.parseInt(f.getOrDefault("id", "0")));
            ok = ps.executeUpdate() > 0;
        } catch (Exception e) {
            error = e.getMessage();
        }
        sendJson(exchange, "{\"success\":" + ok + (error != null ? ",\"error\":\"" + escape(error) + "\"" : "") + "}");
    }

    private static void handleDeleteProduct(HttpExchange exchange) throws IOException {
        Map<String, String> f = readFormBody(exchange);
        if (!isValidUser(f.getOrDefault("username", ""))) { sendForbidden(exchange); return; }
        boolean ok = false;
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM products WHERE id = ?")) {
            ps.setInt(1, Integer.parseInt(f.getOrDefault("id", "0")));
            ok = ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        sendJson(exchange, "{\"success\":" + ok + "}");
    }

    private static void handleStockAdjust(HttpExchange exchange) throws IOException {
        Map<String, String> f = readFormBody(exchange);
        int id = Integer.parseInt(f.getOrDefault("id", "0"));
        int qty = Integer.parseInt(f.getOrDefault("qty", "0"));
        String type = f.getOrDefault("type", "IN");
        String remarks = "IN".equals(type) ? "Web stock-in" : "Web stock-out";

        String updateSql = "IN".equals(type)
                ? "UPDATE products SET quantity = quantity + ? WHERE id = ?"
                : "UPDATE products SET quantity = quantity - ? WHERE id = ? AND quantity >= ?";
        String logSql = "INSERT INTO stock_transactions (product_id, transaction_type, quantity, remarks) VALUES (?, ?, ?, ?)";

        Connection con = null;
        boolean ok = false;
        String error = null;
        try {
            con = getConnection();
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(updateSql)) {
                ps.setInt(1, qty);
                ps.setInt(2, id);
                if ("OUT".equals(type)) ps.setInt(3, qty);
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    con.rollback();
                    sendJson(exchange, "{\"success\":false,\"error\":\"Not enough stock or invalid item\"}");
                    return;
                }
            }
            try (PreparedStatement ps = con.prepareStatement(logSql)) {
                ps.setInt(1, id);
                ps.setString(2, type);
                ps.setInt(3, qty);
                ps.setString(4, remarks);
                ps.executeUpdate();
            }
            con.commit();
            ok = true;
        } catch (Exception e) {
            error = e.getMessage();
            try { if (con != null) con.rollback(); } catch (SQLException ignored) {}
        } finally {
            try { if (con != null) con.setAutoCommit(true); } catch (SQLException ignored) {}
        }
        sendJson(exchange, "{\"success\":" + ok + (error != null ? ",\"error\":\"" + escape(error) + "\"" : "") + "}");
    }

    private static void handleTransactions(HttpExchange exchange) throws IOException {
        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        if (!isAdmin(params.getOrDefault("username", ""))) { sendForbidden(exchange); return; }

        String sql = "SELECT t.transaction_date, p.product_code, p.name, t.transaction_type, t.quantity, t.remarks " +
                     "FROM stock_transactions t JOIN products p ON t.product_id = p.id " +
                     "ORDER BY t.transaction_date DESC LIMIT 200";
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        try (Connection con = getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;
                json.append("{")
                    .append("\"date\":\"").append(rs.getTimestamp("transaction_date")).append("\",")
                    .append("\"code\":\"").append(escape(rs.getString("product_code"))).append("\",")
                    .append("\"name\":\"").append(escape(rs.getString("name"))).append("\",")
                    .append("\"type\":\"").append(rs.getString("transaction_type")).append("\",")
                    .append("\"qty\":").append(rs.getInt("quantity")).append(",")
                    .append("\"remarks\":\"").append(escape(nz(rs.getString("remarks")))).append("\"")
                    .append("}");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        json.append("]");
        sendJson(exchange, json.toString());
    }

    private static void handleDailyReport(HttpExchange exchange) throws IOException {
        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        if (!isAdmin(params.getOrDefault("username", ""))) { sendForbidden(exchange); return; }

        int days = 30;
        try { days = Math.max(1, Math.min(90, Integer.parseInt(params.getOrDefault("days", "30")))); }
        catch (NumberFormatException ignored) { }

        // WITH RECURSIVE builds one row per calendar day in the range, even days with
        // zero transactions, then LEFT JOINs the actual movements onto each day.
        String sql =
                "WITH RECURSIVE date_range AS ( " +
                "  SELECT CURDATE() - INTERVAL ? DAY AS day " +
                "  UNION ALL " +
                "  SELECT day + INTERVAL 1 DAY FROM date_range WHERE day < CURDATE() " +
                ") " +
                "SELECT dr.day AS day, " +
                "COALESCE(SUM(CASE WHEN t.transaction_type='IN' THEN t.quantity ELSE 0 END),0) AS total_in, " +
                "COALESCE(SUM(CASE WHEN t.transaction_type='OUT' THEN t.quantity ELSE 0 END),0) AS total_out, " +
                "COUNT(t.id) AS tx_count " +
                "FROM date_range dr " +
                "LEFT JOIN stock_transactions t ON DATE(t.transaction_date) = dr.day " +
                "GROUP BY dr.day ORDER BY dr.day DESC";

        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, days - 1);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (!first) json.append(",");
                    first = false;
                    int totalIn = rs.getInt("total_in");
                    int totalOut = rs.getInt("total_out");
                    json.append("{")
                        .append("\"day\":\"").append(rs.getDate("day")).append("\",")
                        .append("\"in\":").append(totalIn).append(",")
                        .append("\"out\":").append(totalOut).append(",")
                        .append("\"net\":").append(totalIn - totalOut).append(",")
                        .append("\"count\":").append(rs.getInt("tx_count"))
                        .append("}");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        json.append("]");
        sendJson(exchange, json.toString());
    }

    // =====================================================================
    //  The whole single-page app: HTML + CSS + JS
    // =====================================================================
    private static final String PAGE_HTML =
        "<!DOCTYPE html>\n" +
        "<html lang=\"en\"><head>\n" +
        "<meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n" +
        "<title>Smart Inventory Management System - Web</title>\n" +
        "<style>\n" +
        ":root{--bg:#0F172A;--panel:#16213A;--line:#26324D;--text:#E7ECF5;--muted:#8B96AD;--amber:#F5A623;--red:#EF5A5A;--green:#4ADE80;--shadow:rgba(0,0,0,0.25);}\n" +
        "body.light{--bg:#F4F6FB;--panel:#FFFFFF;--line:#E1E6F0;--text:#1B2436;--muted:#5C6780;--amber:#B4700A;--red:#C0392B;--green:#1E8E5A;--shadow:rgba(30,40,70,0.08);}\n" +
        "body.light input,body.light select{background:#FFFFFF;}\n" +
        "body.light button{background:#FFFFFF;}\n" +
        "body.light button.primary{background:var(--amber);color:#FFFFFF;}\n" +
        "*{box-sizing:border-box;} body{margin:0;background:var(--bg);color:var(--text);font-family:-apple-system,Segoe UI,Roboto,Arial,sans-serif;transition:background 0.2s,color 0.2s;}\n" +
        "input,select,button{font-family:inherit;font-size:14px;}\n" +
        "input,select{background:#0B1220;border:1px solid var(--line);color:var(--text);padding:8px 10px;border-radius:6px;width:100%;}\n" +
        "button{cursor:pointer;border-radius:6px;border:1px solid var(--line);background:var(--panel);color:var(--text);padding:8px 14px;}\n" +
        "button:hover{border-color:var(--amber);}\n" +
        "button.primary{background:var(--amber);color:#1a1305;border-color:var(--amber);font-weight:600;}\n" +
        "button.danger{border-color:var(--red);color:var(--red);}\n" +
        "button.small{padding:4px 8px;font-size:12px;}\n" +
        "button.theme-toggle{padding:6px 12px;font-size:16px;line-height:1;}\n" +
        "#loginView{display:flex;align-items:center;justify-content:center;height:100vh;}\n" +
        ".login-card{background:var(--panel);border:1px solid var(--line);border-radius:14px;padding:36px;width:320px;box-shadow:0 12px 32px var(--shadow);}\n" +
        ".login-card h1{font-size:19px;margin:0 0 4px;font-weight:700;}\n" +
        ".login-card .tagline{color:var(--muted);font-size:12px;margin-bottom:18px;}\n" +
        ".login-card label{display:block;font-size:12px;color:var(--muted);margin:12px 0 4px;}\n" +
        ".login-card .err{color:var(--red);font-size:12px;margin-top:10px;min-height:14px;}\n" +
        ".login-card .hint{color:var(--muted);font-size:11px;margin-top:14px;text-align:center;}\n" +
        ".pass-row{position:relative;}\n" +
        ".pass-row button{position:absolute;right:4px;top:4px;bottom:4px;padding:0 10px;font-size:11px;}\n" +
        ".remember-row{display:flex;align-items:center;gap:6px;margin-top:12px;font-size:12px;color:var(--muted);}\n" +
        ".remember-row input{width:auto;}\n" +
        ".role-tabs{display:flex;border:1px solid var(--line);border-radius:8px;overflow:hidden;margin-bottom:18px;}\n" +
        ".role-tabs button{flex:1;border:none;border-radius:0;background:var(--bg);padding:10px 0;font-weight:600;}\n" +
        ".role-tabs button.active{background:var(--amber);color:#1a1305;}\n" +
        "#appView{display:none;padding:28px 24px 60px;max-width:1200px;margin:0 auto;}\n" +
        ".role-badge{display:inline-block;font-size:11px;font-weight:700;letter-spacing:0.5px;padding:3px 10px;border-radius:20px;margin-left:10px;vertical-align:middle;}\n" +
        "#appView.role-admin .role-badge{background:rgba(74,222,128,0.18);color:var(--green);border:1px solid var(--green);}\n" +
        "#appView.role-staff .role-badge{background:rgba(245,166,35,0.18);color:var(--amber);border:1px solid var(--amber);}\n" +
        "#appView.role-staff .admin-only{display:none !important;}\n" +
        ".modal tr.empty-day td{opacity:0.5;}\n" +
        "header{display:flex;justify-content:space-between;align-items:center;border-bottom:1px solid var(--line);padding-bottom:16px;margin-bottom:22px;flex-wrap:wrap;gap:10px;}\n" +
        "header h1{font-size:20px;margin:0;}\n" +
        "header .welcome{color:var(--muted);font-size:12px;margin-top:2px;}\n" +
        "header .actions{display:flex;gap:8px;align-items:center;}\n" +
        ".alert-banner{display:none;align-items:center;gap:10px;background:rgba(239,90,90,0.12);border:1px solid var(--red);color:var(--red);border-radius:10px;padding:12px 16px;margin-bottom:20px;font-size:13px;font-weight:600;}\n" +
        ".alert-banner button{margin-left:auto;border-color:var(--red);color:var(--red);font-weight:600;}\n" +
        ".stats{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;margin-bottom:22px;}\n" +
        ".stat{background:var(--panel);border:1px solid var(--line);border-radius:10px;padding:14px 16px;box-shadow:0 2px 8px var(--shadow);}\n" +
        ".stat .label{color:var(--muted);font-size:12px;margin-bottom:4px;}\n" +
        ".stat .value{font-size:24px;font-weight:700;}\n" +
        ".stat.amber .value{color:var(--amber);} .stat.red .value{color:var(--red);} .stat.green .value{color:var(--green);}\n" +
        ".toolbar{display:flex;gap:8px;flex-wrap:wrap;margin-bottom:18px;align-items:center;}\n" +
        ".toolbar input{width:220px;}\n" +
        "#barcodeBox{border-color:var(--green);width:240px;}\n" +
        "@keyframes scanFlash{0%{background:rgba(74,222,128,0.35);}100%{background:transparent;}}\n" +
        "tr.scan-hit td{animation:scanFlash 1.6s ease-out;}\n" +
        ".layout{display:grid;grid-template-columns:1fr 300px;gap:20px;align-items:start;}\n" +
        "@media(max-width:800px){.layout{grid-template-columns:1fr;}}\n" +
        "table{width:100%;border-collapse:collapse;font-size:13px;background:var(--panel);border:1px solid var(--line);border-radius:8px;overflow:hidden;}\n" +
        "th{text-align:left;color:var(--muted);font-weight:500;font-size:11px;padding:8px 10px;border-bottom:1px solid var(--line);}\n" +
        "td{padding:8px 10px;border-bottom:1px solid var(--line);vertical-align:middle;}\n" +
        "tr.low td{background:rgba(239,90,90,0.08);}\n" +
        ".tag{display:inline-block;font-size:10px;padding:2px 6px;border-radius:20px;border:1px solid var(--red);color:var(--red);}\n" +
        ".form-card{background:var(--panel);border:1px solid var(--line);border-radius:10px;padding:16px;}\n" +
        ".form-card h2{font-size:13px;color:var(--muted);text-transform:uppercase;letter-spacing:0.5px;margin:0 0 14px;}\n" +
        ".form-card label{display:block;font-size:11px;color:var(--muted);margin:10px 0 4px;}\n" +
        ".form-card .row{display:flex;gap:6px;margin-top:14px;}\n" +
        ".form-card .row button{flex:1;}\n" +
        ".stock-cell{display:flex;gap:4px;align-items:center;}\n" +
        ".stock-cell input{width:55px;padding:4px 6px;}\n" +
        ".modal-overlay{display:none;position:fixed;inset:0;background:rgba(0,0,0,0.6);align-items:flex-start;justify-content:center;padding-top:60px;z-index:10;}\n" +
        ".modal{background:var(--panel);border:1px solid var(--line);border-radius:10px;padding:20px;width:800px;max-width:92%;max-height:75vh;overflow:auto;}\n" +
        ".modal h2{margin-top:0;}\n" +
        ".status-msg{font-size:12px;color:var(--muted);margin:10px 0;min-height:14px;}\n" +
        "</style></head>\n" +
        "<body>\n" +

        "<button class=\"theme-toggle\" id=\"themeToggleBtn\" onclick=\"toggleTheme()\" style=\"position:fixed;top:16px;right:16px;\">Dark</button>\n" +

        "<div id=\"loginView\"><div class=\"login-card\">\n" +
        "  <h1>Inventory Login</h1>\n" +
        "  <div class=\"tagline\">Smart Inventory Management System</div>\n" +
        "  <div class=\"role-tabs\">\n" +
        "    <button type=\"button\" id=\"tabAdmin\" class=\"active\" onclick=\"selectLoginRole('ADMIN')\">Admin</button>\n" +
        "    <button type=\"button\" id=\"tabStaff\" onclick=\"selectLoginRole('STAFF')\">Staff</button>\n" +
        "  </div>\n" +
        "  <label>Username</label><input id=\"loginUser\" type=\"text\" onkeydown=\"if(event.key==='Enter')doLogin()\">\n" +
        "  <label>Password</label>\n" +
        "  <div class=\"pass-row\"><input id=\"loginPass\" type=\"password\" onkeydown=\"if(event.key==='Enter')doLogin()\">" +
        "<button type=\"button\" onclick=\"togglePasswordVisibility()\" id=\"showPassBtn\">Show</button></div>\n" +
        "  <div class=\"remember-row\"><input type=\"checkbox\" id=\"rememberMe\"><label style=\"margin:0;\">Remember username</label></div>\n" +
        "  <div class=\"row\" style=\"margin-top:16px;\"><button class=\"primary\" style=\"width:100%\" onclick=\"doLogin()\">Login</button></div>\n" +
        "  <div class=\"err\" id=\"loginError\"></div>\n" +
        "  <div class=\"hint\">Admin: admin / admin123 &nbsp;|&nbsp; Staff: staff / staff123</div>\n" +
        "</div></div>\n" +

        "<div id=\"appView\">\n" +
        "  <header>\n" +
        "    <div><h1>Smart Inventory Management System<span class=\"role-badge\" id=\"roleBadge\"></span></h1><div class=\"welcome\" id=\"welcomeMsg\"></div></div>\n" +
        "    <div class=\"actions\"><button onclick=\"logout()\">Logout</button></div>\n" +
        "  </header>\n" +
        "  <div class=\"alert-banner\" id=\"lowStockBanner\">\n" +
        "    <span>&#9888;</span><span id=\"lowStockBannerText\"></span>\n" +
        "    <button onclick=\"loadLowStock()\">View Low Stock</button>\n" +
        "  </div>\n" +
        "  <div class=\"stats\">\n" +
        "    <div class=\"stat amber\"><div class=\"label\">Total Products</div><div class=\"value\" id=\"statCount\">-</div></div>\n" +
        "    <div class=\"stat green\"><div class=\"label\">Total Stock Value</div><div class=\"value\" id=\"statValue\">-</div></div>\n" +
        "    <div class=\"stat red\"><div class=\"label\">Low Stock Items</div><div class=\"value\" id=\"statLow\">-</div></div>\n" +
        "  </div>\n" +
        "  <div class=\"toolbar\">\n" +
        "    <input id=\"searchBox\" placeholder=\"Search name/code...\" onkeydown=\"if(event.key==='Enter')searchProducts()\">\n" +
        "    <button onclick=\"searchProducts()\">Search</button>\n" +
        "    <button onclick=\"loadProducts()\">Show All</button>\n" +
        "    <button onclick=\"loadLowStock()\">Low Stock Alert</button>\n" +
        "    <input id=\"barcodeBox\" placeholder=\"\\ud83d\\udd0d Scan or type barcode + Enter\" autocomplete=\"off\" onkeydown=\"if(event.key==='Enter'){event.preventDefault();scanBarcode();}\">\n" +
        "    <button onclick=\"scanBarcode()\">Find</button>\n" +
        "    <button id=\"historyBtn\" class=\"admin-only\" onclick=\"openHistory()\">Transaction History</button>\n" +
        "    <button id=\"dailyReportBtn\" class=\"admin-only\" onclick=\"openDailyReport()\">Daily Report</button>\n" +
        "    <span id=\"roleNote\" style=\"color:var(--muted);font-size:12px;\"></span>\n" +
        "  </div>\n" +
        "  <div class=\"layout\">\n" +
        "    <table><thead><tr><th>S.No.</th><th>Code</th><th>Name</th><th>Category</th><th>Qty</th><th>Reorder</th><th>Price</th><th>Supplier</th><th>Stock</th><th></th></tr></thead>\n" +
        "    <tbody id=\"productRows\"></tbody></table>\n" +
        "    <div class=\"form-card\">\n" +
        "      <h2 id=\"formTitle\">Add Product</h2>\n" +
        "      <input type=\"hidden\" id=\"pId\">\n" +
        "      <label>Product Code</label>\n" +
        "      <div style=\"display:flex;gap:6px;\"><input id=\"pCode\" style=\"flex:1;\">" +
        "<button type=\"button\" class=\"small\" title=\"Click, then scan or type a code\" " +
        "onclick=\"document.getElementById('pCode').focus()\">\\ud83d\\udd0d</button></div>\n" +
        "      <label>Name</label><input id=\"pName\">\n" +
        "      <label>Category</label><select id=\"pCategory\"></select>\n" +
        "      <label>Quantity</label><input id=\"pQty\" type=\"number\">\n" +
        "      <label>Reorder Level</label><input id=\"pReorder\" type=\"number\">\n" +
        "      <label>Unit Price</label><input id=\"pPrice\" type=\"number\" step=\"0.01\">\n" +
        "      <label>Supplier</label><input id=\"pSupplier\">\n" +
        "      <div class=\"status-msg\" id=\"formStatus\"></div>\n" +
        "      <div class=\"row\">\n" +
        "        <button class=\"primary\" id=\"saveBtn\" onclick=\"addProduct()\">Add Product</button>\n" +
        "        <button onclick=\"clearForm()\">Clear</button>\n" +
        "      </div>\n" +
        "    </div>\n" +
        "  </div>\n" +
        "</div>\n" +

        "<div class=\"modal-overlay\" id=\"historyOverlay\">\n" +
        "  <div class=\"modal\">\n" +
        "    <h2>Transaction History</h2>\n" +
        "    <table><thead><tr><th>Date/Time</th><th>Code</th><th>Name</th><th>Type</th><th>Qty</th><th>Remarks</th></tr></thead>\n" +
        "    <tbody id=\"historyRows\"></tbody></table>\n" +
        "    <div class=\"row\" style=\"margin-top:14px;\"><button onclick=\"closeHistory()\">Close</button></div>\n" +
        "  </div>\n" +
        "</div>\n" +

        "<div class=\"modal-overlay\" id=\"dailyReportOverlay\">\n" +
        "  <div class=\"modal\">\n" +
        "    <h2>Daily Stock Movement Report <span style=\"font-weight:400;font-size:13px;color:var(--muted);\">(last 30 days, every date shown)</span></h2>\n" +
        "    <table><thead><tr><th>Date</th><th>Stock IN</th><th>Stock OUT</th><th>Net Change</th><th>Transactions</th></tr></thead>\n" +
        "    <tbody id=\"dailyReportRows\"></tbody></table>\n" +
        "    <div class=\"row\" style=\"margin-top:14px;\"><button onclick=\"closeDailyReport()\">Close</button></div>\n" +
        "  </div>\n" +
        "</div>\n" +

        "<script>\n" +
        "let editingId = null;\n" +
        "let currentUsername = '';\n" +
        "let currentRole = '';\n" +
        "let selectedRole = 'ADMIN';\n" +
        "function selectLoginRole(role){\n" +
        "  selectedRole = role;\n" +
        "  document.getElementById('tabAdmin').classList.toggle('active', role==='ADMIN');\n" +
        "  document.getElementById('tabStaff').classList.toggle('active', role==='STAFF');\n" +
        "  document.getElementById('loginError').textContent = '';\n" +
        "}\n" +
        "\n" +
        "function applyTheme(theme){\n" +
        "  document.body.className = theme === 'light' ? 'light' : '';\n" +
        "  document.getElementById('themeToggleBtn').textContent = theme === 'light' ? 'Light' : 'Dark';\n" +
        "  localStorage.setItem('inventoryTheme', theme);\n" +
        "}\n" +
        "function toggleTheme(){\n" +
        "  const isLight = document.body.classList.contains('light');\n" +
        "  applyTheme(isLight ? 'dark' : 'light');\n" +
        "}\n" +
        "function togglePasswordVisibility(){\n" +
        "  const field = document.getElementById('loginPass');\n" +
        "  const btn = document.getElementById('showPassBtn');\n" +
        "  if(field.type === 'password'){ field.type = 'text'; btn.textContent = 'Hide'; }\n" +
        "  else { field.type = 'password'; btn.textContent = 'Show'; }\n" +
        "}\n" +
        "(function initPrefs(){\n" +
        "  const savedTheme = localStorage.getItem('inventoryTheme') || 'dark';\n" +
        "  applyTheme(savedTheme);\n" +
        "  const savedUser = localStorage.getItem('inventoryRememberedUser');\n" +
        "  if(savedUser){ document.getElementById('loginUser').value = savedUser; document.getElementById('rememberMe').checked = true; }\n" +
        "})();\n" +
        "\n" +
        "async function postForm(url, data){\n" +
        "  const body = new URLSearchParams(data).toString();\n" +
        "  const res = await fetch(url, {method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body});\n" +
        "  return res.json();\n" +
        "}\n" +
        "\n" +
        "async function doLogin(){\n" +
        "  const username = document.getElementById('loginUser').value;\n" +
        "  const password = document.getElementById('loginPass').value;\n" +
        "  const result = await postForm('/api/login', {username, password});\n" +
        "  if(result.success){\n" +
        "    const actualRole = (result.role || '').toUpperCase();\n" +
        "    if(actualRole !== selectedRole){\n" +
        "      document.getElementById('loginError').textContent =\n" +
        "        'This account is a ' + actualRole + ' account \\u2014 switch to the ' + actualRole + ' tab above.';\n" +
        "      return;\n" +
        "    }\n" +
        "    currentUsername = username;\n" +
        "    currentRole = actualRole;\n" +
        "    if(document.getElementById('rememberMe').checked){ localStorage.setItem('inventoryRememberedUser', username); }\n" +
        "    else { localStorage.removeItem('inventoryRememberedUser'); }\n" +
        "    document.getElementById('loginView').style.display='none';\n" +
        "    const appView = document.getElementById('appView');\n" +
        "    appView.style.display='block';\n" +
        "    const isAdmin = currentRole === 'ADMIN';\n" +
        "    appView.classList.toggle('role-admin', isAdmin);\n" +
        "    appView.classList.toggle('role-staff', !isAdmin);\n" +
        "    document.getElementById('roleBadge').textContent = isAdmin ? 'ADMIN DASHBOARD' : 'STAFF DASHBOARD';\n" +
        "    const now = new Date();\n" +
        "    document.getElementById('welcomeMsg').textContent = 'Logged in as ' + username + ' (' + currentRole + ') \\u2014 ' + now.toLocaleDateString(undefined,{weekday:'long',year:'numeric',month:'short',day:'numeric'});\n" +
        "    document.getElementById('roleNote').textContent = isAdmin\n" +
        "      ? 'Full access: products, scanning, transaction history and daily report.'\n" +
        "      : 'Staff access: view, add, edit, delete products, stock IN/OUT and barcode scanning.';\n" +
        "    loadCategories(); loadProducts(); loadSummary();\n" +
        "  } else {\n" +
        "    document.getElementById('loginError').textContent = 'Invalid username or password.';\n" +
        "  }\n" +
        "}\n" +
        "function logout(){\n" +
        "  document.getElementById('appView').style.display='none';\n" +
        "  document.getElementById('appView').classList.remove('role-admin','role-staff');\n" +
        "  document.getElementById('loginView').style.display='flex';\n" +
        "  document.getElementById('loginPass').value='';\n" +
        "}\n" +
        "\n" +
        "async function loadCategories(){\n" +
        "  const res = await fetch('/api/categories');\n" +
        "  const cats = await res.json();\n" +
        "  const sel = document.getElementById('pCategory');\n" +
        "  sel.innerHTML = '';\n" +
        "  cats.forEach(c=>{\n" +
        "    const opt = document.createElement('option');\n" +
        "    opt.value = c.id; opt.textContent = c.name;\n" +
        "    sel.appendChild(opt);\n" +
        "  });\n" +
        "}\n" +
        "\n" +
        "async function loadSummary(){\n" +
        "  const res = await fetch('/api/summary');\n" +
        "  const s = await res.json();\n" +
        "  document.getElementById('statCount').textContent = s.count;\n" +
        "  document.getElementById('statValue').textContent = 'Rs. ' + Number(s.value).toFixed(2);\n" +
        "  document.getElementById('statLow').textContent = s.low;\n" +
        "  const banner = document.getElementById('lowStockBanner');\n" +
        "  if(s.low > 0){\n" +
        "    document.getElementById('lowStockBannerText').textContent =\n" +
        "      s.low + ' item' + (s.low === 1 ? '' : 's') + ' at or below reorder level \\u2014 restock needed.';\n" +
        "    banner.style.display = 'flex';\n" +
        "  } else {\n" +
        "    banner.style.display = 'none';\n" +
        "  }\n" +
        "}\n" +
        "\n" +
        "let lastProducts = [];\n" +
        "function renderProducts(products){\n" +
        "  lastProducts = products;\n" +
        "  const rowsEl = document.getElementById('productRows');\n" +
        "  rowsEl.innerHTML = '';\n" +
        "  products.forEach((p, idx)=>{\n" +
        "    const isLow = p.qty <= p.reorder;\n" +
        "    const tr = document.createElement('tr');\n" +
        "    tr.id = 'row-'+p.id;\n" +
        "    tr.dataset.code = p.code.toLowerCase();\n" +
        "    tr.className = isLow ? 'low' : '';\n" +
        "    tr.innerHTML =\n" +
        "      '<td>'+(idx+1)+'</td><td>'+p.code+'</td><td>'+p.name+'</td><td>'+p.category+'</td>' +\n" +
        "      '<td>'+p.qty+(isLow?' <span class=\"tag\">Low</span>':'')+'</td><td>'+p.reorder+'</td>' +\n" +
        "      '<td>Rs. '+Number(p.price).toFixed(2)+'</td><td>'+p.supplier+'</td>' +\n" +
        "      '<td><div class=\"stock-cell\"><input type=\"number\" id=\"stockQty'+p.id+'\" min=\"1\" placeholder=\"qty\">' +\n" +
        "        '<button class=\"small\" onclick=\"adjustStock('+p.id+',\\'IN\\')\">IN</button>' +\n" +
        "        '<button class=\"small\" onclick=\"adjustStock('+p.id+',\\'OUT\\')\">OUT</button></div></td>' +\n" +
        "      '<td><button class=\"small\" onclick=\\'editProduct(' + JSON.stringify(p).replace(/'/g,\"&#39;\") + ')\\'>Edit</button> ' +\n" +
        "      '<button class=\"small danger\" onclick=\"deleteProduct('+p.id+')\">Delete</button></td>';\n" +
        "    rowsEl.appendChild(tr);\n" +
        "  });\n" +
        "}\n" +
        "\n" +
        "async function loadProducts(){\n" +
        "  document.getElementById('searchBox').value='';\n" +
        "  const res = await fetch('/api/products');\n" +
        "  renderProducts(await res.json());\n" +
        "  loadSummary();\n" +
        "}\n" +
        "async function searchProducts(){\n" +
        "  const q = document.getElementById('searchBox').value;\n" +
        "  const res = await fetch('/api/products?search='+encodeURIComponent(q));\n" +
        "  renderProducts(await res.json());\n" +
        "}\n" +
        "async function loadLowStock(){\n" +
        "  const res = await fetch('/api/products?lowstock=true');\n" +
        "  renderProducts(await res.json());\n" +
        "}\n" +
        "\n" +
        "// Most barcode scanners act like a keyboard: they type the code fast, then send Enter.\n" +
        "// The barcodeBox's onkeydown already calls this on Enter, so a plugged-in scanner\n" +
        "// works the moment the field has focus - no special driver needed.\n" +
        "async function scanBarcode(){\n" +
        "  const box = document.getElementById('barcodeBox');\n" +
        "  const code = box.value.trim();\n" +
        "  box.value = '';\n" +
        "  if(!code) return;\n" +
        "  await loadProducts();\n" +
        "  const match = lastProducts.find(p => p.code.toLowerCase() === code.toLowerCase());\n" +
        "  if(match){\n" +
        "    const row = document.getElementById('row-'+match.id);\n" +
        "    if(row){\n" +
        "      row.scrollIntoView({behavior:'smooth', block:'center'});\n" +
        "      row.classList.remove('scan-hit'); void row.offsetWidth; row.classList.add('scan-hit');\n" +
        "      const qtyInput = document.getElementById('stockQty'+match.id);\n" +
        "      if(qtyInput) qtyInput.focus();\n" +
        "    }\n" +
        "    document.getElementById('formStatus').textContent = '';\n" +
        "  } else {\n" +
        "    if(confirm('No product with code \"'+code+'\". Add it as a new product?')){\n" +
        "      clearForm();\n" +
        "      document.getElementById('pCode').value = code;\n" +
        "      document.getElementById('pName').focus();\n" +
        "      window.scrollTo(0, document.body.scrollHeight);\n" +
        "    }\n" +
        "  }\n" +
        "}\n" +
        "\n" +
        "function clearForm(){\n" +
        "  editingId = null;\n" +
        "  document.getElementById('formTitle').textContent = 'Add Product';\n" +
        "  document.getElementById('saveBtn').textContent = 'Add Product';\n" +
        "  document.getElementById('saveBtn').setAttribute('onclick','addProduct()');\n" +
        "  ['pId','pCode','pName','pQty','pReorder','pPrice','pSupplier'].forEach(id=>document.getElementById(id).value='');\n" +
        "  document.getElementById('formStatus').textContent='';\n" +
        "}\n" +
        "\n" +
        "function editProduct(p){\n" +
        "  editingId = p.id;\n" +
        "  document.getElementById('formTitle').textContent = 'Edit Product';\n" +
        "  document.getElementById('saveBtn').textContent = 'Update Product';\n" +
        "  document.getElementById('saveBtn').setAttribute('onclick','updateProduct()');\n" +
        "  document.getElementById('pId').value = p.id;\n" +
        "  document.getElementById('pCode').value = p.code;\n" +
        "  document.getElementById('pName').value = p.name;\n" +
        "  document.getElementById('pCategory').value = p.categoryId;\n" +
        "  document.getElementById('pQty').value = p.qty;\n" +
        "  document.getElementById('pReorder').value = p.reorder;\n" +
        "  document.getElementById('pPrice').value = p.price;\n" +
        "  document.getElementById('pSupplier').value = p.supplier;\n" +
        "  window.scrollTo(0,0);\n" +
        "}\n" +
        "\n" +
        "function formData(){\n" +
        "  return {\n" +
        "    id: document.getElementById('pId').value,\n" +
        "    code: document.getElementById('pCode').value,\n" +
        "    name: document.getElementById('pName').value,\n" +
        "    categoryId: document.getElementById('pCategory').value,\n" +
        "    qty: document.getElementById('pQty').value,\n" +
        "    reorder: document.getElementById('pReorder').value,\n" +
        "    price: document.getElementById('pPrice').value,\n" +
        "    supplier: document.getElementById('pSupplier').value\n" +
        "  };\n" +
        "}\n" +
        "\n" +
        "async function addProduct(){\n" +
        "  const r = await postForm('/api/products/add', formData());\n" +
        "  document.getElementById('formStatus').textContent = r.success ? 'Product added.' : ('Failed: '+(r.error||'duplicate code?'));\n" +
        "  if(r.success){ clearForm(); loadProducts(); }\n" +
        "}\n" +
        "async function updateProduct(){\n" +
        "  const r = await postForm('/api/products/update', formData());\n" +
        "  document.getElementById('formStatus').textContent = r.success ? 'Product updated.' : ('Failed: '+(r.error||''));\n" +
        "  if(r.success){ clearForm(); loadProducts(); }\n" +
        "}\n" +
        "async function deleteProduct(id){\n" +
        "  if(!confirm('Delete this product?')) return;\n" +
        "  const r = await postForm('/api/products/delete', {id, username: currentUsername});\n" +
        "  if(!r.success) alert('Failed: ' + (r.error || 'unknown error'));\n" +
        "  loadProducts();\n" +
        "}\n" +
        "async function adjustStock(id, type){\n" +
        "  const qtyInput = document.getElementById('stockQty'+id);\n" +
        "  const qty = qtyInput.value;\n" +
        "  if(!qty || qty<=0){ alert('Enter a quantity first.'); return; }\n" +
        "  const r = await postForm('/api/stock', {id, type, qty});\n" +
        "  if(!r.success) alert('Failed: ' + (r.error || 'insufficient stock?'));\n" +
        "  loadProducts();\n" +
        "}\n" +
        "\n" +
        "async function openHistory(){\n" +
        "  const res = await fetch('/api/transactions?username=' + encodeURIComponent(currentUsername));\n" +
        "  const rows = await res.json();\n" +
        "  if(rows.error){ alert(rows.error); return; }\n" +
        "  const el = document.getElementById('historyRows');\n" +
        "  el.innerHTML = '';\n" +
        "  rows.forEach(t=>{\n" +
        "    const tr = document.createElement('tr');\n" +
        "    tr.innerHTML = '<td>'+t.date+'</td><td>'+t.code+'</td><td>'+t.name+'</td><td>'+t.type+'</td><td>'+t.qty+'</td><td>'+t.remarks+'</td>';\n" +
        "    el.appendChild(tr);\n" +
        "  });\n" +
        "  document.getElementById('historyOverlay').style.display='flex';\n" +
        "}\n" +
        "function closeHistory(){ document.getElementById('historyOverlay').style.display='none'; }\n" +
        "\n" +
        "async function openDailyReport(){\n" +
        "  const res = await fetch('/api/reports/daily?username=' + encodeURIComponent(currentUsername));\n" +
        "  const rows = await res.json();\n" +
        "  if(rows.error){ alert(rows.error); return; }\n" +
        "  const el = document.getElementById('dailyReportRows');\n" +
        "  el.innerHTML = '';\n" +
        "  rows.forEach(r=>{\n" +
        "    const tr = document.createElement('tr');\n" +
        "    if(r.count === 0) tr.className = 'empty-day';\n" +
        "    const netColor = r.net >= 0 ? 'var(--green)' : 'var(--red)';\n" +
        "    tr.innerHTML = '<td>'+r.day+'</td><td>'+r.in+'</td><td>'+r.out+'</td>' +\n" +
        "      '<td style=\"color:'+netColor+';font-weight:600;\">'+(r.net>=0?'+':'')+r.net+'</td><td>'+r.count+'</td>';\n" +
        "    el.appendChild(tr);\n" +
        "  });\n" +
        "  document.getElementById('dailyReportOverlay').style.display='flex';\n" +
        "}\n" +
        "function closeDailyReport(){ document.getElementById('dailyReportOverlay').style.display='none'; }\n" +
        "</script>\n" +
        "</body></html>";
}
