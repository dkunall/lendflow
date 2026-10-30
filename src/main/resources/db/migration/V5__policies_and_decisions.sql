CREATE FUNCTION reject_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '% rows are immutable', TG_TABLE_NAME USING ERRCODE = 'integrity_constraint_violation';
END $$;

CREATE TABLE scorecards (
    id          text        PRIMARY KEY,
    definition  jsonb       NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE TRIGGER scorecards_immutable BEFORE UPDATE OR DELETE ON scorecards
    FOR EACH ROW EXECUTE FUNCTION reject_change();

CREATE TABLE rulesets (
    id            uuid        PRIMARY KEY,
    product       text        NOT NULL,
    version       integer     NOT NULL CHECK (version > 0),
    status        text        NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    definition    jsonb       NOT NULL,
    created_by    text        NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    published_by  text,
    published_at  timestamptz,
    UNIQUE (product, version),
    CHECK (status = 'DRAFT' OR published_by IS NOT NULL)
);
CREATE UNIQUE INDEX rulesets_one_published ON rulesets (product) WHERE status = 'PUBLISHED';

-- Only a draft can be edited; after that the only allowed change is PUBLISHED -> RETIRED.
CREATE FUNCTION guard_ruleset() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.status <> 'DRAFT' THEN
            RAISE EXCEPTION 'ruleset % v% was published and cannot be deleted', OLD.product, OLD.version
                USING ERRCODE = 'integrity_constraint_violation';
        END IF;
        RETURN OLD;
    END IF;
    IF OLD.status <> 'DRAFT' AND (NEW.definition IS DISTINCT FROM OLD.definition
            OR NEW.product IS DISTINCT FROM OLD.product OR NEW.version IS DISTINCT FROM OLD.version
            OR NEW.published_by IS DISTINCT FROM OLD.published_by
            OR NOT (OLD.status = 'PUBLISHED' AND NEW.status IN ('PUBLISHED', 'RETIRED'))) THEN
        RAISE EXCEPTION 'ruleset % v% is % and cannot be edited', OLD.product, OLD.version, OLD.status
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER rulesets_guard BEFORE UPDATE OR DELETE ON rulesets
    FOR EACH ROW EXECUTE FUNCTION guard_ruleset();

CREATE TABLE decisions (
    application_id   uuid          PRIMARY KEY REFERENCES applications (id),
    ruleset_id       uuid          NOT NULL REFERENCES rulesets (id),
    ruleset_key      text          NOT NULL,
    scorecard_id     text          NOT NULL REFERENCES scorecards (id),
    outcome          text          NOT NULL CHECK (outcome IN ('APPROVE', 'REFER', 'REJECT')),
    reasons          jsonb         NOT NULL,
    score            integer       NOT NULL,
    grade            text          NOT NULL,
    pd               numeric(8,6)  NOT NULL,
    contributions    jsonb         NOT NULL,
    fired_rules      jsonb         NOT NULL,
    annual_rate      numeric(5,2),
    fee_percent      numeric(5,2),
    max_emi          numeric(12,2),
    approved_amount  numeric(14,2),
    emi              numeric(12,2),
    inputs           jsonb         NOT NULL,
    decided_at       timestamptz   NOT NULL
);
CREATE TRIGGER decisions_immutable BEFORE UPDATE OR DELETE ON decisions
    FOR EACH ROW EXECUTE FUNCTION reject_change();

CREATE TABLE shadow_scores (
    application_id    uuid         PRIMARY KEY REFERENCES applications (id),
    ruleset_key       text         NOT NULL,
    champion_id       text         NOT NULL,
    champion_grade    text         NOT NULL,
    champion_score    integer      NOT NULL,
    challenger_id     text         NOT NULL,
    challenger_grade  text         NOT NULL,
    challenger_score  integer      NOT NULL,
    scored_at         timestamptz  NOT NULL
);
