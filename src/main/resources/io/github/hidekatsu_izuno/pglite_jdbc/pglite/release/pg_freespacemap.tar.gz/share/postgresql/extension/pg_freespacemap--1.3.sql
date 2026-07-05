/* contrib/pg_freespacemap/pg_freespacemap--1.1.sql */

-- complain if script is sourced in psql, rather than via CREATE EXTENSION
\echo Use "CREATE EXTENSION pg_freespacemap" to load this file. \quit

-- Register the C function.
CREATE FUNCTION pg_freespace(regclass, bigint)
RETURNS int2
AS 'MODULE_PATHNAME', 'pg_freespace'
LANGUAGE C STRICT PARALLEL SAFE;

-- pg_freespace shows the recorded space avail at each block in a relation
CREATE FUNCTION
  pg_freespace(rel regclass, blkno OUT bigint, avail OUT int2)
RETURNS SETOF RECORD
AS $$
  SELECT blkno, pg_freespace($1, blkno) AS avail
  FROM generate_series(0, pg_relation_size($1) / current_setting('block_size')::bigint - 1) AS blkno;
$$
LANGUAGE SQL PARALLEL SAFE;


-- Don't want these to be available to public.
REVOKE ALL ON FUNCTION pg_freespace(regclass, bigint) FROM PUBLIC;
REVOKE ALL ON FUNCTION pg_freespace(regclass) FROM PUBLIC;

/* contrib/pg_freespacemap/pg_freespacemap--1.1--1.2.sql */

-- complain if script is sourced in psql, rather than via ALTER EXTENSION
\echo Use "ALTER EXTENSION pg_freespacemap UPDATE TO '1.2'" to load this file. \quit

GRANT EXECUTE ON FUNCTION  pg_freespace(regclass, bigint) TO pg_stat_scan_tables;
GRANT EXECUTE ON FUNCTION  pg_freespace(regclass) TO pg_stat_scan_tables;

/* contrib/pg_freespacemap/pg_freespacemap--1.2--1.3.sql */

-- complain if script is sourced in psql, rather than via ALTER EXTENSION
\echo Use "ALTER EXTENSION pg_freespacemap UPDATE TO '1.3'" to load this file. \quit

CREATE OR REPLACE FUNCTION
  pg_freespace(rel regclass, blkno OUT bigint, avail OUT int2)
RETURNS SETOF RECORD
LANGUAGE SQL PARALLEL SAFE
BEGIN ATOMIC
  SELECT blkno, pg_freespace($1, blkno) AS avail
  FROM generate_series('0'::bigint, pg_relation_size($1) / current_setting('block_size'::text)::bigint - '1'::bigint) AS blkno;
END;
