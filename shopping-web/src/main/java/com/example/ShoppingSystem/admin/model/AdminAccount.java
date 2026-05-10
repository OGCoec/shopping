package com.example.ShoppingSystem.admin.model;

public class AdminAccount {

    private boolean initialized;
    private String username;
    private String email;
    private String phone;
    private String passwordHash;
    private String updatedAt;

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
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

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public static AdminAccount empty() {
        AdminAccount account = new AdminAccount();
        account.setInitialized(false);
        account.setUsername("");
        account.setEmail("");
        account.setPhone("");
        account.setPasswordHash("");
        account.setUpdatedAt("");
        return account;
    }
}
