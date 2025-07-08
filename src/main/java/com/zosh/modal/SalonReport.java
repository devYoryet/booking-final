package com.zosh.modal;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class SalonReport {
    private Long salonId;
    private String salonName;
    private BigDecimal totalEarnings; // ← de Double a BigDecimal
    private Integer totalBookings;
    private Integer cancelledBookings;
    private BigDecimal totalRefund; // ← de Double a BigDecimal
}
