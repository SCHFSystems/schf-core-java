package br.com.schf.migration.validation;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record ValidationIssue(String code, String file, Long line, String message, String severity) {
    public static final String ERROR = "ERROR";
    public static final String WARNING = "WARNING";

    public ValidationIssue(String code, String file, Long line, String message) {
        this(code, file, line, message, ERROR);
    }

    @JsonIgnore
    public boolean isError() { return ERROR.equals(severity); }

    @JsonIgnore
    public boolean isWarning() { return WARNING.equals(severity); }
}
