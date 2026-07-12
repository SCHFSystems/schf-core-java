package br.com.schf.system;

import java.util.List;

public record CapabilitiesResponse(
    String productName,
    String apiVersion,
    List<String> features,
    boolean setupRequired
) {
}
