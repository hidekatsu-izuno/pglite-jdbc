/* contrib/pageinspect/pageinspect--1.5.sql */

-- complain if script is sourced in psql, rather than via CREATE EXTENSION
\echo Use "CREATE EXTENSION pageinspect" to load this file. \quit

--
-- get_raw_page()
--
CREATE FUNCTION get_raw_page(text, int4)
RETURNS bytea
AS 'MODULE_PATHNAME', 'get_raw_page'
LANGUAGE C STRICT PARALLEL SAFE;

CREATE FUNCTION get_raw_page(text, text, int4)
RETURNS bytea
AS 'MODULE_PATHNAME', 'get_raw_page_fork'
LANGUAGE C STRICT PARALLEL SAFE;

--
-- page_header()
--
CREATE FUNCTION page_header(IN page bytea,
    OUT lsn pg_lsn,
    OUT checksum smallint,
    OUT flags smallint,
    OUT lower smallint,
    OUT upper smallint,
    OUT special smallint,
    OUT pagesize smallint,
    OUT version smallint,
    OUT prune_xid xid)
AS 'MODULE_PATHNAME', 'page_header'
LANGUAGE C STRICT PARALLEL SAFE;

--
-- heap_page_items()
--
CREATE FUNCTION heap_page_items(IN page bytea,
    OUT lp smallint,
    OUT lp_off smallint,
    OUT lp_flags smallint,
    OUT lp_len smallint,
    OUT t_xmin xid,
    OUT t_xmax xid,
    OUT t_field3 int4,
    OUT t_ctid tid,
    OUT t_infomask2 integer,
    OUT t_infomask integer,
    OUT t_hoff smallint,
    OUT t_bits text,
    OUT t_oid oid,
    OUT t_data bytea)
RETURNS SETOF record
AS 'MODULE_PATHNAME', 'heap_page_items'
LANGUAGE C STRICT PARALLEL SAFE;

--
-- tuple_data_split()
--
CREATE FUNCTION tuple_data_split(rel_oid oid,
    t_data bytea,
    t_infomask integer,
    t_infomask2 integer,
    t_bits text)
RETURNS bytea[]
AS 'MODULE_PATHNAME','tuple_data_split'
LANGUAGE C PARALLEL SAFE;

CREATE FUNCTION tuple_data_split(rel_oid oid,
    t_data bytea,
    t_infomask integer,
    t_infomask2 integer,
    t_bits text,
    do_detoast bool)
RETURNS bytea[]
AS 'MODULE_PATHNAME','tuple_data_split'
LANGUAGE C PARALLEL SAFE;

--
-- heap_page_item_attrs()
--
CREATE FUNCTION heap_page_item_attrs(
    IN page bytea,
    IN rel_oid regclass,
    IN do_detoast bool,
    OUT lp smallint,
    OUT lp_off smallint,
    OUT lp_flags smallint,
    OUT lp_len smallint,
    OUT t_xmin xid,
    OUT t_xmax xid,
    OUT t_field3 int4,
    OUT t_ctid tid,
    OUT t_infomask2 integer,
    OUT t_infomask integer,
    OUT t_hoff smallint,
    OUT t_bits text,
    OUT t_oid oid,
    OUT t_attrs bytea[]
    )
RETURNS SETOF record AS $$
SELECT lp,
       lp_off,
       lp_flags,
       lp_len,
       t_xmin,
       t_xmax,
       t_field3,
       t_ctid,
       t_infomask2,
       t_infomask,
       t_hoff,
       t_bits,
       t_oid,
       tuple_data_split(
         rel_oid,
         t_data,
         t_infomask,
         t_infomask2,
         t_bits,
         do_detoast)
         AS t_attrs
  FROM heap_page_items(page);
$$ LANGUAGE SQL PARALLEL SAFE;

CREATE FUNCTION heap_page_item_attrs(IN page bytea, IN rel_oid regclass,
    OUT lp smallint,
    OUT lp_off smallint,
    OUT lp_flags smallint,
    OUT lp_len smallint,
    OUT t_xmin xid,
    OUT t_xmax xid,
    OUT t_field3 int4,
    OUT t_ctid tid,
    OUT t_infomask2 integer,
    OUT t_infomask integer,
    OUT t_hoff smallint,
    OUT t_bits text,
    OUT t_oid oid,
    OUT t_attrs bytea[]
    )
RETURNS SETOF record AS $$
SELECT * from heap_page_item_attrs(page, rel_oid, false);
$$ LANGUAGE SQL PARALLEL SAFE;

