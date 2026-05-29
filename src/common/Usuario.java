package common;

import java.io.Serializable;

public class Usuario implements Serializable {
    private static final long serialVersionUID = 3L;

    private int id;
    private String username;
    private String email;
    private double walletBalance;

    public Usuario(int id, String username, String email, double walletBalance) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.walletBalance = walletBalance;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public double getWalletBalance() {
        return walletBalance;
    }

    public void setWalletBalance(double walletBalance) {
        this.walletBalance = walletBalance;
    }
}
