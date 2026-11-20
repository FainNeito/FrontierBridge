package net.lumalyte.frontier.bridge;
/** Exact snapshot identity; explicit opt-in for the named authenticated Bloom test server. */
final class DeploymentPolicy {
    static boolean permits(String ip,int port,int protocol,String version,String deployment,boolean onlineMode,boolean whitelist) {
        boolean exact=protocol==1073742170 && ("26.4-snapshot-8".equals(version)||"26.4 Snapshot 8".equals(version));
        boolean lab="127.0.0.1".equals(ip) && port==25569;
        boolean bloom="bloom-942c6700".equals(deployment) && port==25566
            && ("0.0.0.0".equals(ip)||"208.92.232.97".equals(ip));
        return exact && onlineMode && whitelist && (lab||bloom);
    }
}
