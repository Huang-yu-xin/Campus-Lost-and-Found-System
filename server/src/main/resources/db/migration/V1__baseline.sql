-- =====================================================================
-- V1 baseline schema — 校园失物招领系统
-- 依据任务书 §6 逻辑实体表；MySQL 8 / InnoDB / utf8mb4。
-- 时间统一存 UTC（DATETIME 语义按 UTC 使用）。
-- 状态说明见 docs/architecture/state-machines.md；关系见 docs/architecture/er-diagram.md。
-- 本文件为 P1 草案，字段可在 P2/P3 通过新增 Vx 迁移演进，不回改历史迁移。
-- =====================================================================

-- ---------- 用户与身份 ----------
CREATE TABLE users (
    id                        BIGINT       NOT NULL AUTO_INCREMENT,
    nickname                  VARCHAR(64)  NOT NULL,
    campus                    VARCHAR(64)  NULL,
    avatar_file_id            BIGINT       NULL,
    status                    VARCHAR(24)  NOT NULL DEFAULT 'ACTIVE',        -- ACTIVE / RESTRICTED / DISABLED
    campus_verification_status VARCHAR(24) NOT NULL DEFAULT 'UNVERIFIED',    -- 本期恒为 UNVERIFIED
    created_at                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 第三方/校园身份绑定；(provider, provider_subject) 唯一
CREATE TABLE auth_identities (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    user_id           BIGINT      NOT NULL,
    provider          VARCHAR(32) NOT NULL,   -- WECHAT / MOCK / CAMPUS(future)
    provider_subject  VARCHAR(128) NOT NULL,  -- 如微信 openid（不进入公开响应）
    created_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_provider_subject (provider, provider_subject),
    KEY idx_auth_user (user_id),
    CONSTRAINT fk_auth_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 管理员凭据（安全散列，禁明文）
CREATE TABLE admin_credentials (
    admin_user_id   BIGINT       NOT NULL,
    username        VARCHAR(64)  NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,    -- BCrypt
    security_status VARCHAR(24)  NOT NULL DEFAULT 'ACTIVE',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (admin_user_id),
    UNIQUE KEY uk_admin_username (username),
    CONSTRAINT fk_admin_user FOREIGN KEY (admin_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 服务端会话/刷新令牌（不持久化明文令牌）
CREATE TABLE sessions (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    token_hash  VARCHAR(128) NOT NULL,
    expires_at  DATETIME     NOT NULL,
    revoked_at  DATETIME     NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_token (token_hash),
    KEY idx_session_user (user_id, expires_at),
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 文件 ----------
CREATE TABLE files (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    owner_id     BIGINT       NOT NULL,
    purpose      VARCHAR(24)  NOT NULL,     -- PUBLIC_POST / PRIVATE_CLAIM / PRIVATE_DISPUTE / PRIVATE_LEAD
    storage_key  VARCHAR(255) NOT NULL,     -- 随机不可枚举；物理路径不可由用户构造
    mime_type    VARCHAR(64)  NOT NULL,
    size         BIGINT       NOT NULL,
    visibility   VARCHAR(16)  NOT NULL DEFAULT 'PRIVATE', -- PUBLIC / PRIVATE
    bound        TINYINT(1)   NOT NULL DEFAULT 0,          -- 是否已绑定业务对象（孤儿清理依据）
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_storage_key (storage_key),
    KEY idx_file_owner (owner_id, purpose),
    CONSTRAINT fk_file_owner FOREIGN KEY (owner_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 发布信息 ----------
CREATE TABLE posts (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    publisher_id       BIGINT       NOT NULL,
    type               VARCHAR(8)   NOT NULL,   -- LOST / FOUND
    title              VARCHAR(128) NOT NULL,
    category           VARCHAR(48)  NOT NULL,
    public_description TEXT         NOT NULL,
    campus             VARCHAR(64)  NULL,
    event_location     VARCHAR(128) NULL,       -- 丢失/拾取地点
    event_time         DATETIME     NULL,       -- 丢失/拾取事件时间（≠发布时间）
    published_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status             VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE/HANDOVER/COMPLETED/WITHDRAWN/REMOVED
    version            INT          NOT NULL DEFAULT 0,        -- 乐观锁
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_post_public_list (status, type, category, event_time),
    KEY idx_post_publisher (publisher_id, created_at),
    CONSTRAINT fk_post_publisher FOREIGN KEY (publisher_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE post_images (
    id         BIGINT NOT NULL AUTO_INCREMENT,
    post_id    BIGINT NOT NULL,
    file_id    BIGINT NOT NULL,
    sort_order INT    NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_post_images_post (post_id),
    CONSTRAINT fk_post_images_post FOREIGN KEY (post_id) REFERENCES posts (id),
    CONSTRAINT fk_post_images_file FOREIGN KEY (file_id) REFERENCES files (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 认领与交接 ----------
CREATE TABLE claims (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    post_id       BIGINT       NOT NULL,   -- 仅 FOUND
    applicant_id  BIGINT       NOT NULL,
    description   TEXT         NOT NULL,   -- 私密证明文字（必填）
    status        VARCHAR(24)  NOT NULL DEFAULT 'PENDING',
                  -- PENDING/WAITING_HANDOVER/COMPLETED/REJECTED/WITHDRAWN/CLOSED
    reviewed_by   BIGINT       NULL,
    review_reason VARCHAR(255) NULL,
    reviewed_at   DATETIME     NULL,
    accepted_at   DATETIME     NULL,
    completed_at  DATETIME     NULL,
    version       INT          NOT NULL DEFAULT 0,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_claim_post_status (post_id, status),
    KEY idx_claim_applicant (applicant_id, created_at),
    CONSTRAINT fk_claim_post FOREIGN KEY (post_id) REFERENCES posts (id),
    CONSTRAINT fk_claim_applicant FOREIGN KEY (applicant_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 单活跃交接唯一性（辅助约束）：同一 post 至多一条"有效交接"；
-- 用生成列把 WAITING_HANDOVER 映射为 post_id、其余为 NULL，NULL 不参与唯一约束。
-- 详见 docs/architecture/state-machines.md 竞态方案（锁父发布 + 条件更新 + 本约束）。
ALTER TABLE claims
    ADD COLUMN active_handover_key BIGINT
        AS (CASE WHEN status = 'WAITING_HANDOVER' THEN post_id ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_claim_active_handover (active_handover_key);

-- 同一用户对同一 post 至多一个有效(PENDING/WAITING_HANDOVER)申请
ALTER TABLE claims
    ADD COLUMN active_applicant_key VARCHAR(48)
        AS (CASE WHEN status IN ('PENDING','WAITING_HANDOVER')
                 THEN CONCAT(post_id, ':', applicant_id) ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_claim_active_applicant (active_applicant_key);

CREATE TABLE claim_evidence_files (
    id       BIGINT NOT NULL AUTO_INCREMENT,
    claim_id BIGINT NOT NULL,
    file_id  BIGINT NOT NULL,   -- PRIVATE_CLAIM：仅本人/发布者/授权争议管理员可读
    PRIMARY KEY (id),
    KEY idx_claim_evidence_claim (claim_id),
    CONSTRAINT fk_claim_evidence_claim FOREIGN KEY (claim_id) REFERENCES claims (id),
    CONSTRAINT fk_claim_evidence_file FOREIGN KEY (file_id) REFERENCES files (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE handover_confirmations (
    id           BIGINT   NOT NULL AUTO_INCREMENT,
    claim_id     BIGINT   NOT NULL,
    confirmed_by BIGINT   NOT NULL,   -- 必须属于申请双方
    confirmed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_handover_claim_user (claim_id, confirmed_by), -- 幂等：重复确认不重复写
    CONSTRAINT fk_handover_claim FOREIGN KEY (claim_id) REFERENCES claims (id),
    CONSTRAINT fk_handover_user FOREIGN KEY (confirmed_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE claim_messages (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    claim_id   BIGINT   NOT NULL,
    sender_id  BIGINT   NOT NULL,
    body       VARCHAR(1000) NOT NULL,
    read_at    DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_claim_messages_claim (claim_id, created_at),
    CONSTRAINT fk_claim_messages_claim FOREIGN KEY (claim_id) REFERENCES claims (id),
    CONSTRAINT fk_claim_messages_sender FOREIGN KEY (sender_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 寻物线索 ----------
CREATE TABLE lost_leads (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    lost_post_id BIGINT      NOT NULL,   -- 仅 LOST
    reporter_id  BIGINT      NOT NULL,
    body         VARCHAR(1000) NOT NULL,
    status       VARCHAR(24) NOT NULL DEFAULT 'SUBMITTED', -- SUBMITTED/VIEWED/HELPFUL/CLOSED
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_lead_post (lost_post_id, status),
    KEY idx_lead_reporter (reporter_id, created_at),
    CONSTRAINT fk_lead_post FOREIGN KEY (lost_post_id) REFERENCES posts (id),
    CONSTRAINT fk_lead_reporter FOREIGN KEY (reporter_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE lead_evidence_files (
    id      BIGINT NOT NULL AUTO_INCREMENT,
    lead_id BIGINT NOT NULL,
    file_id BIGINT NOT NULL,   -- PRIVATE_LEAD
    PRIMARY KEY (id),
    KEY idx_lead_evidence_lead (lead_id),
    CONSTRAINT fk_lead_evidence_lead FOREIGN KEY (lead_id) REFERENCES lost_leads (id),
    CONSTRAINT fk_lead_evidence_file FOREIGN KEY (file_id) REFERENCES files (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 争议 ----------
CREATE TABLE disputes (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    claim_id          BIGINT       NOT NULL,
    raised_by         BIGINT       NOT NULL,
    reason            VARCHAR(255) NOT NULL,
    description       TEXT         NULL,
    status            VARCHAR(16)  NOT NULL DEFAULT 'OPEN', -- OPEN / RESOLVED / CLOSED
    assigned_admin_id BIGINT       NULL,
    resolution_type   VARCHAR(24)  NULL,   -- CONTINUE / TERMINATE_REOPEN / CLOSE
    resolution_note   VARCHAR(500) NULL,
    resolved_at       DATETIME     NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_dispute_claim (claim_id, status),
    -- 一个 claim 至多一个 OPEN 争议（生成列 + 唯一约束）
    CONSTRAINT fk_dispute_claim FOREIGN KEY (claim_id) REFERENCES claims (id),
    CONSTRAINT fk_dispute_raiser FOREIGN KEY (raised_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE disputes
    ADD COLUMN open_dispute_key BIGINT
        AS (CASE WHEN status = 'OPEN' THEN claim_id ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_dispute_open (open_dispute_key);

CREATE TABLE dispute_evidence_files (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    dispute_id  BIGINT NOT NULL,
    file_id     BIGINT NOT NULL,   -- PRIVATE_DISPUTE：严格受限，禁静态直链
    uploaded_by BIGINT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_dispute_evidence_dispute (dispute_id),
    CONSTRAINT fk_dispute_evidence_dispute FOREIGN KEY (dispute_id) REFERENCES disputes (id),
    CONSTRAINT fk_dispute_evidence_file FOREIGN KEY (file_id) REFERENCES files (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 治理 / 审计 / 维护 ----------
CREATE TABLE moderation_actions (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    admin_id     BIGINT       NOT NULL,
    target_type  VARCHAR(24)  NOT NULL,   -- POST / USER / DISPUTE ...
    target_id    BIGINT       NOT NULL,
    action       VARCHAR(32)  NOT NULL,   -- REMOVE / RESTORE / RESTRICT / UNRESTRICT ...
    reason       VARCHAR(255) NOT NULL,
    before_state VARCHAR(32)  NULL,
    after_state  VARCHAR(32)  NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_moderation_target (target_type, target_id),
    CONSTRAINT fk_moderation_admin FOREIGN KEY (admin_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE audit_logs (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    actor_id    BIGINT       NULL,
    actor_type  VARCHAR(16)  NOT NULL,    -- USER / ADMIN / SYSTEM
    action      VARCHAR(48)  NOT NULL,
    target_type VARCHAR(24)  NULL,
    target_id   BIGINT       NULL,
    request_id  VARCHAR(64)  NULL,
    result      VARCHAR(16)  NOT NULL,    -- SUCCESS / FAILURE
    metadata    JSON         NULL,        -- 仅必要元数据，不含完整私密证明
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_audit_actor (actor_id, created_at),
    KEY idx_audit_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE backup_records (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    initiated_by        BIGINT       NOT NULL,
    started_at          DATETIME     NOT NULL,
    finished_at         DATETIME     NULL,
    status              VARCHAR(16)  NOT NULL,   -- RUNNING / SUCCESS / FAILED
    manifest_path       VARCHAR(255) NULL,
    checksum            VARCHAR(128) NULL,
    restore_verified_at DATETIME     NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_backup_initiator FOREIGN KEY (initiated_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE idempotency_records (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    actor_id     BIGINT       NOT NULL,
    operation    VARCHAR(64)  NOT NULL,
    request_key  VARCHAR(128) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    response_ref VARCHAR(255) NULL,
    expires_at   DATETIME     NOT NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_idem_actor_op_key (actor_id, operation, request_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
