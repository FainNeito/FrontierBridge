package net.lumalyte.frontier.bridge;
public final class DeploymentPolicyTest {
    public static void main(String[] args) throws Exception {
        int checks=0;
        for(String ip:new String[]{"", "0.0.0.0", "208.92.232.97", "127.0.0.1", "192.0.2.1", null})
        for(int port:new int[]{25565,25566,25567,25569})
        for(int protocol:new int[]{1073742170,1073742167,777})
        for(String version:new String[]{"26.4-snapshot-8","26.4 Snapshot 8","26.4 Snapshot 7"})
        for(String deployment:new String[]{"", "bloom-942c6700", "other"})
        for(boolean online:new boolean[]{true,false})for(boolean whitelist:new boolean[]{true,false}) {
            boolean exact=protocol==1073742170 && !version.endsWith("7");
            boolean expected=exact && online && whitelist && (("127.0.0.1".equals(ip) && port==25569) ||
                (("0.0.0.0".equals(ip)||"208.92.232.97".equals(ip)) && port==25566 && deployment.equals("bloom-942c6700")));
            if(DeploymentPolicy.permits(ip,port,protocol,version,deployment,online,whitelist)!=expected)throw new AssertionError("deployment policy: "+ip+":"+port+" "+version+" "+deployment);
            checks++;
        }
        java.nio.file.Files.writeString(java.nio.file.Path.of(args[0]),"{\"passed\":true,\"cases\":"+checks+",\"bloomRequiresOnlineModeAndWhitelist\":true}");
        System.out.println("DEPLOYMENT_POLICY_PASS cases="+checks);
    }
}
