/* contrib/pg_prewarm/pg_prewarm--1.1.sql */

-- complain if script is sourced in psql, rather than via CREATE EXTENSION
\echo Use "CREATE EXTENSION pg_prewarm" to load this file. \quit

-- Register the function.
CREATE FUNCTION pg_prewarm(regclass,
						   mode text default 'buffer',
						   fork text default 'main',
						   first_block int8 default null,
						   last_block int8 default null)
RETURNS int8
AS 'MODULE_PATHNAME', 'pg_prewarm'
LANGUAGE C PARALLEL SAFE;

/* contrib/pg_prewarm/pg_prewarm--1.1--1.2.sql */

-- complain if script is sourced in psql, rather than via ALTER EXTENSION
\echo Use "ALTER EXTENSION pg_prewarm UPDATE TO '1.2'" to load this file. \quit

CREATE FUNCTION autoprewarm_start_worker()
RETURNS VOID STRICT
AS 'MODULE_PATHNAME', 'autoprewarm_start_worker'
LANGUAGE C;

CREATE FUNCTION autoprewarm_dump_now()
RETURNS pg_catalog.int8 STRICT
AS 'MODULE_PATHNAME', 'autoprewarm_dump_now'
LANGUAGE C;
