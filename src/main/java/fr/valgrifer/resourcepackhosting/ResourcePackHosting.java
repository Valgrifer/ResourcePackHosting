package fr.valgrifer.resourcepackhosting;

import org.bukkit.plugin.java.JavaPlugin;

@SuppressWarnings({"HttpUrlsUsage", "unused"})
public final class ResourcePackHosting extends JavaPlugin
{
    static String DEFAULT_FILE = "resourcepack.zip";
    static int PORT = 25665;
    static boolean verbose = false;
    private static String address = "";

    @Override
    public void onEnable()
    {
        saveDefaultConfig();

        PORT = getConfig().getInt("port", PORT);
        DEFAULT_FILE = getConfig().getString("filename", DEFAULT_FILE);
        verbose = getConfig().getBoolean("verbose", verbose);
        address = getConfig().getString("adresse", "127.0.0.1");

        JavaHTTPServer.start(this);
    }

    public static String getAdresse()
    {
        return String.format("http://%s:%s/", address, PORT);
    }

    @Override
    public void onDisable()
    {
        JavaHTTPServer.stop();
    }
}
