/* contrib/seg/seg--1.1.sql */

-- complain if script is sourced in psql, rather than via CREATE EXTENSION
\echo Use "CREATE EXTENSION seg" to load this file. \quit

-- Create the user-defined type for 1-D floating point intervals (seg)

CREATE FUNCTION seg_in(cstring)
RETURNS seg
AS 'MODULE_PATHNAME'
LANGUAGE C STRICT IMMUTABLE PARALLEL SAFE;

CREATE FUNCTION seg_out(seg)
RETURNS cstring
AS 'MODULE_PATHNAME'
LANGUAGE C STRICT IMMUTABLE PARALLEL SAFE;

CREATE TYPE seg (
	INTERNALLENGTH = 12,
	INPUT = seg_in,
	OUTPUT = seg_out
);

COMMENT ON TYPE seg IS
'floating point interval ''FLOAT .. FLOAT'', ''.. FLOAT'', ''FLOAT ..'' or ''FLOAT''';

--
-- External C-functions for R-tree methods
--

-- Left/Right methods

CREATE FUNCTION seg_over_left(seg, seg)
RETURNS bool
AS 'MODULE_PATHNAME'
LANGUAGE C STRICT IMMUTABLE PARALLEL SAFE;

COMMENT ON FUNCTION seg_over_left(seg, seg) IS
'overlaps or is left of';

CREATE FUNCTION seg_over_right(seg, seg)
RETURNS bool
AS 'MODULE_PATHNAME'
LANGUAGE C STRICT IMMUTABLE PARALLEL SAFE;

COMMENT ON FUNCTION seg_over_right(seg, seg) IS
'overlaps or is right of';

CREATE FUNCTION seg_left(seg, seg)
RETURNS bool
AS 'MODULE_PATHNAME'
LANGUAGE C STRICT IMMUTABLE PARALLEL SAFE;

COMMENT ON FUNCTION seg_left(seg, seg) IS
'is left of';

CREATE FUNCTION seg_right(seg, seg)
RETURNS bool
AS 'MODULE_PATHNAME'
LANGUAGE C STRICT IMMUTABLE PARALLEL SAFE;

COMMENT ON FUNCTION seg_right(seg, seg) IS
'is right of';


-- Scalar comparison methods

CREATE FUNCTION seg_lt(seg, seg)
RETURNS bool
AS 'MODULE_PATHNAME'
LANGUAGE C STRICT IMMUTABLE PARALLEL SAFE;

COMMENT ON FUNCTION seg_lt(seg, seg) IS
'less than';

CREATE FUNCTION seg_le(seg, seg)
RETURNS bool
AS 'MODULE_PATHNAME'
LANGUAGE C STRICT IMMUTABLE PARALLEL SAFE;

COMMENT ON FUNCTION seg_le(seg, seg) IS
'less than or equal';

CREATE FUNCTION seg_gt(seg, seg)
RETURNS bool
AS 'MODULE_PATHNAME'
LANGUAGE C STRICT IMMUTABLE PARALLEL SAFE;

COMMENT ON FUNCTION seg_gt(seg, seg) IS
'greater than';

CREATE FUNCTION seg_ge(seg, seg)
RETURNS bool
AS 'MODULE_PATHNAME'
LANGUAGE C STRICT IMMUTABLE PARALLEL SAFE;

COMMENT ON FUNCTION seg_ge(seg, seg) IS
'greater than or equal';

CREATE FUNCTION seg_contains(seg, seg)
RETURNS bool
AS 'MODULE_PATHNAME'
LANGUAGE C STRICT IMMUTABLE PARALLEL SAFE;

COMMENT ON FUNCTION seg_contains(seg, seg) IS
'contains';

CREATE FUNCTION seg_contained(seg, seg)
RETURNS bool
AS 'MODULE_PATHNAME'
LANGUAGE C STRICT IMMUTABLE PARALLEL SAFE;

COMMENT ON FUNCTION seg_contained(seg, seg) IS
'contained in';

CREATE FUNCTION seg_overlap(seg, seg)
RETURNS bool
AS 'MODULE_PATHNAME'
LANGUAGE C STRICT IMMUTABLE PARALLEL SAFE;

