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

        Map<String, BigDecimal> earningsByDay = bookings.stream()
                .collect(Collectors.groupingBy(
                        b -> b.getStartTime().toLocalDate().toString(),
                        Collectors.mapping(
                                Booking::getTotalPrice,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

        return convertToChartData(earningsByDay, "daily", "earnings");
    }

    /** Número de reservas confirmadas por día */
    public List<Map<String, Object>> generateBookingCountChartData(List<Booking> bookings) {

        Map<String, Long> countsByDay = bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                .collect(Collectors.groupingBy(
                        b -> b.getStartTime().toLocalDate().toString(),
                        Collectors.counting()));

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
