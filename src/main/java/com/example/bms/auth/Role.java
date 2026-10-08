package com.example.bms.auth;

/**
 * GLOSSARY: ADMIN (owner), MANAGER (sales supervisor), EMPLOYEE (sales clerk).
 * Carried in the JWT claim, so authorization is a claim check (ADR 0015).
 */
public enum Role {
    ADMIN, MANAGER, EMPLOYEE
}
