package com.anthem.cartman.users.service;

/**
 * The COSMO mail relay: an HTTP endpoint that takes a multipart form
 * ({@code fromaddress, cc, toaddress, subject, body, fileattach1}) and answers a text containing
 * {@code successful} when the message went out — exactly the contract the Delphi posted to
 * ({@code btnEMailClick}, {@code frmUserManager.pas:640-668}).
 */
public interface MailRelay {

    /** True when the relay has an address to post to. */
    boolean configured();

    /**
     * Post one message.
     *
     * @param attachmentName null when there is nothing to attach
     * @return the relay's raw response text
     */
    String send(String fromAddress, String toAddress, String subject, String htmlBody,
                String attachmentName, byte[] attachment);
}
