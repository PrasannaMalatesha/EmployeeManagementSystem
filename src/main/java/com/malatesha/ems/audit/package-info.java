/**
 * Append-only audit log for HR-meaningful actions: employee create/update,
 * employment history changes, leave decisions, role changes, document
 * access. Written inside the originating transaction; never editable.
 */
package com.malatesha.ems.audit;