--
-- bt_metap()
--
CREATE FUNCTION bt_metap(IN relname text,
    OUT magic int4,
    OUT version int4,
    OUT root int4,
    OUT level int4,
    OUT fastroot int4,
    OUT fastlevel int4)
AS 'MODULE_PATHNAME', 'bt_metap'
LANGUAGE C STRICT PARALLEL SAFE;

--
-- bt_page_stats()
--
CREATE FUNCTION bt_page_stats(IN relname text, IN blkno int4,
    OUT blkno int4,
    OUT type "char",
    OUT live_items int4,
    OUT dead_items int4,
    OUT avg_item_size int4,
    OUT page_size int4,
    OUT free_size int4,
    OUT btpo_prev int4,
    OUT btpo_next int4,
    OUT btpo int4,
    OUT btpo_flags int4)
AS 'MODULE_PATHNAME', 'bt_page_stats'
LANGUAGE C STRICT PARALLEL SAFE;

--
-- bt_page_items()
--
CREATE FUNCTION bt_page_items(IN relname text, IN blkno int4,
    OUT itemoffset smallint,
    OUT ctid tid,
    OUT itemlen smallint,
    OUT nulls bool,
    OUT vars bool,
    OUT data text)
RETURNS SETOF record
AS 'MODULE_PATHNAME', 'bt_page_items'
LANGUAGE C STRICT PARALLEL SAFE;

--
-- brin_page_type()
--
CREATE FUNCTION brin_page_type(IN page bytea)
RETURNS text
AS 'MODULE_PATHNAME', 'brin_page_type'
LANGUAGE C STRICT PARALLEL SAFE;

--
-- brin_metapage_info()
--
CREATE FUNCTION brin_metapage_info(IN page bytea, OUT magic text,
	OUT version integer, OUT pagesperrange integer, OUT lastrevmappage bigint)
AS 'MODULE_PATHNAME', 'brin_metapage_info'
LANGUAGE C STRICT PARALLEL SAFE;

--
-- brin_revmap_data()
--
CREATE FUNCTION brin_revmap_data(IN page bytea,
	OUT pages tid)
RETURNS SETOF tid
AS 'MODULE_PATHNAME', 'brin_revmap_data'
LANGUAGE C STRICT PARALLEL SAFE;

--
-- brin_page_items()
--
CREATE FUNCTION brin_page_items(IN page bytea, IN index_oid regclass,
	OUT itemoffset int,
	OUT blknum int,
	OUT attnum int,
	OUT allnulls bool,
	OUT hasnulls bool,
	OUT placeholder bool,
	OUT value text)
RETURNS SETOF record
AS 'MODULE_PATHNAME', 'brin_page_items'
LANGUAGE C STRICT PARALLEL SAFE;

--
-- fsm_page_contents()
--
CREATE FUNCTION fsm_page_contents(IN page bytea)
RETURNS text
AS 'MODULE_PATHNAME', 'fsm_page_contents'
LANGUAGE C STRICT PARALLEL SAFE;

--
-- GIN functions
--

--
-- gin_metapage_info()
--
CREATE FUNCTION gin_metapage_info(IN page bytea,
    OUT pending_head bigint,
    OUT pending_tail bigint,
    OUT tail_free_size int4,
    OUT n_pending_pages bigint,
    OUT n_pending_tuples bigint,
    OUT n_total_pages bigint,
    OUT n_entry_pages bigint,
    OUT n_data_pages bigint,
    OUT n_entries bigint,
    OUT version int4)
AS 'MODULE_PATHNAME', 'gin_metapage_info'
LANGUAGE C STRICT PARALLEL SAFE;

--
-- gin_page_opaque_info()
--
CREATE FUNCTION gin_page_opaque_info(IN page bytea,
    OUT rightlink bigint,
    OUT maxoff int4,
    OUT flags text[])
AS 'MODULE_PATHNAME', 'gin_page_opaque_info'
LANGUAGE C STRICT PARALLEL SAFE;

--
-- gin_leafpage_items()
--
CREATE FUNCTION gin_leafpage_items(IN page bytea,
    OUT first_tid tid,
    OUT nbytes int2,
    OUT tids tid[])
RETURNS SETOF record
AS 'MODULE_PATHNAME', 'gin_leafpage_items'
LANGUAGE C STRICT PARALLEL SAFE;

/* contrib/pageinspect/pageinspect--1.5--1.6.sql */

