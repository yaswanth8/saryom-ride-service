package com.saryom.rideservice.domain;

/**
 * How much luggage a rider can bring.
 *
 * <p>Three values rather than a number of litres: a driver knows whether the
 * boot is full, not how many litres are left, and a rider knows whether they
 * have a backpack or a suitcase. Anything finer would be guessed at both ends.
 */
public enum BagSize {

    /** No room at all — the seats are the only space. */
    NONE,

    /** A backpack or cabin bag on your lap or at your feet. */
    SMALL,

    /** Boot space for a suitcase. */
    LARGE
}
