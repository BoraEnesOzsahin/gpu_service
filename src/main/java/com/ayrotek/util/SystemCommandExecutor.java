package com.ayrotek.util;

import java.time.Duration;
import java.util.List;

public interface SystemCommandExecutor {
    CommandResult execute(List<String> command, Duration timeout);
}
