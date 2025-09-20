package org.example.roadsimulation.dto;

import java.util.List;

public class BatchOperationResult<T> {
    private final List<T> successfulItems;
    private final List<BatchError> errors;

    public BatchOperationResult(List<T> successfulItems, List<BatchError> errors) {
        this.successfulItems = successfulItems;
        this.errors = errors;
    }

    public List<T> getSuccessfulItems() {
        return successfulItems;
    }

    public List<BatchError> getErrors() {
        return errors;
    }

    public int getTotalCount() {
        return (successfulItems != null ? successfulItems.size() : 0) +
                (errors != null ? errors.size() : 0);
    }

    public int getSuccessCount() {
        return successfulItems != null ? successfulItems.size() : 0;
    }

    public int getErrorCount() {
        return errors != null ? errors.size() : 0;
    }

    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }

    public boolean isAllSuccess() {
        return errors == null || errors.isEmpty();
    }

    public boolean isPartialSuccess() {
        return successfulItems != null && !successfulItems.isEmpty() &&
                errors != null && !errors.isEmpty();
    }

    public boolean isAllFailed() {
        return successfulItems != null && successfulItems.isEmpty() &&
                errors != null && !errors.isEmpty();
    }
}