/* contrib/amcheck/amcheck--1.0.sql */

-- complain if script is sourced in psql, rather than via CREATE EXTENSION
\echo Use "CREATE EXTENSION amcheck" to load this file. \quit

--
-- bt_index_check()
--
CREATE FUNCTION bt_index_check(index regclass)
RETURNS VOID
AS 'MODULE_PATHNAME', 'bt_index_check'
LANGUAGE C STRICT PARALLEL RESTRICTED;

--
-- bt_index_parent_check()
--
CREATE FUNCTION bt_index_parent_check(index regclass)
RETURNS VOID
AS 'MODULE_PATHNAME', 'bt_index_parent_check'
LANGUAGE C STRICT PARALLEL RESTRICTED;

-- Don't want these to be available to public
REVOKE ALL ON FUNCTION bt_index_check(regclass) FROM PUBLIC;
REVOKE ALL ON FUNCTION bt_index_parent_check(regclass) FROM PUBLIC;

/* contrib/amcheck/amcheck--1.0--1.1.sql */

-- complain if script is sourced in psql, rather than via CREATE EXTENSION
\echo Use "ALTER EXTENSION amcheck UPDATE TO '1.1'" to load this file. \quit

-- In order to avoid issues with dependencies when updating amcheck to 1.1,
-- create new, overloaded versions of the 1.0 functions

--
-- bt_index_check()
--
CREATE FUNCTION bt_index_check(index regclass,
    heapallindexed boolean)
RETURNS VOID
AS 'MODULE_PATHNAME', 'bt_index_check'
LANGUAGE C STRICT PARALLEL RESTRICTED;

--
-- bt_index_parent_check()
--
CREATE FUNCTION bt_index_parent_check(index regclass,
    heapallindexed boolean)
RETURNS VOID
AS 'MODULE_PATHNAME', 'bt_index_parent_check'
LANGUAGE C STRICT PARALLEL RESTRICTED;

-- Don't want these to be available to public
REVOKE ALL ON FUNCTION bt_index_check(regclass, boolean) FROM PUBLIC;
REVOKE ALL ON FUNCTION bt_index_parent_check(regclass, boolean) FROM PUBLIC;

/* contrib/amcheck/amcheck--1.1--1.2.sql */

-- complain if script is sourced in psql, rather than via CREATE EXTENSION
\echo Use "ALTER EXTENSION amcheck UPDATE TO '1.2'" to load this file. \quit

-- In order to avoid issues with dependencies when updating amcheck to 1.2,
-- create new, overloaded version of the 1.1 function signature

--
-- bt_index_parent_check()
--
CREATE FUNCTION bt_index_parent_check(index regclass,
    heapallindexed boolean, rootdescend boolean)
RETURNS VOID
AS 'MODULE_PATHNAME', 'bt_index_parent_check'
LANGUAGE C STRICT PARALLEL RESTRICTED;

-- Don't want this to be available to public
REVOKE ALL ON FUNCTION bt_index_parent_check(regclass, boolean, boolean) FROM PUBLIC;

/* contrib/amcheck/amcheck--1.2--1.3.sql */

-- complain if script is sourced in psql, rather than via CREATE EXTENSION
\echo Use "ALTER EXTENSION amcheck UPDATE TO '1.3'" to load this file. \quit

--
-- verify_heapam()
--
CREATE FUNCTION verify_heapam(relation regclass,
							  on_error_stop boolean default false,
							  check_toast boolean default false,
							  skip text default 'none',
							  startblock bigint default null,
							  endblock bigint default null,
							  blkno OUT bigint,
							  offnum OUT integer,
							  attnum OUT integer,
							  msg OUT text)
RETURNS SETOF record
AS 'MODULE_PATHNAME', 'verify_heapam'
LANGUAGE C;

-- Don't want this to be available to public
REVOKE ALL ON FUNCTION verify_heapam(regclass,
									 boolean,
									 boolean,
									 text,
									 bigint,
									 bigint)
FROM PUBLIC;

/* contrib/amcheck/amcheck--1.3--1.4.sql */

-- complain if script is sourced in psql, rather than via CREATE EXTENSION
\echo Use "ALTER EXTENSION amcheck UPDATE TO '1.4'" to load this file. \quit

-- In order to avoid issues with dependencies when updating amcheck to 1.4,
-- create new, overloaded versions of the 1.2 bt_index_parent_check signature,
-- and 1.1 bt_index_check signature.

--
-- bt_index_parent_check()
--
CREATE FUNCTION bt_index_parent_check(index regclass,
    heapallindexed boolean, rootdescend boolean, checkunique boolean)
RETURNS VOID
AS 'MODULE_PATHNAME', 'bt_index_parent_check'
LANGUAGE C STRICT PARALLEL RESTRICTED;
--
-- bt_index_check()
--
CREATE FUNCTION bt_index_check(index regclass,
    heapallindexed boolean, checkunique boolean)
RETURNS VOID
AS 'MODULE_PATHNAME', 'bt_index_check'
LANGUAGE C STRICT PARALLEL RESTRICTED;

-- We don't want this to be available to public
REVOKE ALL ON FUNCTION bt_index_parent_check(regclass, boolean, boolean, boolean) FROM PUBLIC;
REVOKE ALL ON FUNCTION bt_index_check(regclass, boolean, boolean) FROM PUBLIC;

/* contrib/amcheck/amcheck--1.4--1.5.sql */

-- complain if script is sourced in psql, rather than via CREATE EXTENSION
\echo Use "ALTER EXTENSION amcheck UPDATE TO '1.5'" to load this file. \quit


-- gin_index_check()
--
CREATE FUNCTION gin_index_check(index regclass)
RETURNS VOID
AS 'MODULE_PATHNAME', 'gin_index_check'
LANGUAGE C STRICT;

REVOKE ALL ON FUNCTION gin_index_check(regclass) FROM PUBLIC;
