import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import org.jaudiotagger.audio.*;
import org.jaudiotagger.audio.mp3.MP3AudioHeader;
import org.jaudiotagger.audio.mp3.MP3File;
import org.jaudiotagger.tag.images.Artwork;

public class Server
{

    static String resp404 = "<html><body><h1>404: Page not found</h3><a href=\"/\">Return to root</a></body></html>";
    static String logPath = "server.log";
    static LocalDateTime time;
    static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss.SSS");
    HttpServer server;

    ArrayList<TrackInfo> tracks = new ArrayList<TrackInfo>();

    public Server() throws Exception
    {
        Logger.getLogger("org.jaudiotagger").setLevel(Level.OFF);
        System.out.println("Reading audio files...");
        ReadAllMusic();
        System.out.println("Server booting...");

        try
        {
            server = HttpServer.create(new InetSocketAddress(80), 0);
        }
        catch (IOException e)
        {
            System.out.println("An error occurred creating a server instance:");
            System.out.println(e.getMessage());
            throw new Exception(e);
        }

        server.createContext("/", new HanRoot());
        server.createContext("/res", new HanRes());
        server.createContext("/music", new HanMusic());
        server.setExecutor(null); // creates a default executor
    }

    public void start()
    {
        server.start();
        System.out.println("Server running...");
    }

    private void ReadAllMusic()
    {
        try (Stream<Path> paths = Files.walk(Paths.get("res/library")))
        {
            paths.filter(Files::isRegularFile)
            .filter(f -> f.getFileName().toString().toLowerCase().endsWith(".mp3"))
            .forEach(f -> ReadMp3(f));
        }
        catch (IOException e)
        {
            System.out.println("Failed to read music files.");
        }

        CreateLibraryPage("library.html", tracks);
        CreatePlayPages(tracks);
    }

    private void ReadMp3(Path filepath)
    {
        System.out.println(String.format("Reading %s", filepath.getFileName()));
        try
        {
            MP3File f = (MP3File)AudioFileIO.read(filepath.toFile());
            MP3AudioHeader audioHeader = f.getMP3AudioHeader();

            String trackLength = audioHeader.getTrackLengthAsString();
            String trackTitle = f.getID3v1Tag().getFirstTitle();
            String albumTitle = f.getID3v1Tag().getFirstAlbum();

            System.out.println(String.format("Track title: %s", trackTitle));
            System.out.println(String.format("Album: %s", albumTitle));
            System.out.println(String.format("Track length: %s", trackLength));

            File albumArtFile = new File(String.format("res/library/%s.jpg", albumTitle)); 
            File playPage = new File(String.format("music/%s", filepath.getFileName()));

            if (!albumArtFile.exists())
            {
                Artwork coverArt = f.getTag().getFirstArtwork();
                try (FileOutputStream fos = new FileOutputStream(new File(String.format("res/library/%s.jpg", albumTitle))))
                {
                    fos.write(coverArt.getBinaryData());
                }
            }

            tracks.add(new TrackInfo(
                trackTitle,
                albumTitle,
                trackLength,
                "music/" + playPage.getName().substring(0, playPage.getName().length() - 4) + ".html",
                "res/library/" + filepath.getFileName().toString(),
                "res/library/" + albumArtFile.getName()
            ));
        }
        catch (Exception e)
        {
            System.out.println("An unexpected error occurred:");
            System.out.println(e.getMessage());
        }
    }

    private void CreatePlayPages(ArrayList<TrackInfo> tracks)
    {
        try
        {
            FileWriter writer = null;
            File f;
        for (TrackInfo track : tracks)
        {
            f = new File(track.getPagePath());
            if (f.createNewFile())
            {
                writer = new FileWriter(track.getPagePath());
                writer.append("<!DOCTYPE html>");
                writer.append("\n<html>");
                writer.append("\n  <body>");
                writer.append("\n    <h2>" + track.getTitle() + "</h2>");
                writer.append("\n    <h3>" + track.getAlbumTitle() + "</h3>");
                writer.append("\n    <image src=\"/" + track.getArtworkPath() + "\">");
                writer.append("\n    <p>");
                writer.append("\n      <audio id=\"/res/library\" controls>");
                writer.append("\n        <source src=\"/" + track.getAudioPath() + "\" type=\"audio/mpeg\">");
                writer.append("\n        Your browser does not supprt HTML5 audio.");
                writer.append("\n      </audio>");
                writer.append("\n    </p>");
                writer.append("\n    <p>");
                writer.append("\n      <a href=\"/library.html\">Return to Library</a>");
                writer.append("\n    </p>");
                writer.append("\n  </body>");
                writer.append("\n</html>");
            }

            if (writer != null)
            {
                writer.close();
            }
        }
        }
        catch (IOException e)
        {
            System.out.println("Error creating play pages: " + e.getMessage());
        }
        
    }

