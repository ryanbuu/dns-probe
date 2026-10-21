-- Run on the restored database before starting InfraPilot.
-- Refuse an ambiguous state instead of silently combining audit histories.
BEGIN;
DO $$
BEGIN
  IF to_regclass('public.audit_event') IS NOT NULL THEN
    IF to_regclass('public.infrapilot_audit_event') IS NOT NULL THEN
      RAISE EXCEPTION 'Both legacy and InfraPilot audit tables exist; inspect before migrating';
    END IF;
    ALTER TABLE public.audit_event RENAME TO infrapilot_audit_event;
  END IF;
  IF to_regclass('public.infrapilot_audit_event') IS NULL THEN
    RAISE EXCEPTION 'No audit table found in the restored database';
  END IF;
  IF to_regclass('public.audit_event_id_seq') IS NOT NULL THEN
    ALTER SEQUENCE public.audit_event_id_seq RENAME TO infrapilot_audit_event_id_seq;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_constraint
             WHERE conrelid='public.infrapilot_audit_event'::regclass
               AND conname='audit_event_pkey') THEN
    ALTER TABLE public.infrapilot_audit_event
      RENAME CONSTRAINT audit_event_pkey TO infrapilot_audit_event_pkey;
  END IF;
END $$;
COMMIT;
