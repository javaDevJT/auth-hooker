-- V2: Performance indexes for query optimization
-- Adds indexes to improve common query patterns

-- ========================================
-- TENANTS - Indexes
-- ========================================
CREATE INDEX idx_tenants_status ON tenants(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_tenants_plan_tier ON tenants(plan_tier);
CREATE INDEX idx_tenants_stripe_customer ON tenants(stripe_customer_id);

-- ========================================
-- PROVIDERS - Indexes
-- ========================================
CREATE INDEX idx_providers_tenant ON providers(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_providers_type ON providers(provider_type);
CREATE INDEX idx_providers_active ON providers(tenant_id, is_active) WHERE deleted_at IS NULL;

-- ========================================
-- CLAIM_MAPPINGS - Indexes
-- ========================================
CREATE INDEX idx_claim_mappings_provider ON claim_mappings(provider_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_claim_mappings_priority ON claim_mappings(priority DESC);
CREATE INDEX idx_claim_mappings_active ON claim_mappings(provider_id, is_active) WHERE deleted_at IS NULL;

-- ========================================
-- PLATFORM_INTEGRATIONS - Indexes
-- ========================================
CREATE INDEX idx_platform_integrations_tenant ON platform_integrations(tenant_id);
CREATE INDEX idx_platform_integrations_type ON platform_integrations(platform_type);
CREATE INDEX idx_platform_integrations_active ON platform_integrations(tenant_id, is_active) WHERE deleted_at IS NULL;

-- ========================================
-- USERS - Indexes
-- ========================================
CREATE INDEX idx_users_tenant ON users(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_provider ON users(provider_id);
CREATE INDEX idx_users_subject ON users(subject);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_last_verified ON users(last_verified_at);
CREATE INDEX idx_users_tenant_active ON users(tenant_id, is_active) WHERE deleted_at IS NULL;

-- GIN indexes for JSONB queries on claims
CREATE INDEX idx_users_claims_gin ON users USING GIN (claims jsonb_path_ops);
CREATE INDEX idx_users_raw_claims_gin ON users USING GIN (raw_claims jsonb_path_ops);

-- Index for email domain queries (used by role rules)
CREATE INDEX idx_users_email_domain ON users((claims->>'email_domain')) WHERE deleted_at IS NULL;

-- ========================================
-- USER_PLATFORM_MAPPINGS - Indexes
-- ========================================
CREATE INDEX idx_user_platform_mappings_user ON user_platform_mappings(user_id);
CREATE INDEX idx_user_platform_mappings_platform_user 
    ON user_platform_mappings(platform_type, platform_user_id)
    WHERE unlinked_at IS NULL;
CREATE INDEX idx_user_platform_mappings_integration 
    ON user_platform_mappings(platform_integration_id);

-- Index for finding users needing role sync
CREATE INDEX idx_user_platform_mappings_sync_needed 
    ON user_platform_mappings(platform_integration_id, last_role_sync_at)
    WHERE is_active = true AND unlinked_at IS NULL;

-- ========================================
-- ROLE_RULES - Indexes
-- ========================================
CREATE INDEX idx_role_rules_tenant ON role_rules(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_role_rules_platform ON role_rules(platform_integration_id);
CREATE INDEX idx_role_rules_priority ON role_rules(priority DESC);
CREATE INDEX idx_role_rules_active ON role_rules(tenant_id, is_active) WHERE deleted_at IS NULL;

-- GIN index for JSONB rule_set queries
CREATE INDEX idx_role_rules_rule_set_gin ON role_rules USING GIN (rule_set jsonb_path_ops);

-- ========================================
-- REFRESH_TOKENS - Indexes
-- ========================================
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_provider ON refresh_tokens(provider_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens(expires_at)
    WHERE revoked_at IS NULL;

-- ========================================
-- AUDIT_LOGS - Indexes
-- ========================================
CREATE INDEX idx_audit_logs_tenant_timestamp ON audit_logs(tenant_id, timestamp DESC);
CREATE INDEX idx_audit_logs_user ON audit_logs(user_id, timestamp DESC);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_audit_logs_timestamp ON audit_logs(timestamp DESC);
CREATE INDEX idx_audit_logs_resource ON audit_logs(resource_type, resource_id);

-- ========================================
-- USAGE_METERING - Indexes
-- ========================================
CREATE INDEX idx_usage_metering_tenant_month ON usage_metering(tenant_id, billing_month DESC);
CREATE INDEX idx_usage_metering_unreported ON usage_metering(billing_month)
    WHERE reported_to_stripe = false;

-- ========================================
-- VERIFICATION_SESSIONS - Indexes
-- ========================================
CREATE INDEX idx_verification_sessions_state ON verification_sessions(state_token);
CREATE INDEX idx_verification_sessions_expires ON verification_sessions(expires_at)
    WHERE status = 'pending';
CREATE INDEX idx_verification_sessions_tenant ON verification_sessions(tenant_id);
CREATE INDEX idx_verification_sessions_provider ON verification_sessions(provider_id);
