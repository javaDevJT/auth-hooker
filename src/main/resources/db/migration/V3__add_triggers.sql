-- V3: Database triggers for automatic timestamp updates
-- Creates triggers to automatically update 'updated_at' columns

-- ========================================
-- Create trigger function for updated_at
-- ========================================
CREATE OR REPLACE FUNCTION update_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ========================================
-- Apply triggers to all tables with updated_at
-- ========================================

CREATE TRIGGER tenants_updated_at
    BEFORE UPDATE ON tenants
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

CREATE TRIGGER providers_updated_at
    BEFORE UPDATE ON providers
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

CREATE TRIGGER claim_mappings_updated_at
    BEFORE UPDATE ON claim_mappings
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

CREATE TRIGGER platform_integrations_updated_at
    BEFORE UPDATE ON platform_integrations
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

CREATE TRIGGER users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

CREATE TRIGGER user_platform_mappings_updated_at
    BEFORE UPDATE ON user_platform_mappings
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

CREATE TRIGGER role_rules_updated_at
    BEFORE UPDATE ON role_rules
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

CREATE TRIGGER refresh_tokens_updated_at
    BEFORE UPDATE ON refresh_tokens
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

CREATE TRIGGER usage_metering_updated_at
    BEFORE UPDATE ON usage_metering
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

-- ========================================
-- Function to auto-expire verification sessions
-- ========================================
CREATE OR REPLACE FUNCTION cleanup_expired_sessions()
RETURNS void AS $$
BEGIN
    UPDATE verification_sessions
    SET status = 'expired'
    WHERE status = 'pending' 
      AND expires_at < CURRENT_TIMESTAMP;
END;
$$ LANGUAGE plpgsql;

-- Note: This function can be called manually or scheduled via pg_cron extension
COMMENT ON FUNCTION cleanup_expired_sessions() IS 'Updates expired verification sessions status. Should be run periodically.';
