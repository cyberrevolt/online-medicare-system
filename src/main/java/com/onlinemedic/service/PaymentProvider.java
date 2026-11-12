package com.onlinemedic.service;
public interface PaymentProvider {
    record Result(boolean success,String transactionId,String message) { }
    Result pay(String outcome);
}
