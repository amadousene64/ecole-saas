package sn.ecole.inscription.api;

import java.time.Instant;

/** URL signée à durée limitée. */
public record UrlDto(String url, Instant expireLe) {}
