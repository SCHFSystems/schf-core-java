package br.com.schf.migration.api;

import br.com.schf.migration.validation.BundleValidationException;
import br.com.schf.migration.validation.BundleValidationReport;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = MigrationController.class)
public class MigrationExceptionHandler {
    @ExceptionHandler(BundleValidationException.class)
    public ResponseEntity<BundleValidationReport> validation(BundleValidationException exception) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(new BundleValidationReport(false, null, 0, Map.of(), exception.getIssues()));
    }
}
