package br.com.schf.system;

public record SystemInfoResponse(
    String productName,
    String instanceName,
    String instanceId,
    String apiVersion,
    String serverVersion,
    String environment,
    boolean setupRequired
) {
}
