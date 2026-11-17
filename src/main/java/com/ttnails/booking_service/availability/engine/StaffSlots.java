package com.ttnails.booking_service.availability.engine;

import java.util.List;

/** Slot khả dụng của một thợ trong ngày. */
public record StaffSlots(Long staffId, String displayName, List<SlotOption> slots) {

    public StaffSlots {
        slots = slots == null ? List.of() : List.copyOf(slots);
    }

    public boolean hasSlots() {
        return !slots.isEmpty();
    }
}
