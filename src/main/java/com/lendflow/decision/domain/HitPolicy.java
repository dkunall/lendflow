package com.lendflow.decision.domain;

/**
 * How a table turns matching rules into a result.
 * <ul>
 *   <li>FIRST: the first matching rule in table order (pricing grids).</li>
 *   <li>COLLECT: every matching rule (knockout checks, where each failure is a reason).</li>
 *   <li>PRIORITY: the matching rules whose output ranks highest in the table's priority list. DMN returns one
 *   rule here; this keeps all rules tied at the top so each contributes its reason code.</li>
 * </ul>
 */
public enum HitPolicy {
    FIRST, COLLECT, PRIORITY
}
