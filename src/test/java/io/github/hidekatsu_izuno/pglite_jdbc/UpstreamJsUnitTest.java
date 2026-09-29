package io.github.hidekatsu_izuno.pglite_jdbc;

import static org.junit.jupiter.api.Assertions.*;

import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.parser;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.parse;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.types;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class UpstreamJsUnitTest {
    @Test
    void malformedBackendMessageDoesNotPoisonTheNextParse() {
        // DataRow claims two fields, but the first field exceeds the packet.
        var malformed = new byte[] {'D', 0, 0, 0, 10, 0, 2, 0, 0, 0, 16};
        var ready = new byte[] {'Z', 0, 0, 0, 5, 'I'};
        var protocol = new parser.Parser();
        var received = new ArrayList<messages.BackendMessage>();
        for (var i = 0; i < 3; i++) {
            protocol.parse(new Uint8Array(Arrays.copyOf(malformed, 4)), received::add);
            assertThrows(IndexOutOfBoundsException.class, () -> protocol.parse(
                new Uint8Array(Arrays.copyOfRange(malformed, 4, malformed.length)), received::add));
            protocol.parse(new Uint8Array(ready), received::add);
        }
        assertEquals(3, received.size());
        for (var message : received) {
            assertEquals("I", assertInstanceOf(messages.ReadyForQueryMessage.class, message).status);
        }
    }

    @Test
    void arraysPreserveNullStringsEmptyStringsAndNestedEscapes() {
        var values = Arrays.asList(null, "NULL", "", "a,b", "{text}", "a\"b", "a\\b");
        var parser = types.parsers.get(types.TEXT);
        // PostgreSQL emits unquoted NULL in uppercase, unlike the input serializer.
        var backendText = "{NULL,\"NULL\",\"\",\"a,b\",\"{text}\",\"a\\\"b\",\"a\\\\b\"}";
        assertEquals(values, types.arrayParser(backendText, parser, 1009));
        var nested = List.of(values, values);
        assertEquals(nested, types.arrayParser("{" + backendText + "," + backendText + "}", parser, 1009));
        assertEquals(Arrays.asList(null, 123.0, 0.0, null),
            types.arrayParser("{NULL,123,0,NULL}", types.parsers.get(types.INT4), 1007));
        assertEquals(List.of("(1,2),(3,4)", "(5,6),(7,8)"),
            types.arrayParser("{(1,2),(3,4);(5,6),(7,8)}", null, 1020));
        assertEquals(List.of("NULL", ""), types.arrayParser("[0:1]={\"NULL\",\"\"}", parser, 1009));
    }

    @Test
    void untypedDatesUseIsoUtcStrings() {
        var instant = Instant.parse("2024-01-15T12:34:56Z");
        for (var oid : List.of(0, types.TEXT, types.VARCHAR)) {
            assertEquals("2024-01-15T12:34:56.000Z", types.serializers.get(oid).serialize(instant));
            assertEquals("2024-01-15T12:34:56.000Z", types.serializers.get(oid).serialize(java.util.Date.from(instant)));
            assertEquals("true", types.serializers.get(oid).serialize(true));
        }
    }

    @Test
    void commandMetadataIsPerStatementWhileAffectedRowsRemainCumulative() {
        var tags = List.of("CREATE TABLE", "INSERT 0 2", "UPDATE 1", "SELECT 2", "FETCH 1", "COPY 3", "MERGE 2");
        var commands = new ArrayList<messages.BackendMessage>();
        for (var tag : tags) commands.add(new messages.CommandCompleteMessage(tag.length() + 5, tag));
        var results = parse.parseResults(commands, types.parsers, null, null);
        assertNull(results.getFirst().rowCount());
        assertEquals("CREATE", results.getFirst().command());
        assertEquals(0, results.getFirst().commandRowCount()); // Existing JDBC accessor remains compatible.
        assertEquals(2, results.get(1).rowCount());
        assertEquals(1, results.get(2).rowCount());
        assertEquals(3, results.get(2).affectedRows());
        assertEquals(2, results.get(3).rowCount());
        assertEquals(3, results.get(3).affectedRows());
        assertEquals(1, results.get(4).commandRowCount());
        assertEquals(3, results.get(5).rowCount());
        assertEquals(8, results.get(6).affectedRows());
        assertEquals("INSERT 0 2", results.get(1).commandTag());
    }
}
