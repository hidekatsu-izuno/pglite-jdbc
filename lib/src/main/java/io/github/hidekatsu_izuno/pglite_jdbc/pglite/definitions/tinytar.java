package io.github.hidekatsu_izuno.pglite_jdbc.pglite.definitions;

import java.time.Instant;

public class tinytar {
    private tinytar() {}

    public record TarFile(
        String name,
        Integer mode,
        Integer uid,
        Integer gid,
        Integer size,
        Instant modifyTime,
        Integer checksum,
        Integer type,
        String linkName,
        String ustar,
        String owner,
        String group,
        Integer majorNumber,
        Integer minorNumber,
        String prefix,
        Instant accessTime,
        Instant createTime,
        byte[] data,
        Boolean isOldGNUFormat
    ) {}

    public record UntarOptions(
        Boolean extractData,
        Boolean checkHeader,
        Boolean checkChecksum,
        Boolean checkFileSize
    ) {}

    public static final String NULL_CHAR = "\u0000";
    public static final String TMAGIC = "ustar";
    public static final String OLDGNU_MAGIC = "ustar  ";

    public static final int REGTYPE = '0';
    public static final int LNKTYPE = '1';
    public static final int SYMTYPE = '2';
    public static final int CHRTYPE = '3';
    public static final int BLKTYPE = '4';
    public static final int DIRTYPE = '5';
    public static final int FIFOTYPE = '6';
    public static final int CONTTYPE = '7';

    public static final int TSUID = 04000;
    public static final int TSGID = 02000;
    public static final int TSVTX = 01000;
    public static final int TUREAD = 00400;
    public static final int TUWRITE = 00200;
    public static final int TUEXEC = 00100;
    public static final int TGREAD = 00040;
    public static final int TGWRITE = 00020;
    public static final int TGEXEC = 00010;
    public static final int TOREAD = 00004;
    public static final int TOWRITE = 00002;
    public static final int TOEXEC = 00001;

    public static final int TPERMALL = 07777;
    public static final int TPERMMASK = 07777;
}
