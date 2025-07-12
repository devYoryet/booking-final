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

        @GetMapping("/customer")
        public ResponseEntity<Map<String, Object>> getBookingsByCustomer(
                        @RequestHeader("Authorization") String jwt,
                        @RequestHeader(value = "X-Cognito-Sub", required = false) String cognitoSub,
                        @RequestHeader(value = "X-User-Email", required = false) String userEmail,
                        @RequestHeader(value = "X-User-Username", required = false) String username,
                        @RequestHeader(value = "X-User-Role", required = false) String userRole,
                        @RequestHeader(value = "X-Auth-Source", required = false) String authSource) throws Exception {

                System.out.println("📅 BOOKING CONTROLLER - getBookingsByCustomer");

                try {
                        // 🚀 OBTENER USUARIO
                        UserDTO user = userService.getUserFromJwtToken(jwt).getBody();

                        if (user == null) {
                                System.out.println("❌ Usuario no encontrado");
                                Map<String, Object> errorResponse = new HashMap<>();
                                errorResponse.put("bookings", Collections.emptyList());
                                errorResponse.put("totalBookings", 0);
                                errorResponse.put("error", "Usuario no encontrado");
                                return ResponseEntity.ok(errorResponse);
                        }

                        System.out.println("👤 Usuario: " + user.getEmail() + " (ID: " + user.getId() + ")");

                        // 🚀 OBTENER BOOKINGS DEL CUSTOMER (NO DEL SALÓN)
                        List<Booking> bookings = bookingService.getBookingsByCustomer(user.getId());

                        System.out.println("📋 Bookings encontrados: " + bookings.size());

                        // 🚀 CONVERTIR A DTO - PASAR JWT
                        Set<BookingDTO> bookingDTOs = getBookingDTOs(bookings, jwt);

                        // 🚀 RESPUESTA CON ESTRUCTURA CORRECTA
                        Map<String, Object> response = new HashMap<>();
                        response.put("bookings", new ArrayList<>(bookingDTOs));
                        response.put("totalBookings", bookingDTOs.size());

                        System.out.println("✅ Respuesta preparada con " + bookingDTOs.size() + " bookings");

                        return ResponseEntity.ok(response);

                } catch (Exception e) {
                        System.err.println("❌ Error obteniendo bookings del customer: " + e.getMessage());
                        e.printStackTrace();

                        Map<String, Object> errorResponse = new HashMap<>();
                        errorResponse.put("bookings", Collections.emptyList());
                        errorResponse.put("totalBookings", 0);
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

                System.out.println("🔥 BOOKING CONTROLLER - getBookingsBySalon INICIADO");
                System.out.println("   JWT recibido: " + (jwt != null ? "SÍ" : "NO"));

                try {
                        // 1. Obtener usuario del JWT
                        System.out.println("📝 Paso 1: Obteniendo usuario del JWT...");
                        UserDTO user = userService.getUserFromJwtToken(jwt).getBody();

                        if (user == null) {
                                System.out.println("❌ Usuario no encontrado del JWT");
                                return ResponseEntity.ok(java.util.Collections.emptySet());
                        }

                        System.out.println("✅ Usuario encontrado:");
                        System.out.println("   ID: " + user.getId());
                        System.out.println("   Email: " + user.getEmail());

                        // 2. Obtener salón del usuario
                        System.out.println("📝 Paso 2: Obteniendo salón del usuario...");
                        ResponseEntity<SalonDTO> salonResponse = salonService.getSalonByOwner(jwt);
                        SalonDTO salon = salonResponse.getBody();

                        if (salon == null) {
                                System.out.println("❌ Salón no encontrado para el usuario");
                                System.out.println("   Response status: " + salonResponse.getStatusCode());
                                return ResponseEntity.ok(java.util.Collections.emptySet());
                        }

                        System.out.println("✅ Salón encontrado:");
                        System.out.println("   Salon ID: " + salon.getId());
                        System.out.println("   Salon Name: " + salon.getName());
                        System.out.println("   Owner ID: " + salon.getOwnerId());

                        // 3. Obtener bookings del salón
                        System.out.println("📝 Paso 3: Obteniendo bookings del salón...");
                        List<Booking> bookings = bookingService.getBookingsBySalon(salon.getId());

                        System.out.println("✅ Bookings encontrados:");
                        System.out.println("   Total bookings: " + bookings.size());

                        if (bookings.isEmpty()) {
                                System.out.println("⚠️  No hay bookings en la base de datos para salonId: "
                                                + salon.getId());
                        } else {
                                System.out.println("📋 Detalles de bookings:");
                                for (int i = 0; i < Math.min(bookings.size(), 3); i++) {
                                        Booking b = bookings.get(i);
                                        System.out.println("   Booking " + (i + 1) + ":");
                                        System.out.println("     ID: " + b.getId());
                                        System.out.println("     SalonId: " + b.getSalonId());
                                        System.out.println("     CustomerId: " + b.getCustomerId());
                                        System.out.println("     Status: " + b.getStatus());
                                        System.out.println("     StartTime: " + b.getStartTime());
                                        System.out.println("     Price: " + b.getTotalPrice());
                                }
                        }

                        // 4. Convertir a DTOs
                        System.out.println("📝 Paso 4: Convirtiendo a DTOs...");
                        Set<BookingDTO> bookingDTOs = getBookingDTOs(bookings, jwt);

                        System.out.println("✅ DTOs creados:");
                        System.out.println("   Total DTOs: " + bookingDTOs.size());

                        return ResponseEntity.ok(bookingDTOs);

                } catch (feign.FeignException.NotFound e) {
                        System.out.println("❌ FeignException.NotFound: " + e.getMessage());
                        System.out.println("   Probablemente el usuario no tiene salón registrado");
                        return ResponseEntity.ok(java.util.Collections.emptySet());
                } catch (Exception e) {
                        System.err.println("❌ Error general en getBookingsBySalon:");
                        System.err.println("   Error type: " + e.getClass().getSimpleName());
                        System.err.println("   Error message: " + e.getMessage());
                        e.printStackTrace();
                        return ResponseEntity.ok(java.util.Collections.emptySet());
                }
        }

       private Set<BookingDTO> getBookingDTOs(List<Booking> bookings, String jwt) {
                System.out.println("🔄 Convirtiendo " + bookings.size() + " bookings a DTOs...");
                
                return bookings.stream()
                        .map(booking -> {
                                try {
                                // Obtener servicios
                                Set<ServiceOfferingDTO> services = serviceOfferingService
                                        .getServicesByIds(booking.getServiceIds()).getBody();

                                // Obtener salón
                                SalonDTO salon = salonService.getSalonById(booking.getSalonId(), jwt).getBody();

                                // Crear DTO SIN usuario (temporal para evitar error de BD)
                                BookingDTO dto = BookingMapper.toDTO(booking, services, salon, null);
                                
                                System.out.println("✅ DTO creado para booking ID: " + booking.getId());
                                return dto;
                                
                                } catch (Exception e) {
                                System.err.println("❌ Error creando DTO para booking " + booking.getId() + ": " + e.getMessage());
                                // Crear DTO mínimo
                                BookingDTO dto = new BookingDTO();
                                dto.setId(booking.getId());
                                dto.setStatus(booking.getStatus());
                                dto.setTotalPrice(booking.getTotalPrice());
                                dto.setStartTime(booking.getStartTime());
                                dto.setEndTime(booking.getEndTime());
                                return dto;
                                }
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
