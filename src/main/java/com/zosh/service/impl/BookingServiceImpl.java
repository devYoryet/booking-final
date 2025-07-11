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
    private boolean isTimeSlotAvailable(SalonDTO salon, LocalDateTime start, LocalDateTime end) throws Exception {

        System.out.println("🕐 VALIDANDO SLOT DE TIEMPO:");
        System.out.println("   Salon: " + salon.getName());
        System.out.println("   Slot solicitado: " + start + " - " + end);
        System.out.println("   Horario salón: " + salon.getOpenTime() + " - " + salon.getCloseTime());

        // 🚀 ARREGLO: USAR atTime() EN LUGAR DE atDate()
        LocalDateTime salonOpen = start.toLocalDate().atTime(salon.getOpenTime());
        LocalDateTime salonClose = start.toLocalDate().atTime(salon.getCloseTime());

        System.out.println("   Horario completo del día: " + salonOpen + " - " + salonClose);

        // ✅ VALIDAR QUE EL BOOKING ESTÉ DENTRO DEL HORARIO DEL SALÓN
        if (start.isBefore(salonOpen)) {
            System.out.println("❌ Hora de inicio (" + start + ") es antes de apertura (" + salonOpen + ")");
            throw new Exception(
                    "Booking time must be within salon's open hours. Salon opens at " + salon.getOpenTime());
        }

        if (end.isAfter(salonClose)) {
            System.out.println("❌ Hora de fin (" + end + ") es después de cierre (" + salonClose + ")");
            throw new Exception(
                    "Booking time must be within salon's open hours. Salon closes at " + salon.getCloseTime());
        }

        System.out.println("✅ Horario válido - verificando disponibilidad...");

        // ✅ VERIFICAR QUE NO HAYA OVERLAP CON OTROS BOOKINGS
        List<Booking> existingBookings = getBookingsBySalon(salon.getId());
        System.out.println("   Bookings existentes: " + existingBookings.size());

        for (Booking existingBooking : existingBookings) {
            boolean overlap = start.isBefore(existingBooking.getEndTime()) &&
                    end.isAfter(existingBooking.getStartTime());

            boolean exactMatch = start.isEqual(existingBooking.getStartTime()) ||
                    end.isEqual(existingBooking.getEndTime());

            if (overlap || exactMatch) {
                System.out.println("❌ Conflicto con booking existente: " +
                        existingBooking.getStartTime() + " - " + existingBooking.getEndTime());
                throw new Exception("Slot not available, choose different time. Conflicts with existing booking.");
            }
        }

        System.out.println("✅ Slot disponible!");
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