    private void CreateLibraryPage(String fileName, ArrayList<TrackInfo> tracks)
    {
        try
        {
            FileWriter writer = new FileWriter(fileName);
            writer.append("<!DOCTYPE html>");
            writer.append("\n<html>");
            writer.append("\n  <body>");
            writer.append("\n    <table>");
            writer.append("\n      <tbody>");
            for (TrackInfo track : tracks)
            {
                writer.append("\n      <tr>");
                writer.append("\n        <td><image src=\"" + track.getArtworkPath() + "\"></td>");
                writer.append("\n        <td>" + track.getTitle() + "</td>");
                writer.append("\n        <td>" + track.getAlbumTitle() + "</td>");
                writer.append("\n        <td>" + track.getLength() + "s</td>");
                writer.append("\n        <td><a href=\"" + track.getPagePath() + "\">Play</a></td>");
                writer.append("\n      </tr>");
            }
            writer.append("\n      </tbody>");
            writer.append("\n    </table>");
            writer.append("\n  </body>");
            writer.append("\n</html>");
            writer.close();
        }
        catch (IOException e)
        {
            System.out.println("Error creating Library page: " + e.getMessage());
        }
    }

    //#region Helper Methods

    private byte[] getHTML(String name) throws IOException
    {
        byte[] html = Files.readAllBytes(new File(name).toPath());
        return html;
    }

    private byte[] getFile(String filename)
    {
        byte[] data = null;
        try 
        {
            data = Files.readAllBytes(new File(filename).toPath());
        }
        catch (IOException e)
        {
            System.out.println("File \"" + filename + "\" cannot be found.");
        }
        return data;
    }

    private void log(String s)
    {
        time = LocalDateTime.now();
        try
        {
            PrintWriter l = new PrintWriter(new FileOutputStream(new File(logPath), true));
            l.append("[" + time.format(formatter) + "]: " + s + "\n");
            l.close();
        }
        catch (IOException e)
        {
            System.out.println("Error writing to log.");
            System.out.println("Failed to log: \"" + s + "\"");
        }
        
    }

    private HashMap<String, String> parseForm(String data)
    {
        HashMap<String, String> form = new HashMap<String, String>();
        int index = 0;
        int prevIndex;
        while (true)
        {
            prevIndex = index;
            index = data.indexOf("form-data; name=\"", index) + 17; //start of field name
            if (index != -1 && index > prevIndex)
            {
                int start = index;
                int end = data.indexOf("\"", start);                //end of field name
                String key = data.substring(start, end);
                start = end + 3;                             //end of field name: start of data (less leading \n\n)
                end = data.indexOf("------WebKit", end) - 1;    //end of data (less trailing \n);
                String value = data.substring(start, end);
                form.put(key, value);
                index = end;
            }
            else
            {
                break;
            }
        }
        return form;
    }

    //#endregion

    //#region Route Handlers

    private class HanRoot implements HttpHandler
    {
        HttpExchange t;

        public void handle(HttpExchange httpEx) throws IOException
        {
            this.t = httpEx;
            String method = t.getRequestMethod();
            String decodedURI = URLDecoder.decode(t.getRequestURI().toString(), StandardCharsets.UTF_8.name());
            String headers = "";
            for (Map.Entry<String, List<String>> h : t.getRequestHeaders().entrySet())
            {
            headers += (h + "\n");
            }
            String rHeaders = "";
            for (Map.Entry<String, List<String>> h : t.getResponseHeaders().entrySet())
            {
            rHeaders += (h + "\n");
            }

            if (method.equals("GET"))
            {
                String s = t.getRemoteAddress() + "| GET: " + decodedURI + " (HanRoot)";
                log(s);
                System.out.println(s);
                get(decodedURI);
            } else if (method.equals("POST"))
            {
                String s = t.getRemoteAddress() + "| POST: " + decodedURI + " (HanRoot)";
                log(s);
                System.out.println(s);
                post(decodedURI, null);

            }
            t.close();
        }

