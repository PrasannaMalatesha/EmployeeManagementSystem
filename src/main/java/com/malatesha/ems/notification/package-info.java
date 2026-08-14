/**
 * In-app notifications and outbound email. Triggered by domain events with
 * {@code @TransactionalEventListener(AFTER_COMMIT)} — never by direct calls
 * from producing services. See DR-006. Candidate for extraction.
 */
package com.malatesha.ems.notification;
