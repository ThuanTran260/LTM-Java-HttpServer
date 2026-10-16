package client;

/**
 * Observer callback listener interface for monitoring real-time FTP commands
 * and RFC 959 server replies.
 */
public interface FtpCommandListener {

    /**
     * Fired when a command is about to be transmitted over the control channel.
     * Sensitive parameters (such as PASS) must already be sanitized prior to invocation.
     *
     * @param command the sanitized FTP command line (e.g. "USER admin" or "PASS ********")
     */
    void onCommandSent(String command);

    /**
     * Fired when a response is received from the FTP server.
     *
     * @param code the RFC 959 status code (e.g. 200, 220, 226, 331, 550)
     * @param replyText the full message text received from the server
     */
    void onReplyReceived(int code, String replyText);

    /**
     * Optional convenience callback receiving raw response text.
     *
     * @param response the response text
     */
    default void onResponseReceived(String response) {
        // default empty implementation
    }
}