-- complain if script is sourced in psql, rather than via ALTER EXTENSION
\echo Use "ALTER EXTENSION pageinspect UPDATE TO '1.6'" to load this file. \quit

--
-- HASH functions
--

--
-- hash_page_type()
--
CREATE FUNCTION hash_page_type(IN page bytea)
RETURNS text
AS 'MODULE_PATHNAME', 'hash_page_type'
LANGUAGE C STRICT PARALLEL SAFE;

--
-- hash_page_stats()
--
CREATE FUNCTION hash_page_stats(IN page bytea,
    OUT live_items int4,
    OUT dead_items int4,
    OUT page_size int4,
    OUT free_size int4,
    OUT hasho_prevblkno int8,
    OUT hasho_nextblkno int8,
    OUT hasho_bucket int8,
	OUT hasho_flag int4,
	OUT hasho_page_id int4)
AS 'MODULE_PATHNAME', 'hash_page_stats'
LANGUAGE C STRICT PARALLEL SAFE;

--
-- hash_page_items()
--
CREATE FUNCTION hash_page_items(IN page bytea,
	OUT itemoffset int4,
	OUT ctid tid,
	OUT data int8)
RETURNS SETOF record
AS 'MODULE_PATHNAME', 'hash_page_items'
LANGUAGE C STRICT PARALLEL SAFE;

--
-- hash_bitmap_info()
--
CREATE FUNCTION hash_bitmap_info(IN index_oid regclass, IN blkno int8,
	OUT bitmapblkno int8,
	OUT bitmapbit int4,
	OUT bitstatus bool)
RETURNS SETOF record
AS 'MODULE_PATHNAME', 'hash_bitmap_info'
LANGUAGE C STRICT PARALLEL SAFE;

--
-- hash_metapage_info()
--
CREATE FUNCTION hash_metapage_info(IN page bytea,
    OUT magic int8,
    OUT version int8,
    OUT ntuples double precision,
    OUT ffactor int4,
    OUT bsize int4,
    OUT bmsize int4,
    OUT bmshift int4,
    OUT maxbucket int8,
    OUT highmask int8,
    OUT lowmask int8,
    OUT ovflpoint int8,
    OUT firstfree int8,
    OUT nmaps int8,
    OUT procid oid,
    OUT spares int8[],
    OUT mapp int8[])
AS 'MODULE_PATHNAME', 'hash_metapage_info'
LANGUAGE C STRICT PARALLEL SAFE;

--
-- page_checksum()
--
CREATE FUNCTION page_checksum(IN page bytea, IN blkno int4)
RETURNS smallint
AS 'MODULE_PATHNAME', 'page_checksum'
LANGUAGE C STRICT PARALLEL SAFE;

--
-- bt_page_items_bytea()
--
CREATE FUNCTION bt_page_items(IN page bytea,
    OUT itemoffset smallint,
    OUT ctid tid,
    OUT itemlen smallint,
    OUT nulls bool,
    OUT vars bool,
    OUT data text)
RETURNS SETOF record
AS 'MODULE_PATHNAME', 'bt_page_items_bytea'
LANGUAGE C STRICT PARALLEL SAFE;

/* contrib/pageinspect/pageinspect--1.6--1.7.sql */

-- complain if script is sourced in psql, rather than via ALTER EXTENSION
\echo Use "ALTER EXTENSION pageinspect UPDATE TO '1.7'" to load this file. \quit

--
-- bt_metap()
--
DROP FUNCTION bt_metap(IN relname text,
    OUT magic int4,
    OUT version int4,
    OUT root int4,
    OUT level int4,
    OUT fastroot int4,
    OUT fastlevel int4);
CREATE FUNCTION bt_metap(IN relname text,
    OUT magic int4,
    OUT version int4,
    OUT root int4,
    OUT level int4,
    OUT fastroot int4,
    OUT fastlevel int4,
    OUT oldest_xact int4,
    OUT last_cleanup_num_tuples real)
AS 'MODULE_PATHNAME', 'bt_metap'
LANGUAGE C STRICT PARALLEL SAFE;

/* contrib/pageinspect/pageinspect--1.7--1.8.sql */

-- complain if script is sourced in psql, rather than via ALTER EXTENSION
\echo Use "ALTER EXTENSION pageinspect UPDATE TO '1.8'" to load this file. \quit

