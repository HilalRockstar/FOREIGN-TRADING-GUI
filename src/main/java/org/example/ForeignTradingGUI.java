package org.example;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.*;
import java.util.Scanner;
import org.json.JSONObject;

public class ForeignTradingGUI {
    private JFrame frame;
    private JTextField usernameField;
    private JPasswordField passwordField; // Instead of JTextField
    private JTextField balanceField;
    private JLabel balanceLabel;
    private JTable transactionTable;
    private DefaultTableModel tableModel;
    private Connection conn;
    private int loggedInUserId = -1;

    public ForeignTradingGUI() {
        connectDatabase();
        createLoginScreen();
    }

    private void connectDatabase() {
        try {
            conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/foreign_trading", "root", "pass123");
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Database Connection Failed!", "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
    private void registerUser() {
        String username = usernameField.getText();
        String password = new String(passwordField.getPassword()); // Fixed getPassword() issue
        String balanceText = balanceField.getText();

        if (username.isEmpty() || password.isEmpty() || balanceText.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "All fields must be filled!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            double balance = Double.parseDouble(balanceText);
            PreparedStatement stmt = conn.prepareStatement("INSERT INTO users (username, password, balance) VALUES (?, ?, ?)"); // Use existing conn
            stmt.setString(1, username);
            stmt.setString(2, password);
            stmt.setDouble(3, balance);
            stmt.executeUpdate();
            JOptionPane.showMessageDialog(frame, "Registration successful!", "Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(frame, "Balance must be a valid number!", "Error", JOptionPane.ERROR_MESSAGE);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(frame, "User registration failed!", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }


    private void createLoginScreen() {
        frame = new JFrame("Foreign Trading System - Login/Register");
        frame.setSize(400, 350);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());

        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(4, 2, 10, 10)); // Increased row count
        panel.setBackground(new Color(40, 40, 40)); // Dark Theme

        JLabel userLabel = new JLabel("Username:");
        userLabel.setForeground(Color.WHITE);
        usernameField = new JTextField();

        JLabel passLabel = new JLabel("Password:");
        passLabel.setForeground(Color.WHITE);
        passwordField = new JPasswordField();

        JLabel balanceLabel = new JLabel("Initial Balance:");
        balanceLabel.setForeground(Color.WHITE);
        balanceField = new JTextField(); // Initialize balanceField

        JButton loginButton = new JButton("Login");
        loginButton.setBackground(new Color(30, 144, 255));
        loginButton.setForeground(Color.WHITE);
        loginButton.setFont(new Font("Arial", Font.BOLD, 14));
        loginButton.addActionListener(this::handleLogin);

        JButton registerButton = new JButton("Register");
        registerButton.setBackground(new Color(0, 204, 102));
        registerButton.setForeground(Color.WHITE);
        registerButton.setFont(new Font("Arial", Font.BOLD, 14));
        registerButton.addActionListener(e -> registerUser());

        panel.add(userLabel);
        panel.add(usernameField);
        panel.add(passLabel);
        panel.add(passwordField);
        panel.add(balanceLabel); // Add balance field
        panel.add(balanceField);
        panel.add(loginButton);
        panel.add(registerButton);

        frame.add(panel, BorderLayout.CENTER);
        frame.setVisible(true);
    }


    private void handleLogin(ActionEvent e) {
        String username = usernameField.getText();
        String password = new String(((JPasswordField) passwordField).getPassword());

        try {
            String query = "SELECT id FROM users WHERE username=? AND password=?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, username);
                stmt.setString(2, password);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    loggedInUserId = rs.getInt("id");
                    frame.dispose();
                    createDashboard();
                } else {
                    JOptionPane.showMessageDialog(frame, "Invalid credentials!", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void createDashboard() {
        frame = new JFrame("Foreign Trading System - Dashboard");
        frame.setSize(600, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        JPanel topPanel = new JPanel();
        topPanel.setBackground(new Color(0, 102, 204)); // Deep Blue
        balanceLabel = new JLabel("Balance: Loading...");
        balanceLabel.setForeground(Color.WHITE);
        balanceLabel.setFont(new Font("Arial", Font.BOLD, 16));
        topPanel.add(balanceLabel);

        JButton buyButton = new JButton("Buy Currency");
        buyButton.setBackground(new Color(34, 177, 76)); // Green
        buyButton.setForeground(Color.WHITE);
        buyButton.setFont(new Font("Arial", Font.BOLD, 14));

        JButton sellButton = new JButton("Sell Currency");
        sellButton.setBackground(new Color(204, 0, 0)); // Red
        sellButton.setForeground(Color.WHITE);
        sellButton.setFont(new Font("Arial", Font.BOLD, 14));

        buyButton.addActionListener(e -> performTransaction("BUY"));
        sellButton.addActionListener(e -> performTransaction("SELL"));

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(buyButton);
        buttonPanel.add(sellButton);

        tableModel = new DefaultTableModel(new String[]{"Currency", "Amount", "Price", "Type"}, 0);
        transactionTable = new JTable(tableModel);
        JScrollPane scrollPane = new JScrollPane(transactionTable);

        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(buttonPanel, BorderLayout.CENTER);
        frame.add(scrollPane, BorderLayout.SOUTH);

        frame.setVisible(true);

        updateBalance();
        updateTransactionHistory();
    }

    private void performTransaction(String type) {
        String currency = JOptionPane.showInputDialog(frame, "Enter Currency (USD, EUR, INR):");
        if (currency == null || currency.trim().isEmpty()) return;

        double exchangeRate = fetchLiveExchangeRate(currency.toUpperCase());
        if (exchangeRate == 0) {
            JOptionPane.showMessageDialog(frame, "Invalid currency!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String amountStr = JOptionPane.showInputDialog(frame, "Enter Amount:");
        if (amountStr == null || amountStr.trim().isEmpty()) return;

        try {
            double amount = Double.parseDouble(amountStr);
            double totalCost = amount * exchangeRate;

            String balanceQuery = "SELECT balance FROM users WHERE id=?";
            String updateBalanceQuery = "UPDATE users SET balance=? WHERE id=?";
            String transactionQuery = "INSERT INTO transactions (user_id, currency, amount, price, transaction_type) VALUES (?, ?, ?, ?, ?)";

            try (PreparedStatement stmt = conn.prepareStatement(balanceQuery)) {
                stmt.setInt(1, loggedInUserId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    double balance = rs.getDouble("balance");

                    if (type.equals("BUY") && balance < totalCost) {
                        JOptionPane.showMessageDialog(frame, "Insufficient balance!", "Error", JOptionPane.ERROR_MESSAGE);
                        return;
                    }

                    double newBalance = type.equals("BUY") ? balance - totalCost : balance + totalCost;
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateBalanceQuery)) {
                        updateStmt.setDouble(1, newBalance);
                        updateStmt.setInt(2, loggedInUserId);
                        updateStmt.executeUpdate();
                    }

                    try (PreparedStatement transStmt = conn.prepareStatement(transactionQuery)) {
                        transStmt.setInt(1, loggedInUserId);
                        transStmt.setString(2, currency);
                        transStmt.setDouble(3, amount);
                        transStmt.setDouble(4, exchangeRate);
                        transStmt.setString(5, type);
                        transStmt.executeUpdate();
                    }

                    JOptionPane.showMessageDialog(frame, type + " Successful!", "Success", JOptionPane.INFORMATION_MESSAGE);
                    updateBalance();
                    updateTransactionHistory();
                }
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(frame, "Invalid input!", "Error", JOptionPane.ERROR_MESSAGE);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void updateBalance() {
        try {
            String query = "SELECT balance FROM users WHERE id=?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, loggedInUserId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    balanceLabel.setText("Balance: $" + rs.getDouble("balance"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void updateTransactionHistory() {
        try {
            String query = "SELECT currency, amount, price, transaction_type FROM transactions WHERE user_id=?";
            tableModel.setRowCount(0);
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, loggedInUserId);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    tableModel.addRow(new Object[]{
                            rs.getString("currency"),
                            rs.getDouble("amount"),
                            rs.getDouble("price"),
                            rs.getString("transaction_type")
                    });
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    private double fetchLiveExchangeRate(String currency) {
        try {
            String apiUrl = "https://api.exchangerate-api.com/v4/latest/USD";
            HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
            connection.setRequestMethod("GET");

            Scanner scanner = new Scanner(connection.getInputStream());
            String jsonResponse = scanner.useDelimiter("\\A").next();
            scanner.close();

            JSONObject jsonObject = new JSONObject(jsonResponse);
            return jsonObject.getJSONObject("rates").getDouble(currency);
        } catch (Exception e) {
            return 0.0;
        }
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(ForeignTradingGUI::new);
    }
}
