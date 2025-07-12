// =============================================================================
// BOOKING SERVICE - ChartController CORREGIDO
// backend/microservices/booking-service/src/main/java/com/zosh/controller/ChartController.java
// =============================================================================
package com.zosh.controller;

import com.zosh.modal.Booking;
import com.zosh.payload.dto.SalonDTO;
import com.zosh.service.BookingService;
import com.zosh.service.clients.SalonFeignClient;
import com.zosh.service.impl.BookingChartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Collections;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/bookings/chart")
public class ChartController {

    private final BookingChartService bookingChartService;
    private final BookingService bookingService;
    private final SalonFeignClient salonService;

    @GetMapping("/earnings")
    public ResponseEntity<List<Map<String, Object>>> getEarningsChartData(
            @RequestHeader("Authorization") String jwt) {

        System.out.println("📊 CHART CONTROLLER - EARNINGS REQUEST");

        try {
            ResponseEntity<SalonDTO> salonResponse = salonService.getSalonByOwner(jwt);
            SalonDTO salon = salonResponse.getBody();

            if (salon == null) {
                System.out.println("⚠️ Respuesta de salón es null");
                return ResponseEntity.ok(Collections.emptyList());
            }

            System.out.println("✅ Salón encontrado: " + salon.getName() + " (ID: " + salon.getId() + ")");

            List<Booking> bookings = bookingService.getBookingsBySalon(salon.getId());
            System.out.println("📊 Bookings encontrados para earnings: " + bookings.size());

            List<Map<String, Object>> chartData = bookingChartService.generateEarningsChartData(bookings);
            System.out.println("📈 Datos de earnings generados: " + chartData.size() + " puntos");

            return ResponseEntity.ok(chartData);

        } catch (feign.FeignException.NotFound e) {
            System.out.println("ℹ️ 404 - Usuario no tiene salón registrado");
            return ResponseEntity.ok(Collections.emptyList());
        } catch (Exception e) {
            System.err.println("❌ Error obteniendo datos de earnings: " + e.getMessage());
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    @GetMapping("/bookings")
    public ResponseEntity<List<Map<String, Object>>> getBookingsChartData(
            @RequestHeader("Authorization") String jwt) {

        System.out.println("📊 CHART CONTROLLER - BOOKINGS REQUEST");

        try {
            ResponseEntity<SalonDTO> salonResponse = salonService.getSalonByOwner(jwt);
            SalonDTO salon = salonResponse.getBody();

            if (salon == null) {
                System.out.println("⚠️ Respuesta de salón es null para bookings");
                return ResponseEntity.ok(Collections.emptyList());
            }

            System.out.println("✅ Salón encontrado para bookings: " + salon.getName() + " (ID: " + salon.getId() + ")");

            List<Booking> bookings = bookingService.getBookingsBySalon(salon.getId());
            System.out.println("📊 Bookings encontrados para gráfico: " + bookings.size());

            List<Map<String, Object>> chartData = bookingChartService.generateBookingCountChartData(bookings);
            System.out.println("📈 Datos de bookings generados: " + chartData.size() + " puntos");

            return ResponseEntity.ok(chartData);

        } catch (feign.FeignException.NotFound e) {
            System.out.println("ℹ️ 404 - Usuario no tiene salón registrado para bookings");
            return ResponseEntity.ok(Collections.emptyList());
        } catch (Exception e) {
            System.err.println("❌ Error obteniendo datos de bookings: " + e.getMessage());
            return ResponseEntity.ok(Collections.emptyList());
        }
    }
}