--
-- heap_tuple_infomask_flags()
--
CREATE FUNCTION heap_tuple_infomask_flags(
       t_infomask integer,
       t_infomask2 integer,
       raw_flags OUT text[],
       combined_flags OUT text[])
RETURNS record
AS 'MODULE_PATHNAME', 'heap_tuple_infomask_flags'
LANGUAGE C STRICT PARALLEL SAFE;

--
-- bt_metap()
--
DROP FUNCTION bt_metap(text);
CREATE FUNCTION bt_metap(IN relname text,
    OUT magic int4,
    OUT version int4,
    OUT root int8,
    OUT level int8,
    OUT fastroot int8,
    OUT fastlevel int8,
    OUT oldest_xact xid,
    OUT last_cleanup_num_tuples float8,
    OUT allequalimage boolean)
AS 'MODULE_PATHNAME', 'bt_metap'
LANGUAGE C STRICT PARALLEL SAFE;

--
-- bt_page_items(text, int4)
--
DROP FUNCTION bt_page_items(text, int4);
CREATE FUNCTION bt_page_items(IN relname text, IN blkno int4,
    OUT itemoffset smallint,
    OUT ctid tid,
    OUT itemlen smallint,
    OUT nulls bool,
    OUT vars bool,
    OUT data text,
    OUT dead boolean,
    OUT htid tid,
    OUT tids tid[])
RETURNS SETOF record
AS 'MODULE_PATHNAME', 'bt_page_items'
LANGUAGE C STRICT PARALLEL SAFE;

--
-- bt_page_items(bytea)
--
DROP FUNCTION bt_page_items(bytea);
CREATE FUNCTION bt_page_items(IN page bytea,
    OUT itemoffset smallint,
    OUT ctid tid,
    OUT itemlen smallint,
    OUT nulls bool,
    OUT vars bool,
    OUT data text,
    OUT dead boolean,
    OUT htid tid,
    OUT tids tid[])
RETURNS SETOF record
AS 'MODULE_PATHNAME', 'bt_page_items_bytea'
LANGUAGE C STRICT PARALLEL SAFE;

/* contrib/pageinspect/pageinspect--1.8--1.9.sql */

-- complain if script is sourced in psql, rather than via ALTER EXTENSION
\echo Use "ALTER EXTENSION pageinspect UPDATE TO '1.9'" to load this file. \quit

--
-- gist_page_opaque_info()
--
CREATE FUNCTION gist_page_opaque_info(IN page bytea,
    OUT lsn pg_lsn,
    OUT nsn pg_lsn,
    OUT rightlink bigint,
    OUT flags text[])
AS 'MODULE_PATHNAME', 'gist_page_opaque_info'
LANGUAGE C STRICT PARALLEL SAFE;


--
-- gist_page_items_bytea()
--
CREATE FUNCTION gist_page_items_bytea(IN page bytea,
    OUT itemoffset smallint,
    OUT ctid tid,
    OUT itemlen smallint,
    OUT dead boolean,
    OUT key_data bytea)
RETURNS SETOF record
AS 'MODULE_PATHNAME', 'gist_page_items_bytea'
LANGUAGE C STRICT PARALLEL SAFE;

--
-- gist_page_items()
--
CREATE FUNCTION gist_page_items(IN page bytea,
    IN index_oid regclass,
    OUT itemoffset smallint,
    OUT ctid tid,
    OUT itemlen smallint,
    OUT dead boolean,
    OUT keys text)
RETURNS SETOF record
AS 'MODULE_PATHNAME', 'gist_page_items'
LANGUAGE C STRICT PARALLEL SAFE;

--
-- get_raw_page()
--
DROP FUNCTION get_raw_page(text, int4);
CREATE FUNCTION get_raw_page(text, int8)
RETURNS bytea
AS 'MODULE_PATHNAME', 'get_raw_page_1_9'
LANGUAGE C STRICT PARALLEL SAFE;

DROP FUNCTION get_raw_page(text, text, int4);
CREATE FUNCTION get_raw_page(text, text, int8)
RETURNS bytea
AS 'MODULE_PATHNAME', 'get_raw_page_fork_1_9'
LANGUAGE C STRICT PARALLEL SAFE;

--
-- page_checksum()
--
DROP FUNCTION page_checksum(IN page bytea, IN blkno int4);
CREATE FUNCTION page_checksum(IN page bytea, IN blkno int8)
RETURNS smallint
AS 'MODULE_PATHNAME', 'page_checksum_1_9'
LANGUAGE C STRICT PARALLEL SAFE;

