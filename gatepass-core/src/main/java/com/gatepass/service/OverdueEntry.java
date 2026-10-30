package com.gatepass.service;

import com.gatepass.model.GatePass;
import java.time.Duration;

/** A student who has left campus and is past the expected return time, and by how much. */
public record OverdueEntry(GatePass pass, Duration overdueBy) {
}
