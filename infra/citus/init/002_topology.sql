\set ON_ERROR_STOP on
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM pg_dist_node WHERE groupid<>0 AND (nodename NOT IN ('worker1','worker2') OR nodeport<>5432 OR NOT isactive OR noderole<>'primary')) THEN
  RAISE EXCEPTION 'Existing topology has unexpected workers';
 END IF;
END $$;
SELECT recover_prepared_transactions();
SELECT citus_set_coordinator_host('coordinator',5432) WHERE NOT EXISTS (SELECT 1 FROM pg_dist_node WHERE groupid=0 AND nodename='coordinator' AND nodeport=5432);
SELECT citus_add_node('worker1',5432) WHERE NOT EXISTS (SELECT 1 FROM pg_dist_node WHERE nodename='worker1' AND nodeport=5432);
SELECT citus_add_node('worker2',5432) WHERE NOT EXISTS (SELECT 1 FROM pg_dist_node WHERE nodename='worker2' AND nodeport=5432);
