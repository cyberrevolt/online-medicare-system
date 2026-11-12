package com.onlinemedic.service;
import java.util.UUID;

/** Local simulation only. It never contacts a bank or moves real money. */
public class DemoPaymentProvider implements PaymentProvider {
    @Override public Result pay(String outcome){boolean ok="success".equalsIgnoreCase(outcome);return new Result(ok,"DEMO-"+UUID.randomUUID(),ok?"Demo payment approved.":"Demo payment declined.");}
}
