package com.onlinemedic.service;
import java.math.BigDecimal;
import java.util.Map;

public final class CartTotals {
    public static final BigDecimal FREE_DELIVERY_THRESHOLD=new BigDecimal("150.00");
    public static final BigDecimal DELIVERY_FEE=new BigDecimal("50.00");
    private CartTotals() { }
    public static BigDecimal calculate(Map<Long,Integer> quantities,Map<Long,BigDecimal> prices){
        BigDecimal total=BigDecimal.ZERO;
        for(var entry:quantities.entrySet()){
            Integer quantity=entry.getValue();BigDecimal price=prices.get(entry.getKey());
            if(quantity==null||quantity<1||quantity>30||price==null||price.signum()<0)throw new IllegalArgumentException("Cart item quantity or price is invalid.");
            total=total.add(price.multiply(BigDecimal.valueOf(quantity)));
        }
        return total;
    }
    public static BigDecimal deliveryFee(BigDecimal subtotal){
        if(subtotal==null||subtotal.signum()<0)throw new IllegalArgumentException("Cart subtotal is invalid.");
        return subtotal.compareTo(FREE_DELIVERY_THRESHOLD)>0?BigDecimal.ZERO.setScale(2):DELIVERY_FEE;
    }
}
