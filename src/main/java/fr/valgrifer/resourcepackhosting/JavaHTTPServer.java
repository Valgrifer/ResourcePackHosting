package fr.valgrifer.resourcepackhosting;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

// The tutorial can be found just here on the SSaurel's Blog : 
// https://www.ssaurel.com/blog/create-a-simple-http-web-server-in-java
// Each Client Connection will be managed in a dedicated Thread
@SuppressWarnings({"SameParameterValue", "FieldCanBeLocal", "ResultOfMethodCallIgnored"})
class JavaHTTPServer implements Runnable{
    
    private static Logger logger;

    private static final String serverName = "Java HTTP ResourcePackHosting";

    private static final File WEB_ROOT = new File("./resourcepack");

    private final String notSupportedMessage = "Method Not Supported";
    private final String notFoundMessage = "Resource Pack not found";
    private final String bannedMessage = "You are banned";

    private final Socket connect;

    public JavaHTTPServer(Socket c) {
        connect = c;
    }

    private static ServerSocket serverConnect;
    private static BukkitTask task;
    public static void start(ResourcePackHosting plugin)
    {
        if(serverConnect != null)
            return;

        logger = plugin.getLogger();

        if(!WEB_ROOT.exists())
            WEB_ROOT.mkdir();

        task = new BukkitRunnable(){
            @Override
            public void run() {
                try {
                    serverConnect = new ServerSocket(ResourcePackHosting.PORT);
                    logger.info("Server started.\nListening for connections on port : " + ResourcePackHosting.PORT + " ...");

                    while (!serverConnect.isClosed())
                    {
                        JavaHTTPServer myServer = new JavaHTTPServer(serverConnect.accept());

                        if (ResourcePackHosting.verbose)
                            logger.info("Connecton opened. (" + new Date() + ")");

                        Thread thread = new Thread(myServer);
                        thread.start();
                    }

                }
                catch (IOException e)
                {
                    logger.log(Level.WARNING, "Server Connection error : " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    public static void stop()
    {
        if(serverConnect == null)
            return;

        try
        {
            serverConnect.close();
            task.cancel();
        }
        catch (IOException e)
        {
            logger.log(Level.WARNING, "Server Close error : " + e.getMessage());
        }
    }

    @Override
    public void run() {
        BufferedReader in = null; PrintWriter out = null; BufferedOutputStream dataOut = null;

        try {
            in = new BufferedReader(new InputStreamReader(connect.getInputStream()));
            out = new PrintWriter(connect.getOutputStream());
            dataOut = new BufferedOutputStream(connect.getOutputStream());

            String input = in.readLine();
            StringTokenizer parse = new StringTokenizer(input);
            String method = parse.nextToken().toUpperCase();

            if(Bukkit.getBanList(BanList.Type.IP).isBanned(connect.getInetAddress().getHostAddress()))
            {
                if (ResourcePackHosting.verbose)
                    logger.info("401 Unauthorized : " + connect.getInetAddress().getHostAddress());

                send(out, dataOut, "401 Unauthorized", bannedMessage);
            }
            else if (!method.equals("GET")  &&  !method.equals("HEAD")) {
                if (ResourcePackHosting.verbose)
                    logger.info("501 Not Implemented : " + method + " method.");

                send(out, dataOut, "501 Not Implemented", notSupportedMessage);
            }
            else
            {
                File file = new File(WEB_ROOT, ResourcePackHosting.DEFAULT_FILE);
                int fileLength = (int) file.length();
                String content = "application/zip";

                if (method.equals("GET"))
                {
                    byte[] fileData = readFileData(file, fileLength);

                    send(out, dataOut, "200 OK", content, fileData, fileLength, new Date(file.lastModified()));
                }

                if (ResourcePackHosting.verbose)
                    logger.info("File of type ResourcePack returned");
            }

        }
        catch (FileNotFoundException fnfe)
        {
            try
            {
                fileNotFound(Objects.requireNonNull(out), Objects.requireNonNull(dataOut));
            }
            catch (IOException ioe)
            {
                logger.log(Level.WARNING, "Error with file not found exception : " + ioe.getMessage());
            }

        }
        catch (IOException ioe)
        {
            logger.log(Level.WARNING, "Server error : " + ioe);
        }
        finally
        {
            try
            {
                Objects.requireNonNull(in).close();
                Objects.requireNonNull(out).close();
                Objects.requireNonNull(dataOut).close();
                connect.close();
            }
            catch (Exception e)
            {
                logger.log(Level.WARNING, "Error closing stream : " + e.getMessage());
            }

            if (ResourcePackHosting.verbose)
                logger.info("Connection closed.\n");
        }
    }

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
    private void send(PrintWriter out, OutputStream dataOut, String status, String data) throws IOException
    {
        send(out, dataOut, status, "text/plain", data.getBytes(StandardCharsets.UTF_8), data.length(), null);
    }
    private void send(PrintWriter out, OutputStream dataOut, String status, String contentMimeType, byte[] data, int length, Date lastModified) throws IOException
    {

        out.println("HTTP/1.1 " + status);
        out.println("Server: " + serverName);
        out.println("Date: " + new Date());
        out.println("Content-type: " + contentMimeType);
        if(contentMimeType.equals("application/zip"))
            out.println("Content-Disposition: attachment; filename=\"" + ResourcePackHosting.DEFAULT_FILE + "\"");
        out.println("Content-length: " + length);
        if(lastModified != null)
            out.println("Last-Modified: " + dateFormat.format(lastModified));
        out.println();
        out.flush();

        dataOut.write(data, 0, data.length);
        dataOut.flush();
    }

    private byte[] readFileData(File file, int fileLength) throws IOException {
        FileInputStream fileIn = null;
        byte[] fileData = new byte[fileLength];

        try {
            fileIn = new FileInputStream(file);
            fileIn.read(fileData);
        } finally {
            if (fileIn != null)
                fileIn.close();
        }

        return fileData;
    }


    private void fileNotFound(PrintWriter out, OutputStream dataOut) throws IOException {
        send(out, dataOut, "404 File Not Found", notFoundMessage);

        if (ResourcePackHosting.verbose)
            logger.info("File ResourcePack not found");
    }
}