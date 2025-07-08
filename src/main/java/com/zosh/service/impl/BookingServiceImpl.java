package com.zosh.service.impl;

import com.zosh.domain.BookingStatus;
import com.zosh.modal.*;
import com.zosh.payload.dto.SalonDTO;
import com.zosh.payload.dto.ServiceOfferingDTO;
import com.zosh.payload.dto.UserDTO;
import com.zosh.payload.request.BookingRequest;
import com.zosh.repository.BookingRepository;
import com.zosh.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;

    /* ───────────────────────────── CREATE ───────────────────────────── */
    @Override
    public Booking createBooking(BookingRequest req,
            UserDTO user,
            SalonDTO salon,
            Set<ServiceOfferingDTO> services) throws Exception {

        int totalDuration = services.stream()
                .mapToInt(ServiceOfferingDTO::getDuration)
                .sum();

        LocalDateTime start = req.getStartTime();
        LocalDateTime end = start.plusMinutes(totalDuration);

        if (!isTimeSlotAvailable(salon, start, end))
            throw new Exception("Slot is not available");

        BigDecimal totalPrice = services.stream()
                .map(dto -> BigDecimal.valueOf(dto.getPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2);

        Set<Long> serviceIds = services.stream()
                .map(ServiceOfferingDTO::getId)
                .collect(Collectors.toSet());

        Booking booking = Booking.builder()
                .customerId(user.getId())
                .salonId(salon.getId())
                .startTime(start)
                .endTime(end)
                .serviceIds(serviceIds)
                .totalPrice(totalPrice)
                .status(BookingStatus.PENDING)
                .build();

        return bookingRepository.save(booking);
    }

    /* ── disponibilidad ── */
    private boolean isTimeSlotAvailable(SalonDTO salon,
            LocalDateTime start,
            LocalDateTime end) throws Exception {

        LocalDateTime open = salon.getOpenTime().atDate(start.toLocalDate());
        LocalDateTime close = salon.getCloseTime().atDate(start.toLocalDate());

        if (start.isBefore(open) || end.isAfter(close))
            throw new Exception("Booking time must be within salon's open hours.");

        for (Booking b : getBookingsBySalon(salon.getId())) {
            boolean overlap = start.isBefore(b.getEndTime()) && end.isAfter(b.getStartTime());
            if (overlap || start.isEqual(b.getStartTime()) || end.isEqual(b.getEndTime()))
                throw new Exception("Slot not available, choose different time.");
        }
        return true;
    }

    /* ───────────────────────────── READ ───────────────────────────── */
    @Override
    public List<Booking> getBookingsByCustomer(Long id) {
        return bookingRepository.findByCustomerId(id);
    }

    @Override
    public List<Booking> getBookingsBySalon(Long id) {
        return bookingRepository.findBySalonId(id);
    }

    @Override
    public Booking getBookingById(Long id) {
        return bookingRepository.findById(id).orElse(null);
    }

    @Override
    public List<Booking> getBookingsByDate(LocalDate date, Long salonId) {
        List<Booking> all = getBookingsBySalon(salonId);
        if (date == null)
            return all;
        return all.stream()
                .filter(b -> sameDay(b.getStartTime(), date) || sameDay(b.getEndTime(), date))
                .collect(Collectors.toList());
    }

    private boolean sameDay(LocalDateTime dt, LocalDate d) {
        return dt.toLocalDate().isEqual(d);
    }

    /* ───────────────────────────── UPDATE ─────────────────────────── */
    @Override
    public Booking bookingSucess(PaymentOrder order) {
        Booking b = getBookingById(order.getBookingId());
        if (b != null) {
            b.setStatus(BookingStatus.CONFIRMED);
            return bookingRepository.save(b);
        }
        return null;
    }

    @Override
    public Booking updateBookingStatus(Long id, BookingStatus status) throws Exception {
        Booking b = getBookingById(id);
        if (b == null)
            throw new Exception("Booking not found");
        b.setStatus(status);
        return bookingRepository.save(b);
    }

    /* ───────────────────────────── REPORT ─────────────────────────── */
    @Override
    public SalonReport getSalonReport(Long salonId) {
        List<Booking> list = getBookingsBySalon(salonId);

        BigDecimal earnings = list.stream()
                .map(Booking::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Booking> cancelled = list.stream()
                .filter(b -> b.getStatus() == BookingStatus.CANCELLED)
                .collect(Collectors.toList());

        BigDecimal refunds = cancelled.stream()
                .map(Booking::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        SalonReport r = new SalonReport();
        r.setTotalEarnings(earnings);
        r.setTotalBookings(list.size());
        r.setCancelledBookings(cancelled.size());
        r.setTotalRefund(refunds);
        return r;
    }
}
