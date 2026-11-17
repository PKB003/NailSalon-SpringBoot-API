package com.ttnails.booking_service.availability.dto;

import java.util.List;

/** Slot còn trống của một thợ (contract 5.1). */
public record StaffSlotsDTO(Long staffId, String name, List<SlotDTO> slots) {
}
