package com.anthem.cartman.users.service;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Posts to the mail relay the way the Delphi did.
 *
 * <p>The relay address comes from where the Delphi read it — {@code TM_Cosmo_Config.CC_EmailHost}
 * ({@code frmMain1.ReturnEmailHost}) — falling back to the {@code CARTMAN_MAIL_RELAY_URL}
 * environment variable, because the current database does not have that table at all (the live
 * Delphi's e-mail button fails against it for the same reason).
 *
 * <p>The attachment is sent as {@code application/vnd.openxmlformats…sheet}; the Delphi declared
 * the .xlsx as {@code application/pdf} (anomaly A40).
 */
@Component
class HttpMailRelay implements MailRelay {

    private final JdbcTemplate jdbc;
    private final RestClient rest = RestClient.create();
    private final String configuredUrl;

    HttpMailRelay(JdbcTemplate jdbc,
                  @org.springframework.beans.factory.annotation.Value("${cartman.mail.relay-url:}")
                  String configuredUrl) {
        this.jdbc = jdbc;
        this.configuredUrl = configuredUrl;
    }

    @Override
    public boolean configured() {
        return !resolveHost().isBlank();
    }

    @Override
    public String send(String fromAddress, String toAddress, String subject, String htmlBody,
                       String attachmentName, byte[] attachment) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("fromaddress", fromAddress);
        form.add("cc", ""); // the Delphi posts its uninitialised CC as empty
        form.add("toaddress", toAddress);
        form.add("subject", subject);
        form.add("body", htmlBody);
        if (attachmentName != null && attachment != null) {
            form.add("fileattach1", new ByteArrayResource(attachment) {
                @Override
                public String getFilename() {
                    return attachmentName;
                }
            });
        }
        return rest.post()
                .uri(resolveHost())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .body(String.class);
    }

    /** {@code ReturnEmailHost}: the config table first, the environment second, blank when neither. */
    private String resolveHost() {
        try {
            String host = jdbc.queryForObject("SELECT CC_EmailHost FROM TM_Cosmo_Config",
                    String.class);
            if (host != null && !host.isBlank()) {
                return host.trim();
            }
        } catch (Exception e) {
            // No table, no row, or no reachable database — the Delphi swallowed this the same way.
        }
        return configuredUrl == null ? "" : configuredUrl.trim();
    }
}
