package com.zosh.controller;

import com.zosh.domain.BookingStatus;
import com.zosh.domain.PaymentMethod;
import com.zosh.exception.UserException;
import com.zosh.mapper.BookingMapper;
import com.zosh.modal.*;
import com.zosh.payload.dto.*;
import com.zosh.payload.request.BookingRequest;
import com.zosh.payload.response.PaymentLinkResponse;
import com.zosh.service.*;
import com.zosh.service.clients.PaymentFeignClient;
import com.zosh.service.clients.SalonFeignClient;
import com.zosh.service.clients.ServiceOfferingFeignClient;
import com.zosh.service.clients.UserFeignClient;
import jakarta.ws.rs.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

        private final BookingService bookingService;
        private final UserFeignClient userService;
        private final SalonFeignClient salonService;
        private final ServiceOfferingFeignClient serviceOfferingService;
        private final PaymentFeignClient paymentService;
        private final UserFeignClient userFeignClient;

        @PostMapping
        public ResponseEntity<PaymentLinkResponse> createBooking(
                        @RequestHeader("Authorization") String jwt,
                        @RequestParam Long salonId,
                        @RequestParam PaymentMethod paymentMethod,
                        @RequestBody BookingRequest bookingRequest) throws Exception {

                UserDTO user = userService.getUserFromJwtToken(jwt).getBody();

                SalonDTO salon = salonService.getSalonById(salonId, jwt).getBody();

                if (salon.getId() == null) {
                        throw new Exception("Salon not found");
                }

                Set<ServiceOfferingDTO> services = serviceOfferingService
                                .getServicesByIds(bookingRequest.getServiceIds()).getBody();

                Booking createdBooking = bookingService.createBooking(
                                bookingRequest,
                                user,
                                salon,
                                services);
                PaymentLinkResponse res = paymentService.createPaymentLink(
                                jwt,
                                createdBooking,
                                paymentMethod).getBody();

                return new ResponseEntity<>(res, HttpStatus.CREATED);

        }

        /**
         * Get all bookings for a customer
         */
        @GetMapping("/customer")
        public ResponseEntity<Map<String, Object>> getBookingsByCustomer(
                        @RequestHeader("Authorization") String jwt,
                        @RequestHeader(value = "X-Cognito-Sub", required = false) String cognitoSub,
                        @RequestHeader(value = "X-User-Email", required = false) String userEmail,
                        @RequestHeader(value = "X-User-Username", required = false) String username,
                        @RequestHeader(value = "X-User-Role", required = false) String userRole,
                        @RequestHeader(value = "X-Auth-Source", required = false) String authSource) throws Exception {

                try {
                        UserDTO user = userService.getUserFromJwtToken(jwt).getBody();
                        List<Booking> bookings = bookingService.getBookingsByCustomer(user.getId());
                        Set<BookingDTO> bookingDTOs = getBookingDTOs(bookings, jwt); // ✅ PASAR JWT

                        Map<String, Object> response = new HashMap<>();
                        response.put("bookings", new ArrayList<>(bookingDTOs));
                        response.put("totalBookings", bookingDTOs.size());

                        return ResponseEntity.ok(response);

                } catch (Exception e) {
                        System.err.println("❌ Error obteniendo bookings: " + e.getMessage());

                        Map<String, Object> errorResponse = new HashMap<>();
                        errorResponse.put("bookings", Collections.emptyList());
                        errorResponse.put("error", e.getMessage());

                        return ResponseEntity.ok(errorResponse);
                }
        }

        @GetMapping("/report")
        public ResponseEntity<SalonReport> getSalonReport(
                        @RequestHeader("Authorization") String jwt) {

                System.out.println("📊 BOOKING CONTROLLER - getSalonReport");

                try {
                        // 🚀 OBTENER USUARIO (método original)
                        UserDTO user = userService.getUserFromJwtToken(jwt).getBody();

                        if (user == null) {
                                System.out.println("❌ Usuario no encontrado");
                                return createEmptyReportResponse();
                        }

                        System.out.println("👤 Usuario encontrado: " + user.getEmail());

                        // 🚀 OBTENER SALÓN (método original)
                        SalonDTO salon = salonService.getSalonByOwner(jwt).getBody();

                        if (salon == null) {
                                System.out.println("❌ Salón no encontrado");
                                return createEmptyReportResponse();
                        }

                        System.out.println("🏪 Salón encontrado: " + salon.getName());

                        // 🚀 GENERAR REPORTE
                        SalonReport report = bookingService.getSalonReport(salon.getId());

                        return ResponseEntity.ok(report);

                } catch (Exception e) {
                        System.err.println("❌ Error obteniendo reporte: " + e.getMessage());

                        // 🚀 MANEJAR ERRORES ESPECÍFICOS
                        String errorMsg = e.getMessage().toLowerCase();
                        if (errorMsg.contains("404") || errorMsg.contains("not found") ||
                                        errorMsg.contains("no salon") || errorMsg.contains("usuario no encontrado")) {
                                System.out.println("ℹ️ Usuario no tiene salón - retornando reporte vacío");
                        }

                        return createEmptyReportResponse();
                }
        }

        // 🚀 MÉTODO HELPER PARA CREAR REPORTE VACÍO
        private ResponseEntity<SalonReport> createEmptyReportResponse() {
                SalonReport emptyReport = new SalonReport();
                emptyReport.setTotalEarnings(java.math.BigDecimal.ZERO);
                emptyReport.setTotalBookings(0);
                emptyReport.setCancelledBookings(0);
                emptyReport.setTotalRefund(java.math.BigDecimal.ZERO);
                return ResponseEntity.ok(emptyReport);
        }

        // 🚀 TAMBIÉN ACTUALIZA EL MÉTODO /salon PARA CONSISTENCIA
        @GetMapping("/salon")
        public ResponseEntity<Set<BookingDTO>> getBookingsBySalon(
                        @RequestHeader("Authorization") String jwt) {

                System.out.println("📊 BOOKING CONTROLLER - getBookingsBySalon");

                try {
                        UserDTO user = userService.getUserFromJwtToken(jwt).getBody();

                        if (user == null) {
                                System.out.println("❌ Usuario no encontrado");
                                return ResponseEntity.ok(java.util.Collections.emptySet());
                        }

                        SalonDTO salon = salonService.getSalonByOwner(jwt).getBody();

                        if (salon == null) {
                                System.out.println("❌ Salón no encontrado");
                                return ResponseEntity.ok(java.util.Collections.emptySet());
                        }

                        List<Booking> bookings = bookingService.getBookingsBySalon(salon.getId());

                        return ResponseEntity.ok(getBookingDTOs(bookings, jwt)); // ✅ PASAR JWT

                } catch (Exception e) {
                        System.err.println("❌ Error obteniendo bookings del salón: " + e.getMessage());
                        return ResponseEntity.ok(java.util.Collections.emptySet());
                }
        }

        private Set<BookingDTO> getBookingDTOs(List<Booking> bookings, String jwt) { // ✅ AGREGAR JWT PARAMETER

                return bookings.stream()
                                .map(booking -> {
                                        UserDTO user;
                                        Set<ServiceOfferingDTO> offeringDTOS = serviceOfferingService
                                                        .getServicesByIds(booking.getServiceIds()).getBody();

                                        SalonDTO salonDTO;
                                        try {
                                                salonDTO = salonService.getSalonById(booking.getSalonId(), jwt)
                                                                .getBody(); // ✅ AHORA FUNCIONA

                                                user = userFeignClient.getUserById(booking.getCustomerId()).getBody();
                                        } catch (Exception e) {
                                                throw new RuntimeException(e);
                                        }

                                        return BookingMapper.toDTO(
                                                        booking,
                                                        offeringDTOS,
                                                        salonDTO, user);
                                })
                                .collect(Collectors.toSet());
        }

        /**
         * Get a booking by its ID
         */
        @GetMapping("/{bookingId}")
        public ResponseEntity<BookingDTO> getBookingById(@PathVariable Long bookingId) {
                Booking booking = bookingService.getBookingById(bookingId);
                Set<ServiceOfferingDTO> offeringDTOS = serviceOfferingService
                                .getServicesByIds(booking.getServiceIds()).getBody();

                BookingDTO bookingDTO = BookingMapper.toDTO(booking,
                                offeringDTOS, null, null);

                return ResponseEntity.ok(bookingDTO);

        }

        /**
         * Update the status of a booking
         */
        @PutMapping("/{bookingId}/status")
        public ResponseEntity<BookingDTO> updateBookingStatus(
                        @PathVariable Long bookingId,
                        @RequestParam BookingStatus status,
                        @RequestHeader("Authorization") String jwt) throws Exception { // ✅ AGREGAR ESTE PARÁMETRO

                Booking updatedBooking = bookingService.updateBookingStatus(bookingId, status);

                Set<ServiceOfferingDTO> offeringDTOS = serviceOfferingService
                                .getServicesByIds(updatedBooking.getServiceIds()).getBody();

                SalonDTO salonDTO;
                try {
                        salonDTO = salonService.getSalonById(updatedBooking.getSalonId(), jwt).getBody(); // ✅ AHORA SÍ
                                                                                                          // FUNCIONA
                } catch (Exception e) {
                        throw new RuntimeException(e);
                }

                BookingDTO bookingDTO = BookingMapper.toDTO(updatedBooking, offeringDTOS, salonDTO, null);

                return new ResponseEntity<>(bookingDTO, HttpStatus.OK);
        }

        @GetMapping("/slots/salon/{salonId}/date/{date}")
        public ResponseEntity<List<BookedSlotsDTO>> getBookedSlots(
                        @PathVariable Long salonId,
                        @PathVariable LocalDate date,
                        @RequestHeader("Authorization") String jwt) throws Exception {

                List<Booking> bookings = bookingService.getBookingsByDate(date, salonId);

                List<BookedSlotsDTO> slotsDTOS = bookings.stream()
                                .map(booking -> {
                                        BookedSlotsDTO slotDto = new BookedSlotsDTO();

                                        slotDto.setStartTime(booking.getStartTime());
                                        slotDto.setEndTime(booking.getEndTime());

                                        return slotDto;
                                })
                                .toList();

                return ResponseEntity.ok(slotsDTOS);

        }
}
