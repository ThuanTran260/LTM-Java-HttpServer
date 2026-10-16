package client;

import java.util.Collections;
import java.util.List;

/**
 * Represents a structured response from an RFC 959 FTP control channel.
 * Encapsulates the 3-digit reply code, single/multi-line text, and raw reply lines.
 */
public record FtpReply(int code, String message, List<String> lines) {

    public FtpReply {
        if (lines == null) {
            lines = List.of(message != null ? message : "");
        } else {
            lines = Collections.unmodifiableList(lines);
        }
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getLines() {
        return lines;
    }

    /**
     * 1xx: Positive preliminary reply (action being initiated, await another reply before sending next command).
     */
    public boolean isPositivePreliminary() {
        return code >= 100 && code < 200;
    }

    /**
     * 2xx: Positive completion reply (requested action successfully completed).
     */
    public boolean isPositiveCompletion() {
        return code >= 200 && code < 300;
    }

    /**
     * 3xx: Positive intermediate reply (command accepted, but further information needed).
     */
    public boolean isPositiveIntermediate() {
        return code >= 300 && code < 400;
    }

    /**
     * 4xx: Transient negative completion reply (temporary error, command can be retried).
     */
    public boolean isTransientNegative() {
        return code >= 400 && code < 500;
    }

    /**
     * 5xx: Permanent negative completion reply (error, action not accepted).
     */
    public boolean isPermanentNegative() {
        return code >= 500 && code < 600;
    }

    /**
     * Returns true if status is either 2xx or 3xx.
     */
    public boolean isSuccess() {
        return isPositiveCompletion() || isPositiveIntermediate();
    }

    @Override
    public String toString() {
        return code + " " + (message != null ? message : "");
    }
}
