package com.zosh.payload.dto;

import com.zosh.domain.BookingStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@Data
public class BookingDTO {

    private Long id;

    private Long salonId;
    private SalonDTO salon;

    private Long customerId;
    private UserDTO customer;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private Set<Long> servicesIds;
    private Set<ServiceOfferingDTO> services;

    private BookingStatus status;

    private BigDecimal totalPrice; // CAMBIO: de int a BigDecimal
}
