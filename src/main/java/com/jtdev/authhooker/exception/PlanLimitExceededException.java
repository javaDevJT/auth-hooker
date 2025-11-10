package com.jtdev.authhooker.exception;

/**
 * Exception thrown when a tenant plan limit is exceeded
 */
public class PlanLimitExceededException extends RuntimeException {
    
    private final String limitType;
    private final Object currentValue;
    private final Object maxValue;
    
    public PlanLimitExceededException(String limitType, Object currentValue, Object maxValue) {
        super(String.format("Plan limit exceeded for %s: current=%s, max=%s", 
                limitType, currentValue, maxValue));
        this.limitType = limitType;
        this.currentValue = currentValue;
        this.maxValue = maxValue;
    }
    
    public PlanLimitExceededException(String message) {
        super(message);
        this.limitType = null;
        this.currentValue = null;
        this.maxValue = null;
    }
    
    public String getLimitType() {
        return limitType;
    }
    
    public Object getCurrentValue() {
        return currentValue;
    }
    
    public Object getMaxValue() {
        return maxValue;
    }
}
