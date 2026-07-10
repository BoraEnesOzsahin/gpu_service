package com.ayrotek.util;

public record CommandResult(
    int exitCode,
    String stdout,
    String stderr
) {
}
