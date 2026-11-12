package com.onlinemedic.service;

public class PaymentService {
    private final PaymentProvider provider;
    public PaymentService(){this(new DemoPaymentProvider());}
    public PaymentService(PaymentProvider provider){this.provider=provider;}
    public PaymentProvider.Result processDemoPayment(String outcome){return provider.pay(outcome);}
}
