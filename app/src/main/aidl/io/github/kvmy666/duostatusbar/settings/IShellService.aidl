// Issue #4: the Shizuku UserService contract.
//
// The service is instantiated by the Shizuku server in a process that runs as the Shizuku identity
// (shell when Shizuku was started through adb, root when it was started as root). That identity is what
// lets it change the secure `icon_blacklist` setting, which a normal app cannot write.
package io.github.kvmy666.duostatusbar.settings;

interface IShellService {

    // Reserved transaction the Shizuku server calls to stop the service. The value must not change.
    void destroy() = 16777114;

    // Runs one command and returns its combined stdout/stderr.
    String exec(String command) = 1;
}
