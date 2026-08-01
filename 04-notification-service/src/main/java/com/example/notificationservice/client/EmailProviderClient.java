package com.example.notificationservice.client;

// this is a plain interface, not a @FeignClient like the other two clients in this package,
// on purpose, this one gets a real implementation class below instead of an auto generated http call
public interface EmailProviderClient {

    void send(String userEmail, String subject, String htmlContent);
}
