ALTER TABLE orders ADD COLUMN status varchar(20) NOT NULL DEFAULT 'NEW';
ALTER TABLE orders ADD CONSTRAINT orders_status_check CHECK (status IN ('NEW','COMPLETED'));
GRANT SELECT,INSERT,UPDATE,DELETE ON orders TO app_runtime;
