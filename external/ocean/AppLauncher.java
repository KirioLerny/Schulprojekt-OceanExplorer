package ocean;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class AppLauncher {

	public static boolean startSubmarine(String shipID, String shipHost, int shipPort,
			String oceanSrvHost, int oceanSrvPort) {
		return startSubmarine("", shipID, shipHost, shipPort, oceanSrvHost, oceanSrvPort);
	}

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
		cmd.add("-shipid=" + shipID);
		cmd.add("-shiphost=" + shipHost);
		cmd.add("-shipport=" + shipPort);
		cmd.add("-oceanhost=" + oceanSrvHost);
		cmd.add("-oceanport=" + oceanSrvPort);

		ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.inheritIO();
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
