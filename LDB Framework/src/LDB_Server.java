import java.io.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import com.sun.net.httpserver.*;

public class LDB_Server implements HttpHandler {
	// Server variables
	static HttpServer server;
	private static Map<String, Asset> data = new HashMap<String, Asset>();
	private final boolean caching, gzip;
	private final String pathToRoot;

	public LDB_Server(String pathToRoot, boolean caching, boolean gzip) throws IOException {
		this.caching = caching;
		this.pathToRoot = pathToRoot.endsWith("/") ? pathToRoot : pathToRoot + "/";
		this.gzip = gzip;

		File[] files = new File(pathToRoot).listFiles();
		if (files == null)
			throw new IllegalStateException(LDB_SaveAndPopulateUniqueIDs.updateCurrentTime()
					+ " - Local:~ Path to root not found: " + pathToRoot);
		for (File f : files)
			processFile("", f, gzip);
	}

	private static class Asset {
		public final byte[] data;

		public Asset(byte[] data) {
			this.data = data;
		}
	}

	public void handle(HttpExchange httpExchange) throws IOException {
		String path = httpExchange.getRequestURI().getPath();
		try {
			path = path.substring(1);
			path = path.replaceAll("//", "/");
			if (path.length() == 0)
				path = "index.html";

			boolean fromFile = new File(pathToRoot + path).exists();
			InputStream in = fromFile ? new FileInputStream(pathToRoot + path)
					: ClassLoader.getSystemClassLoader().getResourceAsStream(pathToRoot + path);
			Asset res = caching ? data.get(path) : new Asset(readResource(in, gzip));

			if (gzip)
				httpExchange.getResponseHeaders().set("Content-Encoding", "gzip");
			if (path.endsWith(".js"))
				httpExchange.getResponseHeaders().set("Content-Type", "text/javascript");
			else if (path.endsWith(".html"))
				httpExchange.getResponseHeaders().set("Content-Type", "text/html");
			else if (path.endsWith(".css"))
				httpExchange.getResponseHeaders().set("Content-Type", "text/css");
			else if (path.endsWith(".json"))
				httpExchange.getResponseHeaders().set("Content-Type", "application/json");
			else if (path.endsWith(".svg"))
				httpExchange.getResponseHeaders().set("Content-Type", "image/svg+xml");
			if (httpExchange.getRequestMethod().equals("HEAD")) {
				httpExchange.getResponseHeaders().set("Content-Length", "" + res.data.length);
				httpExchange.sendResponseHeaders(200, -1);
				return;
			}

			httpExchange.sendResponseHeaders(200, res.data.length);
			httpExchange.getResponseBody().write(res.data);
			httpExchange.getResponseBody().close();

		} catch (NullPointerException t) {
			System.out.println(LDB_SaveAndPopulateUniqueIDs.updateCurrentTime() + " - Local:~ Fetch Error: " + path);
		} catch (Throwable t) {
			System.out.println(LDB_SaveAndPopulateUniqueIDs.updateCurrentTime() + " - Local:~ Fetch Error: " + path);
			t.printStackTrace();
		}
	}

	private static void processFile(String path, File f, boolean gzip) throws IOException {
		if (!f.isDirectory())
			data.put(path + f.getName(), new Asset(readResource(new FileInputStream(f), gzip)));
		if (f.isDirectory())
			for (File sub : f.listFiles())
				processFile(path + f.getName() + "/", sub, gzip);
	}

	private static byte[] readResource(InputStream in, boolean gzip) throws IOException {
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		OutputStream gout = gzip ? new GZIPOutputStream(bout) : new DataOutputStream(bout);
		byte[] tmp = new byte[4096];
		int r;
		while ((r = in.read(tmp)) >= 0)
			gout.write(tmp, 0, r);
		gout.flush();
		gout.close();
		in.close();
		return bout.toByteArray();
	}

	@SuppressWarnings("unused")
	private static List<String> getResources(String directory) {
		ClassLoader context = Thread.currentThread().getContextClassLoader();

		List<String> resources = new ArrayList<>();

		ClassLoader cl = LDB_Server.class.getClassLoader();
		if (!(cl instanceof URLClassLoader))
			throw new IllegalStateException();
		URL[] urls = ((URLClassLoader) cl).getURLs();

		int slash = directory.lastIndexOf("/");
		String dir = directory.substring(0, slash + 1);
		for (int i = 0; i < urls.length; i++) {
			if (!urls[i].toString().endsWith(".jar"))
				continue;
			try {
				JarInputStream jarStream = new JarInputStream(urls[i].openStream());
				while (true) {
					ZipEntry entry = jarStream.getNextEntry();
					if (entry == null)
						break;
					if (entry.isDirectory())
						continue;

					String name = entry.getName();
					slash = name.lastIndexOf("/");
					String thisDir = "";
					if (slash >= 0)
						thisDir = name.substring(0, slash + 1);

					if (!thisDir.startsWith(dir))
						continue;
					resources.add(name);
				}

				jarStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		InputStream stream = context.getResourceAsStream(directory);
		try {
			if (stream != null) {
				StringBuilder sb = new StringBuilder();
				char[] buffer = new char[1024];
				try (Reader r = new InputStreamReader(stream)) {
					while (true) {
						int length = r.read(buffer);
						if (length < 0) {
							break;
						}
						sb.append(buffer, 0, length);
					}
				}

				for (String s : sb.toString().split("\n")) {
					if (s.length() > 0 && context.getResource(directory + s) != null) {
						resources.add(s);
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return resources;
	}

	public static void main(String[] args) throws IOException {
		// if (args.length < 1 || args[0].equals("-help") || args[0].equals("--help")) {
		// System.out.println("Usage: java -jar HttpServer.jar $webroot [$port]");
		// return;
		// }
		server = HttpServer.create();
		server.createContext("/", new LDB_Server("LDB Framework/interface", false, false));
		int port = args.length > 1 ? Integer.parseInt(args[1]) : 8000;
		server.bind(new InetSocketAddress("localhost", port), 100);
		System.out.println(LDB_SaveAndPopulateUniqueIDs.updateCurrentTime() + " - Local:~ Server Starting ....");
		server.start();
		System.out.println(LDB_SaveAndPopulateUniqueIDs.updateCurrentTime() + " - Local:~ http://localhost:" + port + "/");
	}
}