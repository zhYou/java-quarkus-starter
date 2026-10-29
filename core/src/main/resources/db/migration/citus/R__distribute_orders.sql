SET citus.shard_replication_factor = 1;
DO $$
BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_dist_partition WHERE logicalrelid='orders'::regclass) THEN
  PERFORM create_distributed_table('orders', 'customer_id', shard_count => 8);
 END IF;
 IF NOT EXISTS (SELECT 1 FROM citus_tables WHERE table_name='orders'::regclass AND distribution_column='customer_id' AND shard_count=8) THEN
  RAISE EXCEPTION 'Existing orders distribution is incompatible; expected customer_id with 8 shards';
 END IF;
END $$;
