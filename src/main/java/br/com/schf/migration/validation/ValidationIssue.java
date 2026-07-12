package br.com.schf.migration.validation;

public record ValidationIssue(String code, String file, Long line, String message) {
}