COMMENT ON FUNCTION seg_overlap(seg, seg) IS
'overlaps';

CREATE FUNCTION seg_same(seg, seg)
RETURNS bool
AS 'MODULE_PATHNAME'
LANGUAGE C STRICT IMMUTABLE PARALLEL SAFE;

COMMENT ON FUNCTION seg_same(seg, seg) IS
'same as';

CREATE FUNCTION seg_different(seg, seg)
RETURNS bool
AS 'MODULE_PATHNAME'
LANGUAGE C STRICT IMMUTABLE PARALLEL SAFE;

COMMENT ON FUNCTION seg_different(seg, seg) IS
'different';

-- support routines for indexing

CREATE FUNCTION seg_cmp(seg, seg)
RETURNS int4
AS 'MODULE_PATHNAME'
LANGUAGE C STRICT IMMUTABLE PARALLEL SAFE;

COMMENT ON FUNCTION seg_cmp(seg, seg) IS 'btree comparison function';

CREATE FUNCTION seg_union(seg, seg)
RETURNS seg
AS 'MODULE_PATHNAME'
LANGUAGE C STRICT IMMUTABLE PARALLEL SAFE;

CREATE FUNCTION seg_inter(seg, seg)
RETURNS seg
AS 'MODULE_PATHNAME'
LANGUAGE C STRICT IMMUTABLE PARALLEL SAFE;

CREATE FUNCTION seg_size(seg)
RETURNS float4
AS 'MODULE_PATHNAME'
LANGUAGE C STRICT IMMUTABLE PARALLEL SAFE;

-- miscellaneous

CREATE FUNCTION seg_center(seg)
RETURNS float4
AS 'MODULE_PATHNAME'
LANGUAGE C STRICT IMMUTABLE PARALLEL SAFE;

CREATE FUNCTION seg_upper(seg)
RETURNS float4
AS 'MODULE_PATHNAME'
LANGUAGE C STRICT IMMUTABLE PARALLEL SAFE;

CREATE FUNCTION seg_lower(seg)
RETURNS float4
AS 'MODULE_PATHNAME'
LANGUAGE C STRICT IMMUTABLE PARALLEL SAFE;


--
-- OPERATORS
--

CREATE OPERATOR < (
	LEFTARG = seg,
	RIGHTARG = seg,
	PROCEDURE = seg_lt,
	COMMUTATOR = '>',
	NEGATOR = '>=',
	RESTRICT = scalarltsel,
	JOIN = scalarltjoinsel
);

CREATE OPERATOR <= (
	LEFTARG = seg,
	RIGHTARG = seg,
	PROCEDURE = seg_le,
	COMMUTATOR = '>=',
	NEGATOR = '>',
	RESTRICT = scalarltsel,
	JOIN = scalarltjoinsel
);

CREATE OPERATOR > (
	LEFTARG = seg,
	RIGHTARG = seg,
	PROCEDURE = seg_gt,
	COMMUTATOR = '<',
	NEGATOR = '<=',
	RESTRICT = scalargtsel,
	JOIN = scalargtjoinsel
);

CREATE OPERATOR >= (
	LEFTARG = seg,
	RIGHTARG = seg,
	PROCEDURE = seg_ge,
	COMMUTATOR = '<=',
	NEGATOR = '<',
	RESTRICT = scalargtsel,
	JOIN = scalargtjoinsel
);

CREATE OPERATOR << (
	LEFTARG = seg,
	RIGHTARG = seg,
	PROCEDURE = seg_left,
	COMMUTATOR = '>>',
	RESTRICT = positionsel,
	JOIN = positionjoinsel
);

CREATE OPERATOR &< (
	LEFTARG = seg,
	RIGHTARG = seg,
	PROCEDURE = seg_over_left,
	RESTRICT = positionsel,
	JOIN = positionjoinsel
);

CREATE OPERATOR && (
	LEFTARG = seg,
	RIGHTARG = seg,
	PROCEDURE = seg_overlap,
	COMMUTATOR = '&&',
	RESTRICT = areasel,
	JOIN = areajoinsel
);

CREATE OPERATOR &> (
	LEFTARG = seg,
	RIGHTARG = seg,
	PROCEDURE = seg_over_right,
	RESTRICT = positionsel,
	JOIN = positionjoinsel
);

