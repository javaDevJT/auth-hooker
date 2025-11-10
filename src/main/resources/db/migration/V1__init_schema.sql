-- V1: Initial schema for Unified Identity Bridge (UIB)
-- Creates all core tables for multi-tenant OAuth identity verification

-- ========================================
-- 1. TENANTS
-- ========================================
CREATE TABLE tenants (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(255) NOT NULL,
    subdomain           VARCHAR(63) UNIQUE,
    plan_tier           VARCHAR(50) NOT NULL DEFAULT 'free',
    status              VARCHAR(50) NOT NULL DEFAULT 'active',
    max_verified_users  INTEGER NOT NULL DEFAULT 50,
    owner_email         VARCHAR(255) NOT NULL,
    owner_name          VARCHAR(255),
    
    -- Billing
    stripe_customer_id  VARCHAR(255) UNIQUE,
    stripe_subscription_id VARCHAR(255),
    
    -- Settings (JSONB)
    settings            JSONB NOT NULL DEFAULT '{}',
    
    -- Metadata
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at          TIMESTAMP,
    
    CONSTRAINT chk_plan_tier CHECK (plan_tier IN ('free', 'starter', 'professional', 'enterprise')),
    CONSTRAINT chk_status CHECK (status IN ('active', 'suspended', 'cancelled', 'pending'))
);

-- ========================================
-- 2. PROVIDERS
-- ========================================
CREATE TABLE providers (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    provider_type       VARCHAR(50) NOT NULL,
    name                VARCHAR(255) NOT NULL,
    
    -- OAuth/OIDC Configuration
    client_id           VARCHAR(512) NOT NULL,
    client_secret_encrypted TEXT NOT NULL,
    
    -- Provider-specific config (JSONB)
    config              JSONB NOT NULL,
    
    -- Status
    is_active           BOOLEAN NOT NULL DEFAULT true,
    is_primary          BOOLEAN NOT NULL DEFAULT false,
    
    -- Metadata
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at          TIMESTAMP,
    
    CONSTRAINT chk_provider_type CHECK (provider_type IN (
        'google', 'microsoft', 'github', 'discord', 'battlenet', 'oidc_custom'
    ))
);

-- Only one primary provider per tenant
CREATE UNIQUE INDEX uq_tenant_primary_provider 
    ON providers(tenant_id, is_primary) 
    WHERE is_primary = true AND deleted_at IS NULL;

-- ========================================
-- 3. CLAIM_MAPPINGS
-- ========================================
CREATE TABLE claim_mappings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id         UUID NOT NULL REFERENCES providers(id) ON DELETE CASCADE,
    
    -- Mapping definition
    name                VARCHAR(255) NOT NULL,
    description         TEXT,
    
    -- Source claim path (JSONPath expression)
    source_path         VARCHAR(500) NOT NULL,
    
    -- Target field in normalized claims
    target_field        VARCHAR(255) NOT NULL,
    
    -- Transformation rules (JSONB)
    transform           JSONB NOT NULL DEFAULT '{}',
    
    -- Priority (for conflicting mappings)
    priority            INTEGER NOT NULL DEFAULT 0,
    
    -- Status
    is_active           BOOLEAN NOT NULL DEFAULT true,
    
    -- Metadata
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at          TIMESTAMP
);

-- One mapping per target field per provider
CREATE UNIQUE INDEX uq_provider_target_field 
    ON claim_mappings(provider_id, target_field) 
    WHERE deleted_at IS NULL AND is_active = true;

-- ========================================
-- 4. PLATFORM_INTEGRATIONS
-- ========================================
CREATE TABLE platform_integrations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    platform_type       VARCHAR(50) NOT NULL,
    
    -- Platform-specific identifiers
    platform_id         VARCHAR(255) NOT NULL,
    platform_name       VARCHAR(255) NOT NULL,
    
    -- Configuration (JSONB)
    config              JSONB NOT NULL,
    
    -- API Key for bot/plugin authentication
    api_key_hash        VARCHAR(255) NOT NULL,
    
    -- Status
    is_active           BOOLEAN NOT NULL DEFAULT true,
    last_sync_at        TIMESTAMP,
    
    -- Metadata
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at          TIMESTAMP,
    
    CONSTRAINT chk_platform_type CHECK (platform_type IN (
        'discord', 'minecraft', 'teamspeak', 'slack'
    ))
);

