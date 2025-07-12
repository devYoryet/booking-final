package com.zosh.service.impl;

import com.zosh.domain.BookingStatus;
import com.zosh.modal.Booking;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BookingChartService {

    /** Ingresos diarios (agrupados) */
    public List<Map<String, Object>> generateEarningsChartData(List<Booking> bookings) {
        System.out.println("📊 EARNINGS CHART - Total bookings recibidos: " + bookings.size());

        // Solo excluir CANCELLED para earnings
        List<Booking> validBookings = bookings.stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                .collect(Collectors.toList());

        System.out.println("📊 EARNINGS CHART - Bookings válidos (no cancelados): " + validBookings.size());

        Map<String, BigDecimal> earningsByDay = validBookings.stream()
                .collect(Collectors.groupingBy(
                        b -> b.getStartTime().toLocalDate().toString(),
                        Collectors.mapping(
                                Booking::getTotalPrice,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

        System.out.println("📊 EARNINGS CHART - Días con ingresos: " + earningsByDay.size());
        earningsByDay.forEach((date, amount) -> System.out.println("   " + date + ": $" + amount));

        return convertToChartData(earningsByDay, "daily", "earnings");
    }

    /** Número de reservas confirmadas por día */
    public List<Map<String, Object>> generateBookingCountChartData(List<Booking> bookings) {
        System.out.println("📊 BOOKING COUNT CHART - Total bookings recibidos: " + bookings.size());

        // Incluir PENDING, CONFIRMED, SUCCESS - solo excluir CANCELLED
        List<Booking> validBookings = bookings.stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                .collect(Collectors.toList());

        System.out.println("📊 BOOKING COUNT CHART - Bookings válidos: " + validBookings.size());

        // Debug: mostrar estados de las reservas
        Map<BookingStatus, Long> statusCounts = bookings.stream()
                .collect(Collectors.groupingBy(Booking::getStatus, Collectors.counting()));

        System.out.println("📊 BOOKING COUNT CHART - Estados de reservas:");
        statusCounts.forEach((status, count) -> System.out.println("   " + status + ": " + count));

        Map<String, Long> countsByDay = validBookings.stream()
                .collect(Collectors.groupingBy(
                        b -> b.getStartTime().toLocalDate().toString(),
                        Collectors.counting()));

        System.out.println("📊 BOOKING COUNT CHART - Días con reservas: " + countsByDay.size());
        countsByDay.forEach((date, count) -> System.out.println("   " + date + ": " + count + " reservas"));

        return convertToChartData(countsByDay, "daily", "count");
    }

    /** Conversión genérica a formato de gráfica */
    private <T> List<Map<String, Object>> convertToChartData(
            Map<String, T> grouped, String period, String key) {

        List<Map<String, Object>> data = new ArrayList<>();
        grouped.forEach((date, value) -> {
            Map<String, Object> point = new HashMap<>();
            point.put(period, date);
            point.put(key, value);
            data.add(point);
        });
        data.sort(Comparator.comparing(dp -> dp.get(period).toString()));
        return data;
    }
}