CREATE OPERATOR >> (
	LEFTARG = seg,
	RIGHTARG = seg,
	PROCEDURE = seg_right,
	COMMUTATOR = '<<',
	RESTRICT = positionsel,
	JOIN = positionjoinsel
);

CREATE OPERATOR = (
	LEFTARG = seg,
	RIGHTARG = seg,
	PROCEDURE = seg_same,
	COMMUTATOR = '=',
	NEGATOR = '<>',
	RESTRICT = eqsel,
	JOIN = eqjoinsel,
	MERGES
);

CREATE OPERATOR <> (
	LEFTARG = seg,
	RIGHTARG = seg,
	PROCEDURE = seg_different,
	COMMUTATOR = '<>',
	NEGATOR = '=',
	RESTRICT = neqsel,
	JOIN = neqjoinsel
);

CREATE OPERATOR @> (
	LEFTARG = seg,
	RIGHTARG = seg,
	PROCEDURE = seg_contains,
	COMMUTATOR = '<@',
	RESTRICT = contsel,
	JOIN = contjoinsel
);

CREATE OPERATOR <@ (
	LEFTARG = seg,
	RIGHTARG = seg,
	PROCEDURE = seg_contained,
	COMMUTATOR = '@>',
	RESTRICT = contsel,
	JOIN = contjoinsel
);

-- obsolete:
CREATE OPERATOR @ (
	LEFTARG = seg,
	RIGHTARG = seg,
	PROCEDURE = seg_contains,
	COMMUTATOR = '~',
	RESTRICT = contsel,
	JOIN = contjoinsel
);

CREATE OPERATOR ~ (
	LEFTARG = seg,
	RIGHTARG = seg,
	PROCEDURE = seg_contained,
	COMMUTATOR = '@',
	RESTRICT = contsel,
	JOIN = contjoinsel
);


-- define the GiST support methods
CREATE FUNCTION gseg_consistent(internal,seg,smallint,oid,internal)
RETURNS bool
AS 'MODULE_PATHNAME'
LANGUAGE C IMMUTABLE STRICT PARALLEL SAFE;

CREATE FUNCTION gseg_compress(internal)
RETURNS internal
AS 'MODULE_PATHNAME'
LANGUAGE C IMMUTABLE STRICT PARALLEL SAFE;

CREATE FUNCTION gseg_decompress(internal)
RETURNS internal
AS 'MODULE_PATHNAME'
LANGUAGE C IMMUTABLE STRICT PARALLEL SAFE;

CREATE FUNCTION gseg_penalty(internal,internal,internal)
RETURNS internal
AS 'MODULE_PATHNAME'
LANGUAGE C IMMUTABLE STRICT PARALLEL SAFE;

CREATE FUNCTION gseg_picksplit(internal, internal)
RETURNS internal
AS 'MODULE_PATHNAME'
LANGUAGE C IMMUTABLE STRICT PARALLEL SAFE;

CREATE FUNCTION gseg_union(internal, internal)
RETURNS seg
AS 'MODULE_PATHNAME'
LANGUAGE C IMMUTABLE STRICT PARALLEL SAFE;

CREATE FUNCTION gseg_same(seg, seg, internal)
RETURNS internal
AS 'MODULE_PATHNAME'
LANGUAGE C IMMUTABLE STRICT PARALLEL SAFE;


-- Create the operator classes for indexing

CREATE OPERATOR CLASS seg_ops
    DEFAULT FOR TYPE seg USING btree AS
        OPERATOR        1       < ,
        OPERATOR        2       <= ,
        OPERATOR        3       = ,
        OPERATOR        4       >= ,
        OPERATOR        5       > ,
        FUNCTION        1       seg_cmp(seg, seg);

CREATE OPERATOR CLASS gist_seg_ops
DEFAULT FOR TYPE seg USING gist
AS
	OPERATOR	1	<< ,
	OPERATOR	2	&< ,
	OPERATOR	3	&& ,
	OPERATOR	4	&> ,
	OPERATOR	5	>> ,
	OPERATOR	6	= ,
	OPERATOR	7	@> ,
	OPERATOR	8	<@ ,
	OPERATOR	13	@ ,
	OPERATOR	14	~ ,
	FUNCTION	1	gseg_consistent (internal, seg, smallint, oid, internal),
	FUNCTION	2	gseg_union (internal, internal),
	FUNCTION	3	gseg_compress (internal),
	FUNCTION	4	gseg_decompress (internal),
	FUNCTION	5	gseg_penalty (internal, internal, internal),
	FUNCTION	6	gseg_picksplit (internal, internal),
	FUNCTION	7	gseg_same (seg, seg, internal);

