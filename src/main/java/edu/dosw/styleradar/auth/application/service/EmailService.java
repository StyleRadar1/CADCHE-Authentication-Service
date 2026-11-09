package edu.dosw.styleradar.auth.application.service;

public interface EmailService {

    void enviarOtp(String email, String codigo);
}
