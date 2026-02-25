package ocean.util;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * <pre>
 * Hilfklasse zum Starten von Submarine-Prozessen.
 * </pre>
 */
public class AppLauncher {

    /**
     * Startet eine Submarine-Anwendung aus dem Working-Directory.
     *
     * @param shipID       ID des Mutterschiffs
     * @param shipHost     Hostname der ShipApp
     * @param shipPort     Port der ShipApp
     * @param oceanSrvHost Hostname des OceanServers
     * @param oceanSrvPort Port des OceanServers
     * @return true wenn Prozess erfolgreich gestartet
     */
    public static boolean startSubmarine(String shipID, String shipHost, int shipPort,
            String oceanSrvHost, int oceanSrvPort) {
        return startSubmarine("", shipID, shipHost, shipPort, oceanSrvHost, oceanSrvPort);
    }

    /**
     * Startet eine Submarine-Anwendung aus dem angegebenen Verzeichnis.
     *
     * @param submarinePath Pfad zum Verzeichnis mit submarine.jar
     * @param shipID        ID des Mutterschiffs
     * @param shipHost      Hostname der ShipApp
     * @param shipPort      Port der ShipApp
     * @param oceanSrvHost  Hostname des OceanServers
     * @param oceanSrvPort  Port des OceanServers
     * @return true wenn Prozess erfolgreich gestartet
     */
    public static boolean startSubmarine(String submarinePath, String shipID, String shipHost,
            int shipPort, String oceanSrvHost, int oceanSrvPort) {
        String submarine = "submarine.jar";
        if (submarinePath != null && !submarinePath.isEmpty()) {
            if (!submarinePath.endsWith("\\") && !submarinePath.endsWith("/")) {
                submarinePath += "/";
            }
            submarine = submarinePath + submarine;
        }
        ArrayList<String> cmd = new ArrayList<>();
        String javaExecutablePath = ProcessHandle.current().info().command().orElseThrow();
        cmd.add(javaExecutablePath);
        cmd.add("--add-exports");
        cmd.add("java.desktop/sun.awt=ALL-UNNAMED");
        cmd.add("-jar");
        cmd.add(submarine);
        cmd.add("-shipid="    + shipID);
        cmd.add("-shiphost="  + shipHost);
        cmd.add("-shipport="  + shipPort);
        cmd.add("-oceanhost=" + oceanSrvHost);
        cmd.add("-oceanport=" + oceanSrvPort);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        try {
            Process p = pb.start();
            boolean rc = p.waitFor(1000, TimeUnit.MILLISECONDS);
            int exitVal = 1;
            try {
                exitVal = p.exitValue();
            } catch (IllegalThreadStateException e) {
                return true;
            }
            if (exitVal != 0) {
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
