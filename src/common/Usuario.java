package common;

import java.io.Serializable;

public class Usuario implements Serializable {
    private static final long serialVersionUID = 4L; // increment serialVersionUID as class definition changed

    private int id;
    private String username;
    private String email;
    private double walletBalance;
    private String codigoPais;

    public Usuario(int id, String username, String email, double walletBalance, String codigoPais) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.walletBalance = walletBalance;
        this.codigoPais = codigoPais;
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

    public String getCodigoPais() {
        return codigoPais;
    }

    public void setCodigoPais(String codigoPais) {
        this.codigoPais = codigoPais;
    }
}
