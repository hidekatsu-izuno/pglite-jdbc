package io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;

public final class Messages {
    public static final BackendMessage parseComplete = new BackendMessage() {
        public String getName() {
            return "parseComplete";
        }

        @Override
        public int getLength() {
            return 5;
        }
    };

    public static final BackendMessage bindComplete = new BackendMessage() {
        public String getName() {
            return "bindComplete";
        }

        @Override
        public int getLength() {
            return 5;
        }
    };

    public static final BackendMessage closeComplete = new BackendMessage() {
        public String getName() {
            return "closeComplete";
        }

        @Override
        public int getLength() {
            return 5;
        }
    };

    public static final BackendMessage noData = new BackendMessage() {
        public String getName() {
            return "noData";
        }

        @Override
        public int getLength() {
            return 5;
        }
    };

    public static final BackendMessage portalSuspended = new BackendMessage() {
        public String getName() {
            return "portalSuspended";
        }

        @Override
        public int getLength() {
            return 5;
        }
    };

    public static final BackendMessage replicationStart = new BackendMessage() {
        public String getName() {
            return "replicationStart";
        }

        @Override
        public int getLength() {
            return 4;
        }
    };

    public static final BackendMessage emptyQuery = new BackendMessage() {
        public String getName() {
            return "emptyQuery";
        }

        @Override
        public int getLength() {
            return 4;
        }
    };

    public static final BackendMessage copyDone = new BackendMessage() {
        public String getName() {
            return "copyDone";
        }

        @Override
        public int getLength() {
            return 4;
        }
    };

    private Messages() {
    }
}
