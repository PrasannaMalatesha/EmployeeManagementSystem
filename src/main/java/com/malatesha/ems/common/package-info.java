/**
 * Shared building blocks: {@code BaseAuditableEntity}, exception types,
 * the {@code ProblemDetail} handler, pagination utilities, domain event
 * base types. No feature logic lives here.
 *
 * <p>Every other module may depend on {@code common}; {@code common} depends on
 * no feature module, keeping this package a sink in the dependency graph.
 */
package com.malatesha.ems.common;
