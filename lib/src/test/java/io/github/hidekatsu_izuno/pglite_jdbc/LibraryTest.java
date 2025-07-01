package io.github.hidekatsu_izuno.pglite_jdbc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LibraryTest {
    @Test
    void someLibraryMethodReturnsTrue() {
        var library = new Library();
        var instance = library.newInstance();
        System.out.println(instance.exports());
    }
}