-- One integration per platform per tenant
CREATE UNIQUE INDEX uq_tenant_platform 
    ON platform_integrations(tenant_id, platform_type, platform_id) 
    WHERE deleted_at IS NULL;

-- ========================================
-- 5. USERS
-- ========================================
CREATE TABLE users (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    provider_id         UUID NOT NULL REFERENCES providers(id) ON DELETE CASCADE,
    
    -- Identity
    subject             VARCHAR(255) NOT NULL,
    email               VARCHAR(255),
    email_verified      BOOLEAN DEFAULT false,
    
    -- Claims data (JSONB)
    raw_claims          JSONB NOT NULL,
    claims              JSONB NOT NULL,
    
    -- Verification
    verified_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_verified_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verification_count  INTEGER NOT NULL DEFAULT 1,
    
    -- Status
    is_active           BOOLEAN NOT NULL DEFAULT true,
    
    -- Metadata
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at          TIMESTAMP
);

-- Unique identity per provider
CREATE UNIQUE INDEX uq_tenant_provider_subject 
    ON users(tenant_id, provider_id, subject) 
    WHERE deleted_at IS NULL;

-- ========================================
-- 6. USER_PLATFORM_MAPPINGS
-- ========================================
CREATE TABLE user_platform_mappings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform_integration_id UUID NOT NULL REFERENCES platform_integrations(id) ON DELETE CASCADE,
    
    -- Platform identity
    platform_type       VARCHAR(50) NOT NULL,
    platform_user_id    VARCHAR(255) NOT NULL,
    platform_username   VARCHAR(255),
    
    -- Role sync state (JSONB)
    current_roles       JSONB NOT NULL DEFAULT '[]',
    last_role_sync_at   TIMESTAMP,
    pending_role_changes JSONB DEFAULT '{}',
    
    -- Status
    is_active           BOOLEAN NOT NULL DEFAULT true,
    
    -- Metadata
    linked_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    unlinked_at         TIMESTAMP,
    
    CONSTRAINT chk_upm_platform_type CHECK (platform_type IN (
        'discord', 'minecraft', 'teamspeak', 'slack'
    ))
);

-- One platform account per user per platform type
CREATE UNIQUE INDEX uq_user_platform 
    ON user_platform_mappings(user_id, platform_type) 
    WHERE unlinked_at IS NULL;

-- Platform account can only be linked to one verified user
CREATE UNIQUE INDEX uq_platform_user 
    ON user_platform_mappings(platform_type, platform_user_id) 
    WHERE unlinked_at IS NULL;

-- ========================================
-- 7. ROLE_RULES
-- ========================================
CREATE TABLE role_rules (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    platform_integration_id UUID REFERENCES platform_integrations(id) ON DELETE CASCADE,
    
    -- Rule definition
    name                VARCHAR(255) NOT NULL,
    description         TEXT,
    rule_set            JSONB NOT NULL,
    
    -- Priority for conflict resolution (higher = more important)
    priority            INTEGER NOT NULL DEFAULT 0,
    
    -- Status
    is_active           BOOLEAN NOT NULL DEFAULT true,
    
    -- Metadata
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at          TIMESTAMP
);

-- ========================================
-- 8. REFRESH_TOKENS
-- ========================================
CREATE TABLE refresh_tokens (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider_id         UUID NOT NULL REFERENCES providers(id) ON DELETE CASCADE,
    
    -- Token data
    encrypted_token     TEXT NOT NULL,
    token_type          VARCHAR(50) DEFAULT 'refresh',
    scope               VARCHAR(512),
    
    -- Expiration
    expires_at          TIMESTAMP,
    
    -- Rotation tracking
    rotation_count      INTEGER NOT NULL DEFAULT 0,
    last_rotated_at     TIMESTAMP,
    
    -- Metadata
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at          TIMESTAMP
);