        private void get(String uri) throws IOException
        {
            byte[] resp;
            if (uri.matches("/$"))
            {
                resp = getHTML("index.html");
                t.getResponseHeaders().set("content-type", "text/html");
                t.sendResponseHeaders(200, resp.length);
                t.getResponseBody().write(resp);
            }
            else if (uri.matches("/favicon.ico"))
            {
                resp = getFile("favicon.ico");
                t.getResponseHeaders().set("content-type", "attachment");
                t.sendResponseHeaders(200, resp.length);
                t.getResponseBody().write(resp);
            }
            else if (uri.matches("/library.html"))
            {
                resp = getFile("library.html");
                t.getResponseHeaders().set("content-type", "text/html");
                t.sendResponseHeaders(200, resp.length);
                t.getResponseBody().write(resp);
            }
            else if (uri.matches("/refresh"))
            {
                resp = "false".getBytes();
                t.getResponseHeaders().set("content-type", "attachment");
                t.sendResponseHeaders(200, resp.length);
                t.getResponseBody().write(resp);
            }
            else if (uri.matches("/transfer"))
            {
                resp = getFile("transfer.file");
                t.getResponseHeaders().set("content-type", "attachment");
                t.sendResponseHeaders(200, resp.length);
                t.getResponseBody().write(resp);
            }
            else
            {
                resp = resp404.getBytes();
                t.getResponseHeaders().set("content-type", "text/html");
                t.sendResponseHeaders(404, resp.length);
                t.getResponseBody().write(resp);
            }
        }

        private void post(String uri, byte[] payload) throws IOException
        {
            if (uri.matches("/$"))
            {
                // hate this but Java 8 affords not many better ways
                String data = new BufferedReader(new InputStreamReader(t.getRequestBody())).lines().collect(Collectors.joining("\n"));
                HashMap<String, String> form = parseForm(data);
                if (form.keySet().contains("cat"))
                {
                    
                }
                byte[] resp = getHTML("index.html");
                t.getResponseHeaders().set("content-type", "text/html");
                t.sendResponseHeaders(200, resp.length);
                t.getResponseBody().write(resp);
            }
        }
    }

    private class HanRes implements HttpHandler
    {
        HttpExchange t;

        public void handle(HttpExchange httpEx) throws IOException
        {
            this.t = httpEx;
            String method =  t.getRequestMethod();
            String decodedURI = URLDecoder.decode(t.getRequestURI().toString(), StandardCharsets.UTF_8.name());
           
            if (method.equals("GET"))
            {
                String s = t.getRemoteAddress() + "| GET: " + decodedURI + " (HanRes)";
                log(s);
                System.out.println(s);
                get(decodedURI);
            }    
            t.close();
        }

        private void get(String uri) throws IOException
        {
            byte[] resp = getFile(uri.substring(1));
            if (resp != null) 
            {
                t.getResponseHeaders().set("content-type", "attachment");
                t.sendResponseHeaders(200, resp.length);
                t.getResponseBody().write(resp);
            }
            else
            {
                resp = resp404.getBytes();
                t.getResponseHeaders().set("content-type", "text/html");
                t.sendResponseHeaders(404, resp.length);
                t.getResponseBody().write(resp);
            }
        }
    }

    private class HanMusic implements HttpHandler
    {
        HttpExchange t;

        public void handle(HttpExchange httpEx) throws IOException
        {
            this.t = httpEx;
            String method = t.getRequestMethod();
            String decodedURI = URLDecoder.decode(t.getRequestURI().toString(), StandardCharsets.UTF_8.name());

            if (method.equals("GET"))
            {
                String s = t.getRemoteAddress() + "| GET: " + decodedURI + " (HanMusic)";
                log(s);
                System.out.println(s);
                get(decodedURI);
            }
            t.close();
        }

        private void get(String uri) throws IOException
        {
            byte[] resp = getFile(uri.substring(1));
            if (resp != null) 
            {
                t.getResponseHeaders().set("content-type", "attachment");
                t.sendResponseHeaders(200, resp.length);
                t.getResponseBody().write(resp);
            }
            else
            {
                resp = resp404.getBytes();
                t.getResponseHeaders().set("content-type", "text/html");
                t.sendResponseHeaders(404, resp.length);
                t.getResponseBody().write(resp);
            }
        }
    }

    //#endregion
    
    public static void main(String[] args) throws Exception 
    {
        Server server = new Server();
        server.start();
    } 

}
