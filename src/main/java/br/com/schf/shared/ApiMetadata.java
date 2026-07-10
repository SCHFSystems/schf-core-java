package br.com.schf.shared;

import java.time.Instant;

public record ApiMetadata(String status, String system, String version, Instant timestamp) {
}
