package br.com.schf.security.reset;

public interface PasswordResetDeliveryService {

    void deliver(PasswordResetDelivery delivery);
}