--
-- bt_metap()
--
DROP FUNCTION bt_metap(text);
CREATE FUNCTION bt_metap(IN relname text,
    OUT magic int4,
    OUT version int4,
    OUT root int8,
    OUT level int8,
    OUT fastroot int8,
    OUT fastlevel int8,
    OUT last_cleanup_num_delpages int8,
    OUT last_cleanup_num_tuples float8,
    OUT allequalimage boolean)
AS 'MODULE_PATHNAME', 'bt_metap'
LANGUAGE C STRICT PARALLEL SAFE;

--
-- bt_page_stats()
--
DROP FUNCTION bt_page_stats(text, int4);
CREATE FUNCTION bt_page_stats(IN relname text, IN blkno int8,
    OUT blkno int8,
    OUT type "char",
    OUT live_items int4,
    OUT dead_items int4,
    OUT avg_item_size int4,
    OUT page_size int4,
    OUT free_size int4,
    OUT btpo_prev int8,
    OUT btpo_next int8,
    OUT btpo_level int8,
    OUT btpo_flags int4)
AS 'MODULE_PATHNAME', 'bt_page_stats_1_9'
LANGUAGE C STRICT PARALLEL SAFE;

--
-- bt_page_items()
--
DROP FUNCTION bt_page_items(text, int4);
CREATE FUNCTION bt_page_items(IN relname text, IN blkno int8,
    OUT itemoffset smallint,
    OUT ctid tid,
    OUT itemlen smallint,
    OUT nulls bool,
    OUT vars bool,
    OUT data text,
    OUT dead boolean,
    OUT htid tid,
    OUT tids tid[])
RETURNS SETOF record
AS 'MODULE_PATHNAME', 'bt_page_items_1_9'
LANGUAGE C STRICT PARALLEL SAFE;

--
-- brin_page_items()
--
DROP FUNCTION brin_page_items(IN page bytea, IN index_oid regclass);
CREATE FUNCTION brin_page_items(IN page bytea, IN index_oid regclass,
    OUT itemoffset int,
    OUT blknum int8,
    OUT attnum int,
    OUT allnulls bool,
    OUT hasnulls bool,
    OUT placeholder bool,
    OUT value text)
RETURNS SETOF record
AS 'MODULE_PATHNAME', 'brin_page_items'
LANGUAGE C STRICT PARALLEL SAFE;

/* contrib/pageinspect/pageinspect--1.9--1.10.sql */

-- complain if script is sourced in psql, rather than via ALTER EXTENSION
\echo Use "ALTER EXTENSION pageinspect UPDATE TO '1.10'" to load this file. \quit

--
-- page_header()
--
DROP FUNCTION page_header(IN page bytea);
CREATE FUNCTION page_header(IN page bytea,
    OUT lsn pg_lsn,
    OUT checksum smallint,
    OUT flags smallint,
    OUT lower int,
    OUT upper int,
    OUT special int,
    OUT pagesize int,
    OUT version smallint,
    OUT prune_xid xid)
AS 'MODULE_PATHNAME', 'page_header'
LANGUAGE C STRICT PARALLEL SAFE;

/* contrib/pageinspect/pageinspect--1.10--1.11.sql */

-- complain if script is sourced in psql, rather than via ALTER EXTENSION
\echo Use "ALTER EXTENSION pageinspect UPDATE TO '1.11'" to load this file. \quit

--
-- Functions that fetch relation pages must be PARALLEL RESTRICTED,
-- not PARALLEL SAFE, otherwise they will fail when run on a
-- temporary table in a parallel worker process.
--

ALTER FUNCTION get_raw_page(text, int8) PARALLEL RESTRICTED;
ALTER FUNCTION get_raw_page(text, text, int8) PARALLEL RESTRICTED;
-- tuple_data_split must be restricted because it may fetch TOAST data.
ALTER FUNCTION tuple_data_split(oid, bytea, integer, integer, text) PARALLEL RESTRICTED;
ALTER FUNCTION tuple_data_split(oid, bytea, integer, integer, text, bool) PARALLEL RESTRICTED;
-- heap_page_item_attrs must be restricted because it calls tuple_data_split.
ALTER FUNCTION heap_page_item_attrs(bytea, regclass, bool) PARALLEL RESTRICTED;
ALTER FUNCTION heap_page_item_attrs(bytea, regclass) PARALLEL RESTRICTED;
ALTER FUNCTION bt_metap(text) PARALLEL RESTRICTED;
ALTER FUNCTION bt_page_stats(text, int8) PARALLEL RESTRICTED;
ALTER FUNCTION bt_page_items(text, int8) PARALLEL RESTRICTED;
ALTER FUNCTION hash_bitmap_info(regclass, int8) PARALLEL RESTRICTED;
-- brin_page_items might be parallel safe, because it seems to touch
-- only index metadata, but I don't think there's a point in risking it.
-- Likewise for gist_page_items.
ALTER FUNCTION brin_page_items(bytea, regclass) PARALLEL RESTRICTED;
ALTER FUNCTION gist_page_items(bytea, regclass) PARALLEL RESTRICTED;

