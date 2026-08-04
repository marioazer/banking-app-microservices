package com.example.notificationservice.client;

// mirrors EmailProviderClient - a plain interface with a real implementation class below it
// instead of an auto generated http call, since neither provider is a REST API this service calls directly
public interface SmsProviderClient {

    void send(String phoneNumber, String message);
}
