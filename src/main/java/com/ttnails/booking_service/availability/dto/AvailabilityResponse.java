package com.ttnails.booking_service.availability.dto;

import java.util.List;

/**
 * Response của {@code GET /availability} — contract 5.1.
 *
 * <pre>
 * {
 *   "date": "2026-11-20",
 *   "timezone": "Europe/Berlin",
 *   "slotStepMinutes": 30,
 *   "totalDurationMinutes": 105,
 *   "staff": [ { "staffId": 1, "name": "Minh", "slots": [ {"start":"09:00","end":"10:45"} ] } ],
 *   "anyAvailable": ["09:00", "10:30"]
 * }
 * </pre>
 */
public record AvailabilityResponse(String date,
                                   String timezone,
                                   int slotStepMinutes,
                                   int totalDurationMinutes,
                                   List<StaffSlotsDTO> staff,
                                   List<String> anyAvailable) {
}
