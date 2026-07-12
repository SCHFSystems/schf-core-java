package br.com.schf.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "schf.security.bootstrap-admin")
public class BootstrapAdminProperties {

    private String username = "admin";
    private String email = "admin@example.invalid";
    private String password = "local_dev_only_change_me";

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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