-- One active refresh token per user per provider
CREATE UNIQUE INDEX uq_user_provider_active_token 
    ON refresh_tokens(user_id, provider_id) 
    WHERE revoked_at IS NULL;

-- ========================================
-- 9. AUDIT_LOGS
-- ========================================
CREATE TABLE audit_logs (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           UUID REFERENCES tenants(id) ON DELETE CASCADE,
    user_id             UUID REFERENCES users(id) ON DELETE SET NULL,
    
    -- Actor (who performed the action)
    actor_type          VARCHAR(50) NOT NULL,
    actor_id            VARCHAR(255),
    
    -- Action details
    action              VARCHAR(100) NOT NULL,
    resource_type       VARCHAR(100),
    resource_id         VARCHAR(255),
    
    -- Details (JSONB)
    details             JSONB NOT NULL DEFAULT '{}',
    
    -- Request context
    ip_address          INET,
    user_agent          TEXT,
    
    -- Metadata
    timestamp           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_actor_type CHECK (actor_type IN ('admin', 'user', 'system', 'bot', 'api'))
);

-- ========================================
-- 10. USAGE_METERING
-- ========================================
CREATE TABLE usage_metering (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    
    -- Billing period
    billing_month       DATE NOT NULL,
    
    -- Metrics
    active_verified_users INTEGER NOT NULL DEFAULT 0,
    total_verifications   INTEGER NOT NULL DEFAULT 0,
    total_role_syncs      INTEGER NOT NULL DEFAULT 0,
    
    -- Calculated billing
    metered_amount      DECIMAL(10, 2),
    currency            VARCHAR(3) DEFAULT 'USD',
    
    -- Status
    reported_to_stripe  BOOLEAN DEFAULT false,
    reported_at         TIMESTAMP,
    
    -- Metadata
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- One record per tenant per month
CREATE UNIQUE INDEX uq_tenant_billing_month 
    ON usage_metering(tenant_id, billing_month);

-- ========================================
-- 11. VERIFICATION_SESSIONS
-- ========================================
CREATE TABLE verification_sessions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    provider_id         UUID NOT NULL REFERENCES providers(id) ON DELETE CASCADE,
    
    -- OAuth state
    state_token         VARCHAR(255) NOT NULL UNIQUE,
    code_verifier       VARCHAR(255),
    nonce               VARCHAR(255),
    
    -- Context
    platform_type       VARCHAR(50),
    platform_user_id    VARCHAR(255),
    redirect_url        TEXT,
    
    -- Session data (JSONB)
    session_data        JSONB DEFAULT '{}',
    
    -- Status
    status              VARCHAR(50) NOT NULL DEFAULT 'pending',
    completed_at        TIMESTAMP,
    
    -- Expiration
    expires_at          TIMESTAMP NOT NULL,
    
    -- Metadata
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_session_status CHECK (status IN ('pending', 'completed', 'failed', 'expired'))
);

-- ========================================
-- Comments for documentation
-- ========================================
COMMENT ON TABLE tenants IS 'Customer accounts (server/community admins)';
COMMENT ON TABLE providers IS 'OAuth/OIDC identity provider configurations per tenant';
COMMENT ON TABLE claim_mappings IS 'Custom claim transformation rules for providers';
COMMENT ON TABLE platform_integrations IS 'Discord/Minecraft/Slack integration configs';
COMMENT ON TABLE users IS 'Verified user identities with normalized claims';
COMMENT ON TABLE user_platform_mappings IS 'Links verified users to platform accounts';
COMMENT ON TABLE role_rules IS 'Role mapping rules (JSONB rule engine)';
COMMENT ON TABLE refresh_tokens IS 'OAuth refresh tokens (encrypted)';
COMMENT ON TABLE audit_logs IS 'Activity tracking and audit trail';
COMMENT ON TABLE usage_metering IS 'MAU tracking for billing';
COMMENT ON TABLE verification_sessions IS 'Temporary OAuth flow state tracking';