/* contrib/seg/seg--1.1--1.2.sql */

-- complain if script is sourced in psql, rather than via ALTER EXTENSION
\echo Use "ALTER EXTENSION seg UPDATE TO '1.2'" to load this file. \quit

ALTER OPERATOR <= (seg, seg) SET (
	RESTRICT = scalarlesel,
	JOIN = scalarlejoinsel
);

ALTER OPERATOR >= (seg, seg) SET (
	RESTRICT = scalargesel,
	JOIN = scalargejoinsel
);

/* contrib/seg/seg--1.2--1.3.sql */

-- complain if script is sourced in psql, rather than via ALTER EXTENSION
\echo Use "ALTER EXTENSION seg UPDATE TO '1.3'" to load this file. \quit

--
-- Get rid of unnecessary compress and decompress support functions.
--
-- To be allowed to drop the opclass entry for a support function,
-- we must change the entry's dependency type from 'internal' to 'auto',
-- as though it were a loose member of the opfamily rather than being
-- bound into a particular opclass.  There's no SQL command for that,
-- so fake it with a manual update on pg_depend.
--
DO LANGUAGE plpgsql
$$
DECLARE
  my_schema pg_catalog.text := pg_catalog.quote_ident(pg_catalog.current_schema());
  old_path pg_catalog.text := pg_catalog.current_setting('search_path');
BEGIN
-- for safety, transiently set search_path to just pg_catalog+pg_temp
PERFORM pg_catalog.set_config('search_path', 'pg_catalog, pg_temp', true);

UPDATE pg_catalog.pg_depend
SET deptype = 'a'
WHERE classid = 'pg_catalog.pg_amproc'::pg_catalog.regclass
  AND objid =
    (SELECT objid
     FROM pg_catalog.pg_depend
     WHERE classid = 'pg_catalog.pg_amproc'::pg_catalog.regclass
       AND refclassid = 'pg_catalog.pg_proc'::pg_catalog.regclass
       AND (refobjid = (my_schema || '.gseg_compress(internal)')::pg_catalog.regprocedure))
  AND refclassid = 'pg_catalog.pg_opclass'::pg_catalog.regclass
  AND deptype = 'i';

UPDATE pg_catalog.pg_depend
SET deptype = 'a'
WHERE classid = 'pg_catalog.pg_amproc'::pg_catalog.regclass
  AND objid =
    (SELECT objid
     FROM pg_catalog.pg_depend
     WHERE classid = 'pg_catalog.pg_amproc'::pg_catalog.regclass
       AND refclassid = 'pg_catalog.pg_proc'::pg_catalog.regclass
       AND (refobjid = (my_schema || '.gseg_decompress(internal)')::pg_catalog.regprocedure))
  AND refclassid = 'pg_catalog.pg_opclass'::pg_catalog.regclass
  AND deptype = 'i';

PERFORM pg_catalog.set_config('search_path', old_path, true);
END
$$;

ALTER OPERATOR FAMILY gist_seg_ops USING gist drop function 3 (seg);
ALTER EXTENSION seg DROP function gseg_compress(pg_catalog.internal);
DROP function gseg_compress(pg_catalog.internal);

ALTER OPERATOR FAMILY gist_seg_ops USING gist drop function 4 (seg);
ALTER EXTENSION seg DROP function gseg_decompress(pg_catalog.internal);
DROP function gseg_decompress(pg_catalog.internal);

/* contrib/seg/seg--1.3--1.4.sql */

-- complain if script is sourced in psql, rather than via ALTER EXTENSION
\echo Use "ALTER EXTENSION seg UPDATE TO '1.4'" to load this file. \quit

-- Remove @ and ~
DROP OPERATOR @ (seg, seg);
DROP OPERATOR ~ (seg, seg);
