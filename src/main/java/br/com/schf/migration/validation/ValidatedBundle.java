package br.com.schf.migration.validation;

import br.com.schf.migration.domain.CanonicalBundle;

public record ValidatedBundle(CanonicalBundle bundle, BundleValidationReport report) {
}