/* contrib/pageinspect/pageinspect--1.11--1.12.sql */

-- complain if script is sourced in psql, rather than via ALTER EXTENSION
\echo Use "ALTER EXTENSION pageinspect UPDATE TO '1.12'" to load this file. \quit

--
-- bt_multi_page_stats()
--
CREATE FUNCTION bt_multi_page_stats(IN relname text, IN blkno int8, IN blk_count int8,
    OUT blkno int8,
    OUT type "char",
    OUT live_items int4,
    OUT dead_items int4,
    OUT avg_item_size int4,
    OUT page_size int4,
    OUT free_size int4,
    OUT btpo_prev int8,
    OUT btpo_next int8,
    OUT btpo_level int8,
    OUT btpo_flags int4)
RETURNS SETOF record
AS 'MODULE_PATHNAME', 'bt_multi_page_stats'
LANGUAGE C STRICT PARALLEL RESTRICTED;

--
-- add information about BRIN empty ranges
--
DROP FUNCTION brin_page_items(IN page bytea, IN index_oid regclass);
CREATE FUNCTION brin_page_items(IN page bytea, IN index_oid regclass,
    OUT itemoffset int,
    OUT blknum int8,
    OUT attnum int,
    OUT allnulls bool,
    OUT hasnulls bool,
    OUT placeholder bool,
    OUT empty bool,
    OUT value text)
RETURNS SETOF record
AS 'MODULE_PATHNAME', 'brin_page_items'
LANGUAGE C STRICT PARALLEL RESTRICTED;

/* contrib/pageinspect/pageinspect--1.12--1.13.sql */

-- complain if script is sourced in psql, rather than via ALTER EXTENSION
\echo Use "ALTER EXTENSION pageinspect UPDATE TO '1.13'" to load this file. \quit

-- Convert SQL functions to new style

CREATE OR REPLACE FUNCTION heap_page_item_attrs(
    IN page bytea,
    IN rel_oid regclass,
    IN do_detoast bool,
    OUT lp smallint,
    OUT lp_off smallint,
    OUT lp_flags smallint,
    OUT lp_len smallint,
    OUT t_xmin xid,
    OUT t_xmax xid,
    OUT t_field3 int4,
    OUT t_ctid tid,
    OUT t_infomask2 integer,
    OUT t_infomask integer,
    OUT t_hoff smallint,
    OUT t_bits text,
    OUT t_oid oid,
    OUT t_attrs bytea[]
    )
RETURNS SETOF record
LANGUAGE SQL PARALLEL RESTRICTED
BEGIN ATOMIC
SELECT lp,
       lp_off,
       lp_flags,
       lp_len,
       t_xmin,
       t_xmax,
       t_field3,
       t_ctid,
       t_infomask2,
       t_infomask,
       t_hoff,
       t_bits,
       t_oid,
       tuple_data_split(
         rel_oid::oid,
         t_data,
         t_infomask,
         t_infomask2,
         t_bits,
         do_detoast)
         AS t_attrs
  FROM heap_page_items(page);
END;

CREATE OR REPLACE FUNCTION heap_page_item_attrs(IN page bytea, IN rel_oid regclass,
    OUT lp smallint,
    OUT lp_off smallint,
    OUT lp_flags smallint,
    OUT lp_len smallint,
    OUT t_xmin xid,
    OUT t_xmax xid,
    OUT t_field3 int4,
    OUT t_ctid tid,
    OUT t_infomask2 integer,
    OUT t_infomask integer,
    OUT t_hoff smallint,
    OUT t_bits text,
    OUT t_oid oid,
    OUT t_attrs bytea[]
    )
RETURNS SETOF record
LANGUAGE SQL PARALLEL RESTRICTED
BEGIN ATOMIC
SELECT * FROM heap_page_item_attrs(page, rel_oid, false);
END;